package dev.leeauf.nfcpocket.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B58),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9EF2D9),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4B635B),
    tertiary = Color(0xFF3F6374)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D5BD),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005141),
    onPrimaryContainer = Color(0xFF9EF2D9),
    secondary = Color(0xFFB2CCC2),
    tertiary = Color(0xFFA6CDDF)
)

@Composable
fun NfcPocketTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
