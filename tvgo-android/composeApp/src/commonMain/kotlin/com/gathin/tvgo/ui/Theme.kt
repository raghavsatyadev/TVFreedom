package com.gathin.tvgo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(primary = Color(0xFF3F5F90), secondary = Color(0xFF555F71), tertiary = Color(0xFF6E5676))
private val Dark = darkColorScheme(primary = Color(0xFFA8C8FF), secondary = Color(0xFFBDC7DC), tertiary = Color(0xFFDABDE2))

/** Material 3 Expressive: wallpaper colours on Android 12+, springy expressive motion. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TvGoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = dynamicScheme(dark) ?: if (dark) Dark else Light
    MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive(), content = content)
}
