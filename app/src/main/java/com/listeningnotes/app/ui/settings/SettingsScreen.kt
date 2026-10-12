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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.listeningnotes.app.data.repository.AppSettings
import com.listeningnotes.app.data.repository.ThemeMode
import com.listeningnotes.app.ui.common.BarraProgreso
import com.listeningnotes.app.ui.common.BotonSecundario
import com.listeningnotes.app.ui.common.CabeceraSimple
import com.listeningnotes.app.ui.common.FilaPestanas
import com.listeningnotes.app.ui.common.TarjetaPanel
import com.listeningnotes.app.ui.common.formatMegabytes
import com.listeningnotes.app.ui.theme.colorGasto
import com.listeningnotes.app.whisper.ModelSpec
import com.listeningnotes.app.whisper.ModelState
import com.listeningnotes.app.whisper.Models

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val modelStates by viewModel.modelStates.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { CabeceraSimple("Ajustes", onBack, subtitulo = "Modelo, rendimiento y apariencia") },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TarjetaPanel(Modifier.fillMaxWidth()) {
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
            }

            TarjetaPanel(Modifier.fillMaxWidth()) {
                SectionTitle("Hilos de CPU")
                val max = AppSettings.maxThreads()
                Text("${settings.threads} de $max", fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
            }

            TarjetaPanel(Modifier.fillMaxWidth()) {
                SectionTitle("Frases")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dividir en frases cortas", Modifier.weight(1f), fontSize = 14.sp)
                    androidx.compose.material3.Switch(settings.splitSentences, viewModel::setSplitSentences)
                }
                Text(
                    "Whisper a veces junta varias frases en una. Al activarlo, las transcripciones nuevas se parten por la " +
                        "puntuación para que repetir una frase sea más preciso. Los tiempos se reparten según la longitud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TarjetaPanel(Modifier.fillMaxWidth()) {
                SectionTitle("Tema")
                val modos = ThemeMode.entries
                FilaPestanas(
                    opciones = modos.map { it.label },
                    seleccion = modos.indexOf(settings.theme),
                    onSelect = { viewModel.setTheme(modos[it]) },
                )
            }

            TarjetaPanel(Modifier.fillMaxWidth()) {
                SectionTitle("Motor")
                var info by remember { mutableStateOf<String?>(null) }
                BotonSecundario("Ver información del motor", { info = viewModel.engineInfo() })
                info?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
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
                    color = if (state is ModelState.Error) colorGasto() else MaterialTheme.colorScheme.onSurfaceVariant,
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
            BarraProgreso(fraction, Modifier.padding(start = 48.dp))
            Text(
                "${(fraction * 100).toInt()} %  ·  ${formatMegabytes(state.downloaded)} / ${formatMegabytes(state.total)}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 48.dp),
            )
        }
    }
}
