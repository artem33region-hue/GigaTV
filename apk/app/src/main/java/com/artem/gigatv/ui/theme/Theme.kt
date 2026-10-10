package com.artem.gigatv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val AccentBlue = Color(0xFF4A7DFF)
val SurfaceDark = Color(0xFF14181F)
val BackgroundDark = Color(0xFF0B0D10)
val CardDark = Color(0xFF1B2029)
val TextMuted = Color(0xFF9AA3B2)

@Composable
fun KinoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AccentBlue,
            onPrimary = Color.White,
            secondary = Color(0xFF7A9BFF),
            onSecondary = Color.White,
            background = BackgroundDark,
            onBackground = Color(0xFFE6E9EF),
            surface = SurfaceDark,
            onSurface = Color(0xFFE6E9EF),
            surfaceVariant = CardDark,
            onSurfaceVariant = TextMuted
        ),
        content = content
    )
}
