package com.listeningnotes.app.ui.keywords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.listeningnotes.app.AppContainer
import com.listeningnotes.app.analysis.Keyword
import com.listeningnotes.app.analysis.KeywordExtractor
import com.listeningnotes.app.data.model.Segment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estado del modelo de traducción. */
sealed interface TranslationStatus {
    data object Downloading : TranslationStatus
    data object Ready : TranslationStatus
    data class Error(val message: String) : TranslationStatus
}

data class KeywordItem(
    val keyword: Keyword,
    val translation: String?,
    val example: String,
    val saved: Boolean,
)

sealed interface KeywordsUiState {
    data object Loading : KeywordsUiState
    data object Empty : KeywordsUiState
    data class Error(val message: String) : KeywordsUiState
    data class Content(
        val title: String,
        val topic: List<String>,
        val items: List<KeywordItem>,
        val translation: TranslationStatus,
    ) : KeywordsUiState
}

private data class Analysis(val title: String, val segments: List<Segment>, val keywords: List<Keyword>)

class KeywordsViewModel(private val container: AppContainer, private val transcriptionId: Long) : ViewModel() {

    private val analysis = MutableStateFlow<Analysis?>(null)
    private val failure = MutableStateFlow<String?>(null)
    private val translations = MutableStateFlow<Map<String, String>>(emptyMap())
    private val status = MutableStateFlow<TranslationStatus>(TranslationStatus.Downloading)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val uiState: StateFlow<KeywordsUiState> = combine(
        analysis, failure, translations, status, container.vocabulary.observeAll(),
    ) { a, error, tr, st, vocab ->
        val savedWords = vocab.map { it.palabra }.toSet()
        when {
            error != null -> KeywordsUiState.Error(error)
            a == null -> KeywordsUiState.Loading
            a.keywords.isEmpty() -> KeywordsUiState.Empty
            else -> KeywordsUiState.Content(
                title = a.title,
                topic = KeywordExtractor.topic(a.keywords),
                items = a.keywords.map { k ->
                    KeywordItem(k, tr[k.word], a.segments.getOrNull(k.segmentIndex)?.texto.orEmpty(), k.word in savedWords)
                },
                translation = st,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KeywordsUiState.Loading)

    init {
        viewModelScope.launch {
            try {
                val t = container.repository.getTranscription(transcriptionId)
                val segments = container.repository.getSegments(transcriptionId)
                val keywords = withContext(Dispatchers.Default) { KeywordExtractor.extract(segments.map { it.texto }) }
                analysis.value = Analysis(t?.titulo.orEmpty(), segments, keywords)
                if (keywords.isNotEmpty()) translateAll(keywords)
            } catch (e: Exception) {
                failure.value = e.message ?: "No se pudo analizar el audio"
            }
        }
    }

    private suspend fun translateAll(keywords: List<Keyword>) {
        status.value = TranslationStatus.Downloading
        try {
            container.translator.ensureModel()
            status.value = TranslationStatus.Ready
            for (k in keywords) {
                val text = try {
                    container.translator.translate(k.word)
                } catch (e: Exception) {
                    continue // una palabra que falle no detiene el resto
                }
                translations.update { it + (k.word to text) }
            }
        } catch (e: Exception) {
            status.value = TranslationStatus.Error(e.message ?: "Error de traducción")
        }
    }

    fun retryTranslation() {
        val keywords = analysis.value?.keywords ?: return
        viewModelScope.launch { translateAll(keywords) }
    }

    fun save(item: KeywordItem) {
        val a = analysis.value ?: return
        viewModelScope.launch {
            try {
                saveInternal(a, item)
            } catch (e: Exception) {
                _messages.trySend("No se pudo guardar la palabra: ${e.message}")
            }
        }
    }

    fun saveAll() {
        val a = analysis.value ?: return
        val pending = (uiState.value as? KeywordsUiState.Content)?.items?.filter { !it.saved } ?: return
        viewModelScope.launch {
            try {
                pending.forEach { saveInternal(a, it) }
                _messages.trySend("${pending.size} palabras guardadas")
            } catch (e: Exception) {
                _messages.trySend("No se pudieron guardar las palabras: ${e.message}")
            }
        }
    }

    private suspend fun saveInternal(a: Analysis, item: KeywordItem) {
        val segment = a.segments.getOrNull(item.keyword.segmentIndex)
        container.vocabulary.save(
            palabra = item.keyword.word,
            nota = item.translation.orEmpty(),
            contexto = item.example,
            transcriptionId = transcriptionId,
            inicioMs = segment?.inicioMs ?: 0L,
            tituloOrigen = a.title,
        )
    }
}
