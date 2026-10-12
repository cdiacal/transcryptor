package com.listeningnotes.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listeningnotes.app.ui.theme.degradadoHero
import com.listeningnotes.app.ui.theme.fondoIcono

private val Blanco = Color.White

/** Botón principal: fondo con el degradado hero, texto blanco, sin ripple. */
@Composable
fun BotonPrincipal(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icono: ImageVector? = null,
) {
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(12.dp))
            .background(degradadoHero())
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icono != null) {
            Icon(icono, null, Modifier.size(16.dp), tint = Blanco)
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        Text(texto, color = Blanco, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Botón secundario: superficie con borde fino. */
@Composable
fun BotonSecundario(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icono: ImageVector? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.alpha(if (enabled) 1f else 0.45f),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icono != null) {
                Icon(icono, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            }
            Text(
                texto, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Tarjeta plana: radio 16, borde 1 dp, sin sombra, padding 16 dp. */
@Composable
fun TarjetaPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val inner: @Composable () -> Unit = { Column(Modifier.padding(contentPadding), content = content) }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = containerColor, border = BorderStroke(1.dp, borderColor), content = inner)
    } else {
        Surface(modifier = modifier, shape = shape, color = containerColor, border = BorderStroke(1.dp, borderColor), content = inner)
    }
}

/** Pestañas en píldora con scroll horizontal. */
@Composable
fun FilaPestanas(
    opciones: List<String>,
    seleccion: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        opciones.forEachIndexed { i, texto ->
            val activa = i == seleccion
            Box(
                Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (activa) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(role = Role.Tab, onClick = { onSelect(i) })
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    texto,
                    fontSize = 12.sp,
                    fontWeight = if (activa) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (activa) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Cuadrado redondeado (radio 30 %) con la inicial en Bold al 38 % del tamaño. */
@Composable
fun IconoInicial(texto: String, id: Long, modifier: Modifier = Modifier, tamano: androidx.compose.ui.unit.Dp = 36.dp) {
    Box(
        modifier.size(tamano).clip(RoundedCornerShape(tamano * 0.3f)).background(fondoIcono(id)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texto.trim().take(1).uppercase().ifEmpty { "?" },
            fontSize = (tamano.value * 0.38f).sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Cabecera principal con degradado y esquinas inferiores de 24 dp. Contiene el dato protagonista. */
@Composable
fun CabeceraHero(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(degradadoHero())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 26.dp),
        content = content,
    )
}

/** Cabecera de pantalla secundaria: barra con degradado, botón volver blanco, título y subtítulo. */
@Composable
fun CabeceraSimple(
    titulo: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    acciones: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(degradadoHero())
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Blanco) }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(titulo, color = Blanco, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitulo != null) {
                Text(subtitulo, color = Blanco.copy(alpha = 0.82f), fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        acciones()
    }
}

/** Botón de icono blanco para usar dentro de las cabeceras con degradado. */
@Composable
fun IconoCabecera(icono: ImageVector, descripcion: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, colors = IconButtonDefaults.iconButtonColors(contentColor = Blanco)) {
        Icon(icono, descripcion)
    }
}

/** Cifra protagonista del hero. */
@Composable
fun CifraHero(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto, modifier, color = Blanco, fontSize = 32.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-1).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** Etiqueta pequeña sobre el degradado (11.5 sp, 82 % de opacidad). */
@Composable
fun EtiquetaHero(texto: String, modifier: Modifier = Modifier) {
    Text(texto, modifier, color = Blanco.copy(alpha = 0.82f), fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Mini-dato del hero: etiqueta 10.5 sp y valor 14 sp Bold, una sola línea. */
@Composable
fun MiniDatoHero(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(etiqueta, color = Blanco.copy(alpha = 0.8f), fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(valor, color = Blanco, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Barra de progreso con extremos redondeados; [progress] null = indeterminada. */
@Composable
fun BarraProgreso(progress: Float?, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    if (progress != null) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier.fillMaxWidth(),
            trackColor = track,
            strokeCap = StrokeCap.Round,
        )
    } else {
        LinearProgressIndicator(modifier = modifier.fillMaxWidth(), trackColor = track, strokeCap = StrokeCap.Round)
    }
}
