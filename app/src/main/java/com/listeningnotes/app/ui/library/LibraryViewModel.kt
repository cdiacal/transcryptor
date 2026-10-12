package com.listeningnotes.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.listeningnotes.app.AppContainer
import com.listeningnotes.app.ModelStatus
import com.listeningnotes.app.data.model.Transcription
import com.listeningnotes.app.selectedModelStatus
import com.listeningnotes.app.transcription.JobState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Empty(val searching: Boolean) : LibraryUiState
    data class Content(val items: List<Transcription>) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.repository

    /** Texto de búsqueda (inmediato, para el campo de texto). */
    val query = MutableStateFlow("")

    private val retry = MutableStateFlow(0)

    val uiState: StateFlow<LibraryUiState> = retry
        .flatMapLatest { query.debounce { if (it.isEmpty()) 0L else 250L }.distinctUntilChanged() }
        .flatMapLatest { q ->
            repository.observeLibrary(q)
                .map<List<Transcription>, LibraryUiState> { list ->
                    if (list.isEmpty()) LibraryUiState.Empty(searching = q.isNotBlank()) else LibraryUiState.Content(list)
                }
                .onStart { emit(LibraryUiState.Loading) }
                .catch { emit(LibraryUiState.Error(it.message ?: "No se pudo leer la biblioteca")) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)

    val modelStatus: StateFlow<ModelStatus?> = container.selectedModelStatus()
        .map<ModelStatus, ModelStatus?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val jobState: StateFlow<JobState> = container.transcriptionManager.state

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun setQuery(value: String) {
        query.value = value
    }

    fun retryLoad() {
        retry.value++
    }

    fun rename(item: Transcription, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.rename(item.id, clean)
            } catch (e: Exception) {
                _messages.trySend("No se pudo renombrar: ${e.message}")
            }
        }
    }

    fun delete(item: Transcription) {
        viewModelScope.launch {
            try {
                repository.delete(item)
            } catch (e: Exception) {
                _messages.trySend("No se pudo borrar: ${e.message}")
            }
        }
    }

    fun downloadModel() {
        modelStatus.value?.let { container.modelManager.download(it.spec) }
    }

    fun pauseModelDownload() {
        modelStatus.value?.let { container.modelManager.cancel(it.spec) }
    }

    fun acknowledgeJob() = container.transcriptionManager.acknowledge()
}
