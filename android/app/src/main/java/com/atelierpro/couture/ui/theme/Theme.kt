package com.atelierpro.couture.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val IndigoAtelier = Color(0xFF1E3A5F)
val CreamCanvas = Color(0xFFFAF9F6)
val StoneInk = Color(0xFF1C1917)
val StoneMuted = Color(0xFF78716C)
val StoneBorder = Color(0xFFE7E5E4)
val EmeraldSuccess = Color(0xFF15803D)
val AmberAlert = Color(0xFFB45309)
val CrimsonAlert = Color(0xFFB91C1C)

private val AtelierColorScheme = lightColorScheme(
    primary = IndigoAtelier,
    onPrimary = Color.White,
    background = CreamCanvas,
    onBackground = StoneInk,
    surface = Color.White,
    onSurface = StoneInk,
    surfaceVariant = Color(0xFFF5F5F4),
    outline = StoneBorder,
    error = CrimsonAlert
)

@Composable
fun AtelierProTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AtelierColorScheme,
        content = content
    )
}
