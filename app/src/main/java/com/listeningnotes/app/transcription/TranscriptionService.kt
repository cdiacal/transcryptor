package com.listeningnotes.app.transcription

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.listeningnotes.app.ListeningNotesApp
import com.listeningnotes.app.MainActivity
import com.listeningnotes.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano que solo muestra la notificación de progreso mientras se transcribe
 * un audio largo (el trabajo real lo hace [TranscriptionManager] en el ámbito de la app).
 */
class TranscriptionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = (application as ListeningNotesApp).container.transcriptionManager

        if (intent?.action == ACTION_CANCEL) {
            manager.cancel()
            return START_NOT_STICKY
        }

        createChannel(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(this, JobState.Running(Stage.IMPORTING, null, "")), type)

        if (observer == null) {
            observer = scope.launch {
                manager.state.collect { state ->
                    if (state is JobState.Running) {
                        val nm = getSystemService(NotificationManager::class.java)
                        nm.notify(NOTIFICATION_ID, buildNotification(this@TranscriptionService, state))
                    } else {
                        ServiceCompat.stopForeground(this@TranscriptionService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "com.listeningnotes.app.action.CANCEL_TRANSCRIPTION"
        private const val CHANNEL_ID = "transcription"
        private const val NOTIFICATION_ID = 1001

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, "Transcripciones", NotificationManager.IMPORTANCE_LOW)
                channel.description = "Progreso de las transcripciones de audios largos"
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
        }

        private fun buildNotification(context: Context, state: JobState.Running): Notification {
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val cancel = PendingIntent.getService(
                context, 1, Intent(context, TranscriptionService::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val progress = state.progress
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(state.title.ifBlank { "Transcribiendo audio" })
                .setContentText(
                    if (progress != null) "${state.stage.label} ${(progress * 100).toInt()} %" else state.stage.label
                )
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(0, "Cancelar", cancel)
            if (progress != null) builder.setProgress(100, (progress * 100).toInt(), false)
            else builder.setProgress(0, 0, true)
            return builder.build()
        }
    }
}
