package com.lemarc.fake_gps.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(primary = Blue800, secondary = Green800)
private val DarkColors = darkColorScheme(primary = Blue200, secondary = Green200)

/** Thème Material 3 de l'appli (clair / sombre selon le système). */
@Composable
fun FakeGpsTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
