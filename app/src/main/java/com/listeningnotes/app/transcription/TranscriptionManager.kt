package com.listeningnotes.app.transcription

import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import android.content.Intent
import com.listeningnotes.app.audio.AudioDecodeException
import com.listeningnotes.app.audio.AudioDecoder
import com.listeningnotes.app.audio.AudioImportException
import com.listeningnotes.app.audio.AudioImporter
import com.listeningnotes.app.data.repository.SettingsRepository
import com.listeningnotes.app.data.repository.TranscriptionRepository
import com.listeningnotes.app.whisper.ModelManager
import com.listeningnotes.app.whisper.Models
import com.listeningnotes.app.whisper.SentenceSplitter
import com.listeningnotes.app.whisper.WhisperEngine
import com.listeningnotes.app.whisper.WhisperException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class Stage(val label: String) {
    IMPORTING("Copiando el audio…"),
    DECODING("Decodificando el audio…"),
    TRANSCRIBING("Transcribiendo…"),
}

/** Estado del trabajo de transcripción (único a la vez). */
sealed interface JobState {
    data object Idle : JobState

    /** [progress] en 0..1, o null si es indeterminado. */
    data class Running(val stage: Stage, val progress: Float?, val title: String) : JobState

    data class Done(val transcriptionId: Long) : JobState
    data class Failed(val message: String) : JobState
}

/**
 * Orquesta importar -> decodificar -> transcribir -> guardar. Vive en el ámbito de la aplicación,
 * así que sobrevive a la navegación entre pantallas. Para audios largos arranca un servicio en primer plano.
 */
class TranscriptionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val importer: AudioImporter,
    private val decoder: AudioDecoder,
    private val whisper: WhisperEngine,
    private val models: ModelManager,
    private val settings: SettingsRepository,
    private val repository: TranscriptionRepository,
) {
    private val _state = MutableStateFlow<JobState>(JobState.Idle)
    val state: StateFlow<JobState> = _state.asStateFlow()

    private var job: Job? = null

    val isRunning: Boolean get() = _state.value is JobState.Running

    /** Inicia la transcripción del audio [uri]. Se ignora si ya hay una en curso. */
    @Synchronized
    fun start(uri: Uri) {
        if (isRunning) return
        _state.value = JobState.Running(Stage.IMPORTING, null, "")
        job = scope.launch { execute(uri) }
    }

    fun cancel() {
        whisper.cancel()
        job?.cancel()
    }

    /** La UI llama a esto tras mostrar el resultado/error para volver a Idle. */
    fun acknowledge() {
        if (_state.value !is JobState.Running) _state.value = JobState.Idle
    }

    private suspend fun execute(uri: Uri) {
        var imported: File? = null
        var saved = false
        try {
            val cfg = settings.current()
            val spec = Models.byId(cfg.modelId)
            val modelFile = models.file(spec)
            if (!models.isInstalled(spec)) {
                throw UserFacingError("El modelo ${spec.id} no está descargado todavía.")
            }

            val audio = importer.import(uri)
            imported = audio.file
            val title = audio.title
            _state.value = JobState.Running(Stage.IMPORTING, null, title)

            val durationMs = decoder.probeDurationMs(audio.file)
            if (durationMs != null && durationMs > LONG_AUDIO_MS) startForegroundService()

            _state.value = JobState.Running(Stage.DECODING, 0f, title)
            val decoded = decoder.decode(audio.file) { p ->
                _state.value = JobState.Running(Stage.DECODING, p, title)
            }
            // Sin duración en cabecera, decidimos con la real tras decodificar.
            if (durationMs == null && decoded.durationMs > LONG_AUDIO_MS) startForegroundService()

            _state.value = JobState.Running(Stage.TRANSCRIBING, 0f, title)
            val segments = whisper.transcribe(modelFile, decoded.samples, decoded.count, cfg.threads) { percent ->
                _state.value = JobState.Running(Stage.TRANSCRIBING, percent / 100f, title)
            }
            if (segments.isEmpty()) throw UserFacingError("No se detectó voz en el audio.")
            val finalSegments = if (cfg.splitSentences) segments.flatMap { SentenceSplitter.split(it) } else segments

            val id = repository.save(title, audio.file.absolutePath, decoded.durationMs, spec.id, finalSegments)
            saved = true
            _state.value = JobState.Done(id)
        } catch (e: CancellationException) {
            _state.value = JobState.Idle
            throw e
        } catch (e: UserFacingError) {
            _state.value = JobState.Failed(e.message ?: "Error")
        } catch (e: AudioImportException) {
            _state.value = JobState.Failed(e.message ?: "No se pudo importar el audio")
        } catch (e: AudioDecodeException) {
            _state.value = JobState.Failed(e.message ?: "No se pudo decodificar el audio")
        } catch (e: WhisperException) {
            _state.value = JobState.Failed(e.message ?: "Error de transcripción")
        } catch (e: OutOfMemoryError) {
            _state.value = JobState.Failed("Memoria insuficiente. Prueba con un audio más corto o con el modelo base.en.")
        } catch (e: Exception) {
            _state.value = JobState.Failed("Error inesperado: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            // Si no se llegó a guardar, no dejamos huérfana la copia del audio.
            if (!saved) imported?.delete()
        }
    }

    private fun startForegroundService() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, TranscriptionService::class.java))
        } catch (e: Exception) {
            // Si el sistema no permite arrancarlo (p. ej. app en segundo plano), seguimos sin notificación.
        }
    }

    private class UserFacingError(message: String) : Exception(message)

    companion object {
        /** A partir de 2 minutos de audio se usa el servicio en primer plano. */
        const val LONG_AUDIO_MS = 2 * 60 * 1000L
    }
}
