package com.holymeowlabs.catnipkiosk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ground = Color(0xFF0F1410)
val Surface = Color(0xFF182019)
val TextColor = Color(0xFFEEF2EA)
val Muted = Color(0xFFA7B2A2)
val Accent = Color(0xFF9BD873)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Ground,
    background = Ground,
    onBackground = TextColor,
    surface = Surface,
    onSurface = TextColor,
    onSurfaceVariant = Muted,
)

@Composable
fun KioskTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)
