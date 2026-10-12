@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.listeningnotes.app.ui.study

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.listeningnotes.app.audio.PlayerUiState
import com.listeningnotes.app.data.model.Segment
import com.listeningnotes.app.ui.common.ErrorState
import com.listeningnotes.app.ui.common.LoadingState
import com.listeningnotes.app.ui.common.MessageState
import com.listeningnotes.app.ui.common.ObscuredText
import com.listeningnotes.app.util.formatClock
import kotlinx.coroutines.launch

private val SPEEDS = listOf(0.75f, 1f, 1.25f)

@Composable
fun StudyScreen(viewModel: StudyViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    // Al salir de la pantalla dejamos de reproducir.
    DisposableEffect(Unit) { onDispose { viewModel.pause() } }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) viewModel.saveTo(uri)
    }

    val content = state as? StudyUiState.Content
    var editing by remember { mutableStateOf<Segment?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(content?.transcription?.titulo ?: "Estudio", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    if (content != null) {
                        IconButton(onClick = {
                            scope.launch {
                                viewModel.textForClipboard()?.let {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Transcripción", it))
                                    snackbar.showSnackbar("Texto copiado")
                                }
                            }
                        }) { Icon(Icons.Filled.ContentCopy, "Copiar todo") }
                        IconButton(onClick = {
                            scope.launch {
                                val uri = viewModel.createShareUri(context) ?: return@launch
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, "Compartir transcripción"))
                            }
                        }) { Icon(Icons.Filled.Share, "Compartir como .txt") }
                        IconButton(onClick = { saveLauncher.launch(viewModel.suggestedFileName()) }) {
                            Icon(Icons.Filled.Save, "Guardar como .txt")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            when (val s = state) {
                StudyUiState.Loading -> LoadingState()
                StudyUiState.NotFound -> MessageState(Icons.Filled.Warning, "Esta transcripción ya no existe", actionLabel = "Volver", onAction = onBack)
                is StudyUiState.Error -> ErrorState(s.message, onRetry = viewModel::retryLoad)
                is StudyUiState.Content -> StudyContent(s, viewModel, onEdit = { editing = it })
            }
        }
    }

    editing?.let { seg ->
        EditDialog(
            initial = seg.texto,
            onConfirm = { viewModel.editSegment(seg, it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun StudyContent(s: StudyUiState.Content, vm: StudyViewModel, onEdit: (Segment) -> Unit) {
    val player by vm.playerState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Scroll automático hacia la frase que suena (solo mientras se reproduce, para no pelear con el usuario).
    val isPlaying by rememberUpdatedState(player.isPlaying)
    LaunchedEffect(s.activeId) {
        val id = s.activeId ?: return@LaunchedEffect
        val index = s.segments.indexOfFirst { it.id == id }
        if (index >= 0 && isPlaying) listState.animateScrollToItem((index - 1).coerceAtLeast(0))
    }

    PlayerPanel(s, player, vm)

    if (!s.audioAvailable) {
        Text(
            "El archivo de audio ya no está disponible: solo puedes leer el texto.",
            Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (s.segments.isEmpty()) {
        MessageState(
            Icons.Filled.StarBorder,
            if (s.favoritesOnly) "Aún no hay frases favoritas" else "Sin frases",
            detail = if (s.favoritesOnly) "Marca frases con la estrella para repasarlas aquí." else null,
        )
    } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(s.segments, key = { it.id }) { seg ->
                val hidden = s.hideText && seg.id !in s.revealed
                SegmentRow(
                    segment = seg,
                    active = seg.id == s.activeId,
                    hidden = hidden,
                    showEye = s.hideText,
                    onClick = { vm.onSegmentClick(seg) },
                    onLongClick = { onEdit(seg) },
                    onToggleReveal = { vm.toggleReveal(seg.id) },
                    onToggleFavorite = { vm.toggleFavorite(seg) },
                )
            }
        }
    }
}

@Composable
private fun PlayerPanel(s: StudyUiState.Content, player: PlayerUiState, vm: StudyViewModel) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Barra de progreso: mientras se arrastra mostramos el valor local y solo buscamos al soltar.
            var dragging by remember { mutableStateOf(false) }
            var dragValue by remember { mutableFloatStateOf(0f) }
            val duration = if (player.durationMs > 0) player.durationMs else s.transcription.duracionMs
            val fraction = if (duration > 0) (player.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(onClick = vm::togglePlay, enabled = s.audioAvailable) {
                    Icon(
                        if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (player.isPlaying) "Pausar" else "Reproducir",
                    )
                }
                Slider(
                    value = if (dragging) dragValue else fraction,
                    onValueChange = { dragging = true; dragValue = it },
                    onValueChangeFinished = {
                        vm.seekTo((dragValue * duration).toLong())
                        dragging = false
                    },
                    enabled = s.audioAvailable && duration > 0,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                val shown = if (dragging) (dragValue * duration).toLong() else player.positionMs
                Text("${formatClock(shown)} / ${formatClock(duration)}", style = MaterialTheme.typography.labelMedium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                SPEEDS.forEach { speed ->
                    FilterChip(
                        selected = player.speed == speed,
                        onClick = { vm.setSpeed(speed) },
                        label = { Text(speedLabel(speed)) },
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledSwitch("Repetir frase", s.loop, vm::setLoop)
                LabeledSwitch("Ocultar texto", s.hideText, vm::setHideText)
                LabeledSwitch("Solo favoritas", s.favoritesOnly, vm::setFavoritesOnly)
            }
        }
    }
}

private fun speedLabel(speed: Float): String = when (speed) {
    0.75f -> "0.75x"
    1.25f -> "1.25x"
    else -> "1x"
}

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Switch(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SegmentRow(
    segment: Segment,
    active: Boolean,
    hidden: Boolean,
    showEye: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleReveal: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val container = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "[${formatClock(segment.inicioMs)}]",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            ObscuredText(
                text = segment.texto,
                hidden = hidden,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            if (showEye) {
                IconButton(onClick = onToggleReveal) {
                    Icon(
                        if (hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        if (hidden) "Mostrar frase" else "Ocultar frase",
                    )
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (segment.favorita) Icons.Filled.Star else Icons.Filled.StarBorder,
                    if (segment.favorita) "Quitar de favoritas" else "Marcar como favorita",
                    tint = if (segment.favorita) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EditDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar frase") },
        text = { OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 2) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
