@file:OptIn(ExperimentalLayoutApi::class)

package com.listeningnotes.app.ui.keywords

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.listeningnotes.app.ui.common.BarraProgreso
import com.listeningnotes.app.ui.common.BotonPrincipal
import com.listeningnotes.app.ui.common.CabeceraSimple
import com.listeningnotes.app.ui.common.ErrorState
import com.listeningnotes.app.ui.common.LoadingState
import com.listeningnotes.app.ui.common.MessageState
import com.listeningnotes.app.ui.common.TarjetaPanel
import com.listeningnotes.app.ui.theme.colorGasto
import com.listeningnotes.app.ui.theme.colorIngreso

@Composable
fun KeywordsScreen(viewModel: KeywordsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val content = state as? KeywordsUiState.Content
            CabeceraSimple(
                "Vocabulario del audio", onBack,
                subtitulo = content?.let { c -> "${c.items.size} palabras · ${c.title}" },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            when (val s = state) {
                KeywordsUiState.Loading -> LoadingState()
                is KeywordsUiState.Error -> ErrorState(s.message)
                KeywordsUiState.Empty -> MessageState(
                    Icons.Filled.Translate, "No hay vocabulario destacable",
                    detail = "El audio es muy corto o solo contiene palabras básicas.",
                )
                is KeywordsUiState.Content -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { TopicCard(s, onSaveAll = viewModel::saveAll) }
                    if (s.translation !is TranslationStatus.Ready) {
                        item { TranslationBanner(s.translation, viewModel::retryTranslation) }
                    }
                    items(s.items, key = { it.keyword.word }) { item ->
                        KeywordCard(item, onSave = { viewModel.save(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicCard(s: KeywordsUiState.Content, onSaveAll: () -> Unit) {
    TarjetaPanel(Modifier.fillMaxWidth()) {
        Text("Tema del audio", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.topic.forEach { word ->
                Box(
                    Modifier.clip(RoundedCornerShape(100.dp)).background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text(word, color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        val pending = s.items.count { !it.saved }
        Spacer(Modifier.height(14.dp))
        if (pending > 0) {
            BotonPrincipal("Guardar las $pending palabras", onSaveAll, Modifier.fillMaxWidth())
        } else {
            Text("Todas las palabras están guardadas", fontSize = 13.sp, color = colorIngreso(), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TranslationBanner(status: TranslationStatus, onRetry: () -> Unit) {
    TarjetaPanel(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
        when (status) {
            TranslationStatus.Downloading -> {
                Text("Preparando la traducción…", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Se descarga un modelo de ~30 MB solo la primera vez; después traduce sin conexión.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                BarraProgreso(null)
            }
            is TranslationStatus.Error -> {
                Text(status.message, fontSize = 13.sp, color = colorGasto())
                TextButton(onClick = onRetry) { Text("Reintentar") }
            }
            TranslationStatus.Ready -> Unit
        }
    }
}

@Composable
private fun KeywordCard(item: KeywordItem, onSave: () -> Unit) {
    TarjetaPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(item.keyword.word, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "  ×${item.keyword.count}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                Text(
                    item.translation ?: "…",
                    fontSize = 14.sp,
                    color = if (item.translation == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
                if (item.example.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "“${item.example}”", fontSize = 12.sp, fontStyle = FontStyle.Italic, maxLines = 3,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (item.saved) {
                Icon(Icons.Filled.Check, "Guardada", Modifier.padding(12.dp), tint = colorIngreso())
            } else {
                IconButton(onClick = onSave) { Icon(Icons.Filled.Add, "Guardar palabra", tint = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}
