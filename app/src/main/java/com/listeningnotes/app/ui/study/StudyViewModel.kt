package com.listeningnotes.app.ui.study

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.listeningnotes.app.AppContainer
import com.listeningnotes.app.audio.PlayerUiState
import com.listeningnotes.app.audio.StudyPlayer
import com.listeningnotes.app.audio.Tramo
import com.listeningnotes.app.data.model.Vocab
import com.listeningnotes.app.data.model.Segment
import com.listeningnotes.app.data.model.Transcription
import com.listeningnotes.app.export.ExportSegment
import com.listeningnotes.app.export.SegmentExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface StudyUiState {
    data object Loading : StudyUiState
    data object NotFound : StudyUiState
    data class Error(val message: String) : StudyUiState
    data class Content(
        val transcription: Transcription,
        /** Todas las frases (para exportar). */
        val allSegments: List<Segment>,
        /** Frases visibles tras aplicar el filtro de favoritas. */
        val segments: List<Segment>,
        val activeId: Long?,
        val hideText: Boolean,
        val revealed: Set<Long>,
        val loop: Boolean,
        val favoritesOnly: Boolean,
        val audioAvailable: Boolean,
        val shadowing: Boolean,
        val shadowPauseIndex: Int,
    ) : StudyUiState
}

private data class StudyPrefs(
    val hideText: Boolean = false,
    val revealed: Set<Long> = emptySet(),
    val loop: Boolean = false,
    val favoritesOnly: Boolean = false,
    val shadowing: Boolean = false,
    val shadowPauseIndex: Int = 1,
)

/** Opciones de pausa del shadowing: milisegundos (-1 = igual a la duración de la frase). */
val SHADOW_PAUSES_MS = listOf(1_000L, 2_000L, 4_000L, -1L)
val SHADOW_PAUSE_LABELS = listOf("1 s", "2 s", "4 s", "= frase")

@OptIn(ExperimentalCoroutinesApi::class)
class StudyViewModel(private val container: AppContainer, private val transcriptionId: Long) : ViewModel() {

    private val repository = container.repository
    private val player = StudyPlayer(container.appContext)
    private val prefs = MutableStateFlow(StudyPrefs())
    private val audioAvailable = MutableStateFlow(true)
    private val retry = MutableStateFlow(0)

    val playerState: StateFlow<PlayerUiState> = player.state

    /** Palabras ya guardadas, por clave normalizada. */
    val savedWords: StateFlow<Map<String, Vocab>> = container.vocabulary.observeAll()
        .map { list -> list.associateBy { it.palabra } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val uiState: StateFlow<StudyUiState> = retry.flatMapLatest {
        val segmentsFlow = repository.observeSegments(transcriptionId)
        val activeFlow = segmentsFlow.flatMapLatest { segs ->
            player.state.map { findActiveSegmentId(segs, it.positionMs) }.distinctUntilChanged()
        }
        combine(
            repository.observeTranscription(transcriptionId), segmentsFlow, prefs, activeFlow, audioAvailable,
        ) { transcription, segments, p, activeId, audio ->
            if (transcription == null) {
                StudyUiState.NotFound
            } else {
                StudyUiState.Content(
                    transcription = transcription,
                    allSegments = segments,
                    segments = if (p.favoritesOnly) segments.filter { it.favorita } else segments,
                    activeId = activeId,
                    hideText = p.hideText,
                    revealed = p.revealed,
                    loop = p.loop,
                    favoritesOnly = p.favoritesOnly,
                    audioAvailable = audio,
                    shadowing = p.shadowing,
                    shadowPauseIndex = p.shadowPauseIndex,
                ) as StudyUiState
            }
        }.catch { emit(StudyUiState.Error(it.message ?: "No se pudo cargar la transcripción")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudyUiState.Loading)

    init {
        // Siguiente frase visible (respeta el filtro de favoritas) para el modo shadowing.
        player.nextTramo = { id ->
            val list = (uiState.value as? StudyUiState.Content)?.segments.orEmpty()
            val i = list.indexOfFirst { it.id == id }
            if (i >= 0) list.getOrNull(i + 1)?.let { Tramo(it.id, it.inicioMs, it.finMs) } else null
        }
        viewModelScope.launch {
            val t = try {
                repository.getTranscription(transcriptionId)
            } catch (e: Exception) {
                null
            }
            if (t != null) {
                val file = File(t.rutaAudio)
                if (file.isFile) player.prepare(file) else audioAvailable.value = false
            }
        }
    }

    // --- Reproducción ---

    fun togglePlay() = player.togglePlay()
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun setSpeed(speed: Float) = player.setSpeed(speed)
    fun pause() = player.pause()

    fun onSegmentClick(segment: Segment) {
        if (audioAvailable.value) player.playSegment(Tramo(segment.id, segment.inicioMs, segment.finMs))
    }

    fun setLoop(enabled: Boolean) {
        prefs.update { it.copy(loop = enabled) }
        player.setLoop(enabled)
    }

    fun setShadowing(enabled: Boolean) {
        prefs.update { it.copy(shadowing = enabled) }
        player.setShadowing(enabled, SHADOW_PAUSES_MS[prefs.value.shadowPauseIndex])
    }

    fun setShadowPause(index: Int) {
        prefs.update { it.copy(shadowPauseIndex = index) }
        player.setShadowing(prefs.value.shadowing, SHADOW_PAUSES_MS[index])
    }

    // --- Vocabulario ---

    fun saveWord(word: String, note: String, segment: Segment) {
        val title = (uiState.value as? StudyUiState.Content)?.transcription?.titulo ?: ""
        viewModelScope.launch {
            try {
                container.vocabulary.save(word, note, segment.texto, segment.transcriptionId, segment.inicioMs, title)
                _messages.trySend("Palabra guardada")
            } catch (e: Exception) {
                _messages.trySend("No se pudo guardar la palabra: ${e.message}")
            }
        }
    }

    fun removeWord(word: String) {
        viewModelScope.launch {
            try {
                container.vocabulary.remove(word)
            } catch (e: Exception) {
                _messages.trySend("No se pudo quitar la palabra: ${e.message}")
            }
        }
    }

    // --- Modo listening y favoritas ---

    fun setHideText(enabled: Boolean) = prefs.update { it.copy(hideText = enabled, revealed = emptySet()) }

    fun toggleReveal(segmentId: Long) = prefs.update {
        it.copy(revealed = if (segmentId in it.revealed) it.revealed - segmentId else it.revealed + segmentId)
    }

    fun setFavoritesOnly(enabled: Boolean) = prefs.update { it.copy(favoritesOnly = enabled) }

    fun toggleFavorite(segment: Segment) {
        viewModelScope.launch {
            try {
                repository.setFavorite(segment.id, !segment.favorita)
            } catch (e: Exception) {
                _messages.trySend("No se pudo actualizar la favorita: ${e.message}")
            }
        }
    }

    fun editSegment(segment: Segment, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                repository.updateSegmentText(segment.id, text)
            } catch (e: Exception) {
                _messages.trySend("No se pudo guardar el texto: ${e.message}")
            }
        }
    }

    fun retryLoad() {
        retry.value++
    }

    // --- Exportación ---

    private suspend fun exportText(): String? {
        val t = repository.getTranscription(transcriptionId) ?: return null
        val segs = repository.getSegments(transcriptionId).map { ExportSegment(it.inicioMs, it.finMs, it.texto) }
        return SegmentExporter.toPlainText(t.titulo, segs)
    }

    /** Texto completo para el portapapeles. */
    suspend fun textForClipboard(): String? = try {
        exportText()
    } catch (e: Exception) {
        _messages.trySend("No se pudo preparar el texto: ${e.message}")
        null
    }

    /** Nombre de fichero sugerido para "Guardar". */
    fun suggestedFileName(): String {
        val title = (uiState.value as? StudyUiState.Content)?.transcription?.titulo ?: "transcripcion"
        return SegmentExporter.suggestedFileName(title)
    }

    /** Escribe el .txt en el documento elegido por el usuario (CreateDocument). */
    fun saveTo(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = exportText() ?: error("La transcripción ya no existe")
                withContext(Dispatchers.IO) {
                    container.appContext.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("No se pudo abrir el destino")
                }
                _messages.trySend("Guardado")
            } catch (e: Exception) {
                _messages.trySend("No se pudo guardar el archivo: ${e.message}")
            }
        }
    }

    /** Crea el .txt en la caché y devuelve un content:// seguro (FileProvider) para compartirlo. */
    suspend fun createShareUri(context: Context): Uri? = try {
        val text = exportText() ?: error("La transcripción ya no existe")
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, suggestedFileName())
            file.writeText(text, Charsets.UTF_8)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    } catch (e: Exception) {
        _messages.trySend("No se pudo preparar el archivo: ${e.message}")
        null
    }

    override fun onCleared() {
        player.release()
    }
}
