package io.github.raghavsatyadev.tvfreedom.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** One quiet dark look on every phone: near-black, grey cards, a soft blue for what to tap. */
private val Quiet = darkColorScheme(
    primary = Color(0xFF9DBBFF), onPrimary = Color(0xFF0B1A33),
    primaryContainer = Color(0xFF22324F), onPrimaryContainer = Color(0xFFD8E3FF),
    secondary = Color(0xFFB4BCCB), secondaryContainer = Color(0xFF2A2E35), onSecondaryContainer = Color(0xFFE1E4EA),
    tertiary = Color(0xFFC9BFD8), tertiaryContainer = Color(0xFF2E2A36), onTertiaryContainer = Color(0xFFE7DEF5),
    background = Color(0xFF0D0E10), onBackground = Color(0xFFE4E5E8),
    surface = Color(0xFF0D0E10), onSurface = Color(0xFFE4E5E8), onSurfaceVariant = Color(0xFF9A9EA6),
    surfaceContainerLowest = Color(0xFF08090A), surfaceContainerLow = Color(0xFF131416),
    surfaceContainer = Color(0xFF161719), surfaceContainerHigh = Color(0xFF1C1D20), surfaceContainerHighest = Color(0xFF242528),
    outline = Color(0xFF45484E), outlineVariant = Color(0xFF2C2E32),
)

/** Material 3 Expressive in [Quiet] dark, with springy expressive motion. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TvFreedomTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(colorScheme = Quiet, motionScheme = MotionScheme.expressive(), content = content)
}
