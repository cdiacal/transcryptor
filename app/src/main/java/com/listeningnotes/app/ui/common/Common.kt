package com.listeningnotes.app.ui.common

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.listeningnotes.app.ui.theme.colorGasto
import com.listeningnotes.app.whisper.ModelSpec
import com.listeningnotes.app.whisper.ModelState

/** Factoría sencilla de ViewModels para la inyección manual (sin Hilt). */
class SimpleFactory<T : ViewModel>(private val create: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun MessageState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (detail != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                detail, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            BotonPrincipal(actionLabel, onAction)
        }
    }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) =
    MessageState(Icons.Filled.Warning, "Algo ha ido mal", modifier, message, onRetry?.let { "Reintentar" }, onRetry)

/** Texto que se difumina (modo listening) hasta que se revela. */
@Composable
fun ObscuredText(text: String, hidden: Boolean, style: TextStyle, modifier: Modifier = Modifier) {
    when {
        !hidden -> Text(text, style = style, modifier = modifier)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            Text(text, style = style, modifier = modifier.blur(9.dp, BlurredEdgeTreatment.Unbounded))
        else -> // Sin Modifier.blur antes de Android 12: ocultamos el texto tras una barra del mismo tamaño.
            Box(modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f), RoundedCornerShape(6.dp))) {
                Text(text, style = style, color = Color.Transparent)
            }
    }
}

fun formatMegabytes(bytes: Long): String = "%.0f MB".format(bytes / 1_000_000.0)

/**
 * Tarjeta con el estado del modelo seleccionado (descarga con progreso, error con reintento, etc.).
 * No muestra nada cuando el modelo está listo.
 */
@Composable
fun ModelBanner(
    spec: ModelSpec,
    state: ModelState,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is ModelState.Ready) return
    TarjetaPanel(modifier.fillMaxWidth()) {
        when (state) {
            is ModelState.Downloading -> {
                val fraction = if (state.total > 0) (state.downloaded.toFloat() / state.total).coerceIn(0f, 1f) else 0f
                Text("Descargando el modelo ${spec.id}…", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                BarraProgreso(fraction)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${(fraction * 100).toInt()} %  ·  ${formatMegabytes(state.downloaded)} / ${formatMegabytes(state.total)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onPause) { Text("Pausar") }
                }
            }
            is ModelState.Error -> {
                Text("No se pudo descargar el modelo", style = MaterialTheme.typography.titleSmall, color = colorGasto())
                Spacer(Modifier.height(4.dp))
                Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                BotonSecundario("Reintentar", onDownload, icono = Icons.Filled.Refresh)
            }
            is ModelState.NotInstalled -> {
                Text("Falta descargar el modelo ${spec.id}", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Se descarga una sola vez (${formatMegabytes(spec.approxBytes)}) y después todo funciona sin conexión.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                BotonPrincipal(
                    if (state.partialBytes > 0) "Reanudar descarga" else "Descargar",
                    onDownload, icono = Icons.Filled.Download,
                )
            }
            ModelState.Ready -> Unit
        }
    }
}
