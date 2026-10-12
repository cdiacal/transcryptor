package com.listeningnotes.app.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val error: String? = null,
    /** Pausa de shadowing en curso: es el momento de repetir la frase en voz alta. */
    val waiting: Boolean = false,
)

/** Tramo de audio asociado a una frase. */
data class Tramo(val id: Long, val startMs: Long, val endMs: Long)

/**
 * Reproductor para el modo estudio sobre Media3 ExoPlayer. Debe usarse desde el hilo principal.
 * Soporta reproducir un tramo [inicio, fin) con bucle opcional y cambio de velocidad sin cambiar el tono.
 */
class StudyPlayer(context: Context) {

    private val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .also { it.setSeekParameters(SeekParameters.EXACT) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    // Tramo activo (segActive = false: reproducción libre).
    private var segId = -1L
    private var segStart = 0L
    private var segEnd = 0L
    private var segActive = false
    private var loop = false
    private var speed = 1f

    // Shadowing: al acabar cada frase se pausa para que el usuario la repita y luego pasa a la siguiente.
    private var shadowing = false
    private var shadowPauseMs = 2_000L // -1 = igual a la duración de la frase
    private var waiting = false
    private var waitJob: Job? = null

    /** Devuelve la frase siguiente a la de id dado (null = no hay más). */
    var nextTramo: ((Long) -> Tramo?)? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = publish()

        override fun onPlaybackStateChanged(playbackState: Int) {
            // El tramo llega hasta el final del fichero: se trata como fin de frase.
            if (playbackState == Player.STATE_ENDED && segActive) onSegmentEnd()
            publish()
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(
                isPlaying = false,
                error = "No se pudo reproducir el audio (${error.errorCodeName})",
            )
        }
    }

    init {
        player.addListener(listener)
        ticker = scope.launch {
            while (isActive) {
                tick()
                delay(if (player.isPlaying) TICK_PLAYING_MS else TICK_IDLE_MS)
            }
        }
    }

    fun prepare(file: File) {
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.playbackParameters = PlaybackParameters(speed, 1f)
        player.prepare()
    }

    fun togglePlay() {
        cancelWait()
        if (player.isPlaying) {
            player.pause()
        } else {
            // Si estaba parado al final del fichero, volvemos al principio.
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    /** Búsqueda libre desde la barra de progreso: abandona el tramo activo. */
    fun seekTo(positionMs: Long) {
        cancelWait()
        segActive = false
        player.seekTo(positionMs.coerceAtLeast(0))
        publish()
    }

    /** Reproduce el tramo; con bucle se repite y con shadowing se pausa entre frases. */
    fun playSegment(tramo: Tramo) {
        cancelWait()
        segId = tramo.id
        segStart = tramo.startMs
        segEnd = tramo.endMs
        segActive = true
        player.seekTo(tramo.startMs)
        player.play()
        publish()
    }

    fun setLoop(enabled: Boolean) {
        loop = enabled
    }

    fun setSpeed(value: Float) {
        speed = value
        // Tono (pitch) fijo a 1: ExoPlayer cambia solo el tempo.
        player.playbackParameters = PlaybackParameters(value, 1f)
        publish()
    }

    fun setShadowing(enabled: Boolean, pauseMs: Long) {
        shadowing = enabled
        shadowPauseMs = pauseMs
        if (!enabled) cancelWait()
        publish()
    }

    fun pause() {
        cancelWait()
        player.pause()
    }

    private fun cancelWait() {
        waitJob?.cancel()
        waitJob = null
        waiting = false
    }

    /** Fin de la frase: bucle, pausa de shadowing o parar. */
    private fun onSegmentEnd() {
        if (shadowing) {
            val ended = Tramo(segId, segStart, segEnd)
            player.pause()
            segActive = false
            waiting = true
            publish()
            val pause = if (shadowPauseMs < 0) (segEnd - segStart).coerceAtLeast(1_000L) else shadowPauseMs
            waitJob = scope.launch {
                delay(pause)
                waiting = false
                val next = if (loop) ended else nextTramo?.invoke(ended.id)
                if (next != null) playSegment(next) else publish()
            }
        } else if (loop) {
            player.seekTo(segStart)
            player.play()
        } else {
            player.pause()
            player.seekTo(segEnd)
            segActive = false
        }
    }

    private fun tick() {
        if (segActive && player.isPlaying && player.currentPosition >= segEnd - END_MARGIN_MS) onSegmentEnd()
        publish()
    }

    private fun publish() {
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        _state.value = _state.value.copy(
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition,
            durationMs = duration,
            speed = speed,
            waiting = waiting,
        )
    }

    fun release() {
        scope.cancel()
        player.removeListener(listener)
        player.release()
    }

    private companion object {
        const val TICK_PLAYING_MS = 40L
        const val TICK_IDLE_MS = 250L
        const val END_MARGIN_MS = 30L
    }
}
