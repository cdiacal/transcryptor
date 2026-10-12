package com.listeningnotes.app.ui.vocab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.listeningnotes.app.data.model.Vocab
import com.listeningnotes.app.ui.common.CabeceraSimple
import com.listeningnotes.app.ui.common.ErrorState
import com.listeningnotes.app.ui.common.LoadingState
import com.listeningnotes.app.ui.common.MessageState
import com.listeningnotes.app.ui.common.TarjetaPanel
import com.listeningnotes.app.ui.common.TextoDialog
import com.listeningnotes.app.ui.common.abrirUrl
import com.listeningnotes.app.ui.common.urlDiccionario

@Composable
fun VocabScreen(viewModel: VocabViewModel, onBack: () -> Unit, onOpenTranscription: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Vocab?>(null) }
    var deleting by remember { mutableStateOf<Vocab?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val n = (state as? VocabUiState.Content)?.items?.size ?: 0
            CabeceraSimple("Vocabulario", onBack, subtitulo = if (n == 1) "1 palabra guardada" else "$n palabras guardadas")
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            when (val s = state) {
                VocabUiState.Loading -> LoadingState()
                is VocabUiState.Error -> ErrorState(s.message)
                VocabUiState.Empty -> MessageState(
                    Icons.Filled.Book, "Aún no has guardado palabras",
                    detail = "En el modo estudio, mantén pulsada una frase y toca una palabra para guardarla aquí.",
                )
                is VocabUiState.Content -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(s.items, key = { it.id }) { item ->
                        TarjetaPanel(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.palabra, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { editing = item }) {
                                    Icon(Icons.Filled.Edit, "Editar nota", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { deleting = item }) {
                                    Icon(Icons.Filled.Delete, "Borrar palabra", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(
                                item.nota.ifBlank { "Sin traducción: toca el lápiz para añadirla" },
                                fontSize = 14.sp,
                                color = if (item.nota.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "“${item.contexto}”", fontSize = 13.sp, fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    item.tituloOrigen, Modifier.weight(1f), fontSize = 11.sp, maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { abrirUrl(context, urlDiccionario(item.palabra)) }) { Text("Diccionario") }
                                TextButton(onClick = { onOpenTranscription(item.transcriptionId) }) { Text("Ir al audio") }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { item ->
        TextoDialog(
            titulo = item.palabra, inicial = item.nota, etiqueta = "Traducción o nota", minLines = 2,
            onConfirm = { viewModel.updateNote(item, it); editing = null },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Borrar palabra") },
            text = { Text("¿Quitar «${item.palabra}» del vocabulario?") },
            confirmButton = { TextButton(onClick = { viewModel.delete(item); deleting = null }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}
