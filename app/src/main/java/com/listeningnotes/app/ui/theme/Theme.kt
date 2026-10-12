package com.listeningnotes.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.listeningnotes.app.data.repository.ThemeMode

/** Indica si la interfaz está en modo oscuro (resuelto desde la preferencia sistema/claro/oscuro). */
val LocalEsModoOscuro = compositionLocalOf { false }

// --- Identidad (azul marino) ---
private val HeroInicioClaro = Color(0xFF152C52)
private val HeroInicioOscuro = Color(0xFF11223F)
private val HeroFinClaro = Color(0xFF1E4B87)
private val HeroFinOscuro = Color(0xFF183C6B)

// --- Semánticos ---
private val IngresoClaro = Color(0xFF12734A)
private val IngresoOscuro = Color(0xFF2DD48E)
private val GastoClaro = Color(0xFFC6473A)
private val GastoOscuro = Color(0xFFF0705F)
private val AvisoClaro = Color(0xFFD9932B)
private val AvisoOscuro = Color(0xFFE5A93F)

/** Fondos de icono (se elige uno por `id % 8`). */
private val FondosIconoClaro = listOf(
    0xFFE3EAF5, 0xFFE0EFE7, 0xFFF5EADF, 0xFFEDE6F3, 0xFFF6E4E4, 0xFFE1EFF2, 0xFFF0EEE3, 0xFFE7EAF0,
).map { Color(it) }
private val FondosIconoOscuro = listOf(
    0xFF1E2A3E, 0xFF1B2F28, 0xFF322A20, 0xFF2A2438, 0xFF352424, 0xFF1C2E33, 0xFF2E2C22, 0xFF242833,
).map { Color(it) }

/** Colores para series de gráficos. */
val ColoresGrafico = listOf(
    0xFF1E4B87, 0xFF12734A, 0xFFD9932B, 0xFFC6473A, 0xFF6B4E9E, 0xFF2A8A99, 0xFF8A6A3D, 0xFF4A5C7A,
).map { Color(it) }

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E4B87),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3EAF5),
    onPrimaryContainer = Color(0xFF152C52),
    secondary = IngresoClaro,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3EAF5),
    onSecondaryContainer = Color(0xFF152C52),
    tertiary = AvisoClaro,
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF111420),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111420),
    surfaceVariant = Color(0xFFE6E9F0),
    onSurfaceVariant = Color(0xFF767C90),
    outline = Color(0xFFE6E9F0),
    outlineVariant = Color(0xFFE6E9F0),
    error = GastoClaro,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF6E4E4),
    onErrorContainer = GastoClaro,
    surfaceTint = Color(0xFF1E4B87),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5A9BF5),
    onPrimary = Color(0xFF0C0E16),
    primaryContainer = Color(0xFF1B2C46),
    onPrimaryContainer = Color(0xFFD6E4FA),
    secondary = IngresoOscuro,
    onSecondary = Color(0xFF0C0E16),
    secondaryContainer = Color(0xFF1B2C46),
    onSecondaryContainer = Color(0xFFD6E4FA),
    tertiary = AvisoOscuro,
    onTertiary = Color(0xFF0C0E16),
    background = Color(0xFF0C0E16),
    onBackground = Color(0xFFEDEEF5),
    surface = Color(0xFF161A26),
    onSurface = Color(0xFFEDEEF5),
    surfaceVariant = Color(0xFF232838),
    onSurfaceVariant = Color(0xFF8189A0),
    outline = Color(0xFF232838),
    outlineVariant = Color(0xFF232838),
    error = GastoOscuro,
    onError = Color(0xFF0C0E16),
    errorContainer = Color(0xFF352424),
    onErrorContainer = GastoOscuro,
    surfaceTint = Color(0xFF5A9BF5),
    surfaceContainerLowest = Color(0xFF161A26),
    surfaceContainerLow = Color(0xFF161A26),
    surfaceContainer = Color(0xFF161A26),
    surfaceContainerHigh = Color(0xFF161A26),
    surfaceContainerHighest = Color(0xFF161A26),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp), // diálogos y tarjetas grandes
)

// --- Colores que dependen del modo (usar siempre estas funciones, nunca los valores fijos) ---

@Composable
@ReadOnlyComposable
fun colorIngreso(): Color = if (LocalEsModoOscuro.current) IngresoOscuro else IngresoClaro

@Composable
@ReadOnlyComposable
fun colorGasto(): Color = if (LocalEsModoOscuro.current) GastoOscuro else GastoClaro

@Composable
@ReadOnlyComposable
fun colorAviso(): Color = if (LocalEsModoOscuro.current) AvisoOscuro else AvisoClaro

@Composable
@ReadOnlyComposable
fun fondoIcono(id: Long): Color {
    val lista = if (LocalEsModoOscuro.current) FondosIconoOscuro else FondosIconoClaro
    return lista[Math.floorMod(id, 8L).toInt()]
}

/** Degradado diagonal del hero y del botón principal. */
@Composable
@ReadOnlyComposable
fun degradadoHero(): Brush {
    val oscuro = LocalEsModoOscuro.current
    return Brush.linearGradient(
        listOf(
            if (oscuro) HeroInicioOscuro else HeroInicioClaro,
            if (oscuro) HeroFinOscuro else HeroFinClaro,
        ),
    )
}

@Composable
fun ListeningNotesTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val oscuro = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalEsModoOscuro provides oscuro) {
        MaterialTheme(
            colorScheme = if (oscuro) DarkColors else LightColors,
            shapes = AppShapes,
            content = content,
        )
    }
}
