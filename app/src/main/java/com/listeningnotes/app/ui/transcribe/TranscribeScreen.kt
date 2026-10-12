@file:OptIn(ExperimentalMaterial3Api::class)

package com.listeningnotes.app.ui.transcribe

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.listeningnotes.app.transcription.JobState
import com.listeningnotes.app.ui.common.ModelBanner
import com.listeningnotes.app.ui.common.MessageState

@Composable
fun TranscribeScreen(
    viewModel: TranscribeViewModel,
    onBack: () -> Unit,
    onFinished: (Long) -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val running = ui.job is JobState.Running

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    // Resultado listo: abrimos la pantalla de estudio.
    val job = ui.job
    LaunchedEffect(job) {
        if (job is JobState.Done) {
            viewModel.acknowledge()
            onFinished(job.transcriptionId)
        }
    }

    // Pantalla activa mientras se transcribe.
    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    // Permiso de notificaciones (solo Android 13+); si se rechaza, todo sigue funcionando sin notificación.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onAudioSelected(uri)
    }
    val pickAudio = { picker.launch(arrayOf("audio/*", "application/ogg")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nueva transcripción") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ui.model?.let {
                ModelBanner(it.spec, it.state, viewModel::downloadModel, viewModel::pauseModelDownload)
            }

            when {
                job is JobState.Running -> RunningContent(job, onCancel = viewModel::cancelJob)
                job is JobState.Failed -> MessageState(
                    Icons.Filled.Warning, "No se pudo transcribir", detail = job.message,
                    actionLabel = "Elegir otro archivo",
                    onAction = { viewModel.acknowledge(); pickAudio() },
                    modifier = Modifier.weight(1f),
                )
                ui.waitingForModel -> Column(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Audio listo. La transcripción empezará automáticamente cuando termine la descarga del modelo.",
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = viewModel::cancelWaiting) { Text("Cancelar") }
                }
                else -> MessageState(
                    Icons.Filled.FolderOpen, "Elige un audio en inglés",
                    detail = "Funciona con m4a, opus, ogg, mp3 y wav. También puedes compartir un audio de WhatsApp directamente con esta app.",
                    actionLabel = "Elegir archivo",
                    onAction = pickAudio,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RunningContent(job: JobState.Running, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        if (job.title.isNotBlank()) {
            Text(job.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, textAlign = TextAlign.Center)
        }
        Text(job.stage.label, style = MaterialTheme.typography.bodyLarge)
        val p = job.progress
        if (p != null) {
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            Text("${(p * 100).toInt()} %", style = MaterialTheme.typography.bodyMedium)
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(
            "Todo se procesa en tu móvil, sin conexión. Los audios largos pueden tardar varios minutos; " +
                "la pantalla se mantiene encendida mientras tanto.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onCancel) { Text("Cancelar") }
        Spacer(Modifier.size(1.dp))
    }
}
