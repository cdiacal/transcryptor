package com.listeningnotes.app.whisper

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Estado de un modelo en disco / descarga. */
sealed interface ModelState {
    /** [partialBytes] > 0 indica que existe una descarga a medias que se puede reanudar. */
    data class NotInstalled(val partialBytes: Long = 0) : ModelState
    data class Downloading(val downloaded: Long, val total: Long) : ModelState
    data object Ready : ModelState
    data class Error(val message: String, val partialBytes: Long = 0) : ModelState
}

/** Gestiona la descarga, el estado y el borrado de los modelos de whisper. */
class ModelManager(context: Context, private val scope: CoroutineScope) {

    val modelsDir = File(context.filesDir, "models")
    private val downloader = ModelDownloader(modelsDir)
    private val jobs = HashMap<String, Job>()

    private val _states = MutableStateFlow(Models.all.associate { it.id to diskState(it) })
    val states: StateFlow<Map<String, ModelState>> = _states.asStateFlow()

    fun file(spec: ModelSpec): File = spec.file(modelsDir)

    fun isInstalled(spec: ModelSpec): Boolean {
        val f = file(spec)
        return f.isFile && spec.hasPlausibleSize(f.length())
    }

    private fun diskState(spec: ModelSpec): ModelState =
        if (isInstalled(spec)) ModelState.Ready
        else ModelState.NotInstalled(partialBytes = downloader.partFile(spec).takeIf { it.exists() }?.length() ?: 0L)

    private fun setState(spec: ModelSpec, state: ModelState) =
        _states.update { it + (spec.id to state) }

    /** Inicia (o reanuda) la descarga del modelo si no está instalado ni descargándose. */
    @Synchronized
    fun download(spec: ModelSpec) {
        if (jobs[spec.id]?.isActive == true || isInstalled(spec)) return
        val partial = downloader.partFile(spec).takeIf { it.exists() }?.length() ?: 0L
        setState(spec, ModelState.Downloading(partial, spec.approxBytes))
        jobs[spec.id] = scope.launch {
            try {
                downloader.download(spec) { done, total ->
                    setState(spec, ModelState.Downloading(done, if (total > 0) total else spec.approxBytes))
                }
                setState(spec, ModelState.Ready)
            } catch (e: CancellationException) {
                setState(spec, diskState(spec))
                throw e
            } catch (e: ModelDownloadException) {
                setState(spec, ModelState.Error(e.message ?: "Error de descarga", partialOf(spec)))
            } catch (e: Exception) {
                setState(spec, ModelState.Error("Error inesperado: ${e.message}", partialOf(spec)))
            }
        }
    }

    private fun partialOf(spec: ModelSpec) = downloader.partFile(spec).takeIf { it.exists() }?.length() ?: 0L

    /** Pausa la descarga; el fichero parcial se conserva para reanudar. */
    @Synchronized
    fun cancel(spec: ModelSpec) {
        jobs.remove(spec.id)?.cancel()
    }

    /** Descarga el modelo seleccionado si falta (se llama al arrancar la app). */
    fun ensureInstalled(spec: ModelSpec) {
        if (!isInstalled(spec) && _states.value[spec.id] !is ModelState.Error) download(spec)
    }

    @Synchronized
    fun delete(spec: ModelSpec) {
        jobs.remove(spec.id)?.cancel()
        file(spec).delete()
        downloader.partFile(spec).delete()
        setState(spec, ModelState.NotInstalled())
    }
}
