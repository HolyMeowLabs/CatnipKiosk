package com.holymeowlabs.catnipkiosk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ground = Color(0xFF0F1410)
val SurfaceColor = Color(0xFF182019)
val TextColor = Color(0xFFEEF2EA)
val Muted = Color(0xFFA7B2A2)
val Accent = Color(0xFF9BD873)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Ground,
    background = Ground,
    onBackground = TextColor,
    surface = SurfaceColor,
    onSurface = TextColor,
    onSurfaceVariant = Muted,
)

/** The Surface sets the default text colour; plain Text outside one is black on the dark ground. */
@Composable
fun KioskTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme) {
    Surface(color = scheme.background, contentColor = scheme.onBackground, content = content)
}
