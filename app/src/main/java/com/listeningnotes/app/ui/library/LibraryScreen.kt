@file:OptIn(ExperimentalMaterial3Api::class)

package com.listeningnotes.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.listeningnotes.app.data.model.Transcription
import com.listeningnotes.app.transcription.JobState
import com.listeningnotes.app.ui.common.ErrorState
import com.listeningnotes.app.ui.common.LoadingState
import com.listeningnotes.app.ui.common.MessageState
import com.listeningnotes.app.ui.common.ModelBanner
import com.listeningnotes.app.util.formatClock
import java.text.DateFormat
import java.util.Date

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onNew: () -> Unit,
    onOpen: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val model by viewModel.modelStatus.collectAsStateWithLifecycle()
    val job by viewModel.jobState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    var renaming by remember { mutableStateOf<Transcription?>(null) }
    var deleting by remember { mutableStateOf<Transcription?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Biblioteca") },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Ajustes") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Nueva transcripción") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Buscar en títulos y frases") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Filled.Close, "Borrar búsqueda") }
                        }
                    },
                )
                model?.let {
                    ModelBanner(it.spec, it.state, viewModel::downloadModel, viewModel::pauseModelDownload)
                }
                JobBanner(job, onOpenJob = onNew, onOpenResult = { id ->
                    viewModel.acknowledgeJob()
                    onOpen(id)
                }, onDismiss = viewModel::acknowledgeJob)
            }

            when (val s = state) {
                LibraryUiState.Loading -> LoadingState()
                is LibraryUiState.Error -> ErrorState(s.message, onRetry = viewModel::retryLoad)
                is LibraryUiState.Empty -> if (s.searching) {
                    MessageState(Icons.Filled.Search, "Sin resultados", detail = "Prueba con otra palabra.")
                } else {
                    MessageState(
                        Icons.Filled.Mic, "Todavía no hay transcripciones",
                        detail = "Pulsa «Nueva transcripción» o comparte un audio de WhatsApp con esta app.",
                    )
                }
                is LibraryUiState.Content -> LazyColumn(
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(s.items, key = { it.id }) { item ->
                        TranscriptionRow(
                            item,
                            onClick = { onOpen(item.id) },
                            onRename = { renaming = item },
                            onDelete = { deleting = item },
                        )
                    }
                }
            }
        }
    }

    renaming?.let { item ->
        RenameDialog(
            initial = item.titulo,
            onConfirm = { viewModel.rename(item, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Eliminar transcripción") },
            text = { Text("Se borrarán «${item.titulo}», sus frases y la copia del audio. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(item); deleting = null }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun TranscriptionRow(
    item: Transcription,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.titulo, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Spacer(Modifier.size(2.dp))
                val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.fecha))
                Text(
                    "${formatClock(item.duracionMs)} · $date · ${item.modelo}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Más opciones") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Renombrar") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Eliminar") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar") },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Resumen del trabajo de transcripción en curso / terminado, visible desde la biblioteca. */
@Composable
private fun JobBanner(
    job: JobState,
    onOpenJob: () -> Unit,
    onOpenResult: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    when (job) {
        JobState.Idle -> Unit
        is JobState.Running -> Card(
            Modifier.fillMaxWidth().clickable(onClick = onOpenJob),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(job.title.ifBlank { "Transcribiendo" }, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(job.stage.label, style = MaterialTheme.typography.bodySmall)
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                val p = job.progress
                if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        is JobState.Done -> Card(
            Modifier.fillMaxWidth().clickable { onOpenResult(job.transcriptionId) },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        ) {
            Text("Transcripción lista. Toca para abrirla.", Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
        }
        is JobState.Failed -> Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(job.message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        }
    }
}
