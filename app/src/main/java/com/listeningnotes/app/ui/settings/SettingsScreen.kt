@file:OptIn(ExperimentalMaterial3Api::class)

package com.listeningnotes.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.listeningnotes.app.data.repository.AppSettings
import com.listeningnotes.app.data.repository.ThemeMode
import com.listeningnotes.app.ui.common.formatMegabytes
import com.listeningnotes.app.whisper.ModelSpec
import com.listeningnotes.app.whisper.ModelState
import com.listeningnotes.app.whisper.Models

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val modelStates by viewModel.modelStates.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("Modelo de transcripción")
            Models.all.forEach { spec ->
                ModelRow(
                    spec = spec,
                    selected = spec.id == settings.modelId,
                    state = modelStates[spec.id] ?: ModelState.NotInstalled(),
                    onSelect = { viewModel.selectModel(spec) },
                    onDownload = { viewModel.download(spec) },
                    onPause = { viewModel.pause(spec) },
                    onDelete = { viewModel.delete(spec) },
                )
            }
            Text(
                "small.en es más preciso pero más lento y necesita más memoria. Se descarga la primera vez que lo eliges.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Hilos de CPU")
            val max = AppSettings.maxThreads()
            Text("${settings.threads} de $max", style = MaterialTheme.typography.bodyMedium)
            if (max > 1) {
                Slider(
                    value = settings.threads.toFloat(),
                    onValueChange = { viewModel.setThreads(it.toInt().coerceIn(1, max)) },
                    valueRange = 1f..max.toFloat(),
                    steps = (max - 2).coerceAtLeast(0),
                )
            }
            Text(
                "Más hilos aceleran la transcripción pero calientan el móvil. Recomendado: ${AppSettings.defaultThreads()}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Tema")
            ThemeMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidth().selectable(
                        selected = settings.theme == mode,
                        onClick = { viewModel.setTheme(mode) },
                        role = Role.RadioButton,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.theme == mode, onClick = null)
                    Text(mode.label, Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Motor")
            var info by remember { mutableStateOf<String?>(null) }
            OutlinedButton(onClick = { info = viewModel.engineInfo() }) { Text("Ver información del motor") }
            info?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ModelRow(
    spec: ModelSpec,
    selected: Boolean,
    state: ModelState,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onSelect)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(spec.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    when (state) {
                        ModelState.Ready -> "Instalado"
                        is ModelState.Downloading -> "Descargando…"
                        is ModelState.Error -> "Error: ${state.message}"
                        is ModelState.NotInstalled ->
                            if (state.partialBytes > 0) "Descarga a medias (${formatMegabytes(state.partialBytes)})" else "No descargado"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state is ModelState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (state) {
                ModelState.Ready -> TextButton(onClick = onDelete) { Text("Borrar") }
                is ModelState.Downloading -> TextButton(onClick = onPause) { Text("Pausar") }
                is ModelState.Error -> TextButton(onClick = onDownload) { Text("Reintentar") }
                is ModelState.NotInstalled -> TextButton(onClick = onDownload) {
                    Text(if (state.partialBytes > 0) "Reanudar" else "Descargar")
                }
            }
        }
        if (state is ModelState.Downloading) {
            val fraction = if (state.total > 0) (state.downloaded.toFloat() / state.total).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(start = 48.dp))
            Text(
                "${(fraction * 100).toInt()} %  ·  ${formatMegabytes(state.downloaded)} / ${formatMegabytes(state.total)}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 48.dp),
            )
        }
    }
}
