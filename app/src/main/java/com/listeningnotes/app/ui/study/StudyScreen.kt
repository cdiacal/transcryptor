@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.PictureAsPdf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.listeningnotes.app.audio.PlayerUiState
import com.listeningnotes.app.data.model.Segment
import com.listeningnotes.app.ui.common.CabeceraSimple
import com.listeningnotes.app.ui.common.FilaPestanas
import com.listeningnotes.app.ui.common.IconoCabecera
import com.listeningnotes.app.analysis.KeywordExtractor
import com.listeningnotes.app.ui.common.TarjetaPanel
import com.listeningnotes.app.ui.common.TextoDialog
import com.listeningnotes.app.ui.common.BotonPrincipal
import com.listeningnotes.app.ui.common.BotonSecundario
import com.listeningnotes.app.ui.common.abrirUrl
import com.listeningnotes.app.ui.common.urlDiccionario
import com.listeningnotes.app.ui.common.urlTraductor
import com.listeningnotes.app.data.model.Vocab
import com.listeningnotes.app.util.normalizeWord
import com.listeningnotes.app.util.splitTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.listeningnotes.app.ui.theme.colorAviso
import com.listeningnotes.app.ui.common.ErrorState
import com.listeningnotes.app.ui.common.LoadingState
import com.listeningnotes.app.ui.common.MessageState
import com.listeningnotes.app.ui.common.ObscuredText
import com.listeningnotes.app.util.formatClock
import kotlinx.coroutines.launch

private val SPEEDS = listOf(0.75f, 1f, 1.25f)

@Composable
fun StudyScreen(viewModel: StudyViewModel, onBack: () -> Unit, onKeywords: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    // Al salir de la pantalla dejamos de reproducir.
    DisposableEffect(Unit) { onDispose { viewModel.pause() } }

    var exportFormat by remember { mutableStateOf(ExportFormat.PDF_TIEMPOS) }
    var showExport by remember { mutableStateOf(false) }
    val saveTxt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) viewModel.saveTo(uri, ExportFormat.TXT)
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) viewModel.saveTo(uri, exportFormat)
    }

    val content = state as? StudyUiState.Content
    var actionsFor by remember { mutableStateOf<Segment?>(null) }
    var editing by remember { mutableStateOf<Segment?>(null) }
    val saved by viewModel.savedWords.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CabeceraSimple(
                titulo = content?.transcription?.titulo ?: "Estudio",
                onBack = onBack,
                subtitulo = content?.let { "${formatClock(it.transcription.duracionMs)} · ${it.allSegments.size} frases · ${it.transcription.modelo}" },
                acciones = {
                    if (content != null) {
                        IconoCabecera(Icons.Filled.ContentCopy, "Copiar todo") {
                            scope.launch {
                                viewModel.textForClipboard()?.let {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Transcripción", it))
                                    snackbar.showSnackbar("Texto copiado")
                                }
                            }
                        }
                        IconoCabecera(Icons.Filled.PictureAsPdf, "Exportar") { showExport = true }
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
                is StudyUiState.Content -> StudyContent(s, viewModel, onKeywords = onKeywords, onLongPress = { actionsFor = it })
            }
        }
    }

    if (showExport) {
        ExportDialog(
            selected = exportFormat,
            onSelect = { exportFormat = it },
            onShare = {
                showExport = false
                scope.launch {
                    val uri = viewModel.createShareUri(context, exportFormat) ?: return@launch
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = exportFormat.mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, "Compartir transcripción"))
                }
            },
            onSave = {
                showExport = false
                val name = viewModel.suggestedFileName(exportFormat)
                if (exportFormat == ExportFormat.TXT) saveTxt.launch(name) else savePdf.launch(name)
            },
            onDismiss = { showExport = false },
        )
    }

    actionsFor?.let { seg ->
        SegmentActionsDialog(
            segment = seg,
            saved = saved,
            onEditText = { editing = seg; actionsFor = null },
            onSaveWord = { word, note -> viewModel.saveWord(word, note, seg) },
            onRemoveWord = viewModel::removeWord,
            onTranslate = viewModel::translate,
            onDismiss = { actionsFor = null },
        )
    }
    editing?.let { seg ->
        TextoDialog(
            titulo = "Editar frase",
            inicial = seg.texto,
            minLines = 2,
            onConfirm = { if (it.isNotBlank()) viewModel.editSegment(seg, it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun StudyContent(s: StudyUiState.Content, vm: StudyViewModel, onKeywords: () -> Unit, onLongPress: (Segment) -> Unit) {
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
            item(key = "topic") {
                val topic = remember(s.allSegments) { KeywordExtractor.topic(KeywordExtractor.extract(s.allSegments.map { it.texto })) }
                if (topic.isNotEmpty()) {
                    TarjetaPanel(Modifier.fillMaxWidth(), onClick = onKeywords) {
                        Text("Vocabulario del audio", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            topic.joinToString(" · "), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Text(
                            "Toca para ver las palabras clave con su traducción",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            items(s.segments, key = { it.id }) { seg ->
                val hidden = s.hideText && seg.id !in s.revealed
                SegmentRow(
                    segment = seg,
                    active = seg.id == s.activeId,
                    hidden = hidden,
                    showEye = s.hideText,
                    onClick = { vm.onSegmentClick(seg) },
                    onLongClick = { onLongPress(seg) },
                    onToggleReveal = { vm.toggleReveal(seg.id) },
                    onToggleFavorite = { vm.toggleFavorite(seg) },
                )
            }
        }
    }
}

@Composable
private fun PlayerPanel(s: StudyUiState.Content, player: PlayerUiState, vm: StudyViewModel) {
    TarjetaPanel(Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp)) {
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
            Text(
                "${formatClock(shown)} / ${formatClock(duration)}",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }

        FilaPestanas(
            opciones = SPEEDS.map { speedLabel(it) },
            seleccion = SPEEDS.indexOf(player.speed).coerceAtLeast(1),
            onSelect = { vm.setSpeed(SPEEDS[it]) },
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LabeledSwitch("Repetir frase", s.loop, vm::setLoop)
            LabeledSwitch("Ocultar texto", s.hideText, vm::setHideText)
            LabeledSwitch("Solo favoritas", s.favoritesOnly, vm::setFavoritesOnly)
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = s.shadowing, onCheckedChange = vm::setShadowing)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Shadowing", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (player.waiting) "Repite en voz alta…" else "Pausa tras cada frase para repetirla",
                    fontSize = 11.sp,
                    color = if (player.waiting) colorAviso() else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (s.shadowing) {
            FilaPestanas(
                opciones = SHADOW_PAUSE_LABELS,
                seleccion = s.shadowPauseIndex,
                onSelect = vm::setShadowPause,
                modifier = Modifier.padding(top = 8.dp),
            )
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
    TarjetaPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        borderColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        contentPadding = PaddingValues(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 0.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "[${formatClock(segment.inicioMs)}]",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            ObscuredText(
                text = segment.texto,
                hidden = hidden,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            if (showEye) {
                IconButton(onClick = onToggleReveal) {
                    Icon(
                        if (hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        if (hidden) "Mostrar frase" else "Ocultar frase",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (segment.favorita) Icons.Filled.Star else Icons.Filled.StarBorder,
                    if (segment.favorita) "Quitar de favoritas" else "Marcar como favorita",
                    tint = if (segment.favorita) colorAviso() else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Acciones de una frase (pulsación larga): guardar palabras, traducir y editar. */
@Composable
private fun SegmentActionsDialog(
    segment: Segment,
    saved: Map<String, Vocab>,
    onEditText: () -> Unit,
    onSaveWord: (String, String) -> Unit,
    onRemoveWord: (String) -> Unit,
    onTranslate: suspend (String) -> String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<String?>(null) }

    selected?.let { word ->
        WordDialog(
            word = word,
            existing = saved[word],
            onSave = { note -> onSaveWord(word, note); selected = null },
            onTranslate = onTranslate,
            onRemove = { onRemoveWord(word); selected = null },
            onDismiss = { selected = null },
        )
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Frase") },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Text(
                    "Toca una palabra para guardarla en tu vocabulario.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    splitTokens(segment.texto).forEach { token ->
                        val key = normalizeWord(token)
                        if (key.isEmpty()) {
                            Text(token, fontSize = 15.sp)
                        } else {
                            val isSaved = key in saved
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSaved) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { selected = key }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                            ) {
                                Text(token, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonSecundario("Editar texto", onEditText, Modifier.weight(1f))
                    BotonSecundario("Traducir", { abrirUrl(context, urlTraductor(segment.texto)) }, Modifier.weight(1f))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun WordDialog(
    word: String,
    existing: Vocab?,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onTranslate: suspend (String) -> String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var translating by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf(existing?.nota ?: "") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(word) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    note, { note = it }, Modifier.fillMaxWidth(),
                    label = { Text("Traducción o nota") }, minLines = 2,
                )
                BotonSecundario(
                    if (translating) "Traduciendo…" else "Traducir al español",
                    {
                        translating = true
                        scope.launch {
                            onTranslate(word)?.let { note = it }
                            translating = false
                        }
                    },
                    Modifier.fillMaxWidth(), enabled = !translating,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonSecundario("Diccionario", { abrirUrl(context, urlDiccionario(word)) }, Modifier.weight(1f))
                    if (existing != null) BotonSecundario("Quitar", onRemove, Modifier.weight(1f))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(note) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ExportDialog(
    selected: ExportFormat,
    onSelect: (ExportFormat) -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val formats = ExportFormat.entries
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportar transcripción") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FilaPestanas(formats.map { it.label }, formats.indexOf(selected), { onSelect(formats[it]) })
                Text(
                    when (selected) {
                        ExportFormat.TXT -> "Una línea por frase con su marca de tiempo."
                        ExportFormat.PDF_TIEMPOS -> "Una tarjeta por frase con su marca [mm:ss] y las favoritas con estrella."
                        ExportFormat.PDF_TEXTO -> "Todo el texto seguido, en párrafos y sin tiempos."
                    },
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonPrincipal("Compartir", onShare, Modifier.weight(1f))
                    BotonSecundario("Guardar", onSave, Modifier.weight(1f))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
