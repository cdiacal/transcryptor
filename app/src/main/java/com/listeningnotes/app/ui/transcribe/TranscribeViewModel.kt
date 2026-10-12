package com.listeningnotes.app.ui.transcribe

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.listeningnotes.app.AppContainer
import com.listeningnotes.app.ModelStatus
import com.listeningnotes.app.selectedModelStatus
import com.listeningnotes.app.transcription.JobState
import com.listeningnotes.app.whisper.ModelState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TranscribeUi(
    val model: ModelStatus?,
    val job: JobState,
    /** Hay un audio elegido esperando a que termine la descarga del modelo. */
    val waitingForModel: Boolean,
)

class TranscribeViewModel(private val container: AppContainer) : ViewModel() {

    private val manager = container.transcriptionManager
    private val waiting = MutableStateFlow<Uri?>(null)

    private val modelStatus: StateFlow<ModelStatus?> = container.selectedModelStatus()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val ui: StateFlow<TranscribeUi> = combine(modelStatus, manager.state, waiting) { model, job, w ->
        TranscribeUi(model, job, w != null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranscribeUi(null, JobState.Idle, false))

    init {
        // Audio recibido desde otras apps (compartir / abrir con).
        viewModelScope.launch {
            container.sharedAudio.collect { uri ->
                if (uri != null) {
                    container.sharedAudio.value = null
                    onAudioSelected(uri)
                }
            }
        }
        // Si el audio se eligió antes de tener el modelo, arrancamos en cuanto esté listo.
        viewModelScope.launch {
            combine(modelStatus, waiting) { m, w -> m?.state to w }.collect { (state, uri) ->
                if (uri != null && state is ModelState.Ready) {
                    waiting.value = null
                    manager.start(uri)
                }
            }
        }
    }

    fun onAudioSelected(uri: Uri) {
        if (manager.isRunning) {
            _messages.trySend("Ya hay una transcripción en curso.")
            return
        }
        viewModelScope.launch {
            val status = modelStatus.filterNotNull().first()
            if (status.state is ModelState.Ready) {
                manager.start(uri)
            } else {
                waiting.value = uri
                container.modelManager.download(status.spec) // no hace nada si ya está en curso
            }
        }
    }

    fun cancelWaiting() {
        waiting.value = null
    }

    fun cancelJob() = manager.cancel()

    fun acknowledge() = manager.acknowledge()

    fun downloadModel() {
        modelStatus.value?.let { container.modelManager.download(it.spec) }
    }

    fun pauseModelDownload() {
        modelStatus.value?.let { container.modelManager.cancel(it.spec) }
    }
}
