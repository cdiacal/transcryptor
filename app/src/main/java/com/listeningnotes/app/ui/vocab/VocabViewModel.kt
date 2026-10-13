package com.listeningnotes.app.ui.vocab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.listeningnotes.app.AppContainer
import com.listeningnotes.app.data.model.Vocab
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface VocabUiState {
    data object Loading : VocabUiState
    data object Empty : VocabUiState
    data class Content(val items: List<Vocab>) : VocabUiState
    data class Error(val message: String) : VocabUiState
}

class VocabViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.vocabulary

    val uiState: StateFlow<VocabUiState> = repository.observeAll()
        .map<List<Vocab>, VocabUiState> { if (it.isEmpty()) VocabUiState.Empty else VocabUiState.Content(it) }
        .catch { emit(VocabUiState.Error(it.message ?: "No se pudo leer el vocabulario")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VocabUiState.Loading)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun updateNote(item: Vocab, note: String) = launchSafe("No se pudo guardar la nota") { repository.updateNote(item.id, note) }
    fun deleteMany(ids: Set<Long>) = launchSafe("No se pudieron borrar las palabras") { repository.deleteMany(ids) }
    fun delete(item: Vocab) = launchSafe("No se pudo borrar la palabra") { repository.delete(item.id) }

    private fun launchSafe(error: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _messages.trySend("$error: ${e.message}")
            }
        }
    }
}
