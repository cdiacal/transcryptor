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
)

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

    // Tramo activo (null = reproducción libre).
    private var segStart = 0L
    private var segEnd = 0L
    private var segActive = false
    private var loop = false
    private var speed = 1f

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = publish()

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && segActive && loop) {
                // El tramo llega hasta el final del fichero: reiniciamos el bucle.
                player.seekTo(segStart)
                player.play()
            }
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
        segActive = false
        player.seekTo(positionMs.coerceAtLeast(0))
        publish()
    }

    /** Reproduce desde [startMs] hasta [endMs]; con [loop] se repite hasta que se pause o se elija otro tramo. */
    fun playSegment(startMs: Long, endMs: Long) {
        segStart = startMs
        segEnd = endMs
        segActive = true
        player.seekTo(startMs)
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

    fun pause() = player.pause()

    private fun tick() {
        if (segActive && player.isPlaying && player.currentPosition >= segEnd - END_MARGIN_MS) {
            if (loop) {
                player.seekTo(segStart)
            } else {
                player.pause()
                player.seekTo(segEnd)
                segActive = false
            }
        }
        publish()
    }

    private fun publish() {
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        _state.value = _state.value.copy(
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition,
            durationMs = duration,
            speed = speed,
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
