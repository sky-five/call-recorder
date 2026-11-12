package app.callrecorder.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Muted brick red, matching the launcher icon (#A8473F).
private val Light = lightColorScheme(
    primary = Color(0xFF9A3F38), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5), onPrimaryContainer = Color(0xFF3E0605),
    secondary = Color(0xFF775652), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD5), onSecondaryContainer = Color(0xFF2C1512),
    tertiary = Color(0xFF715B2E), tertiaryContainer = Color(0xFFFDDFA6),
    background = Color(0xFFFFF8F6), onBackground = Color(0xFF231918),
    surface = Color(0xFFFFF8F6), onSurface = Color(0xFF231918),
    surfaceVariant = Color(0xFFF5DDDA), onSurfaceVariant = Color(0xFF534341),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF0EE),
    surfaceContainer = Color(0xFFFCEAE7), surfaceContainerHigh = Color(0xFFF6E4E1),
    surfaceContainerHighest = Color(0xFFF1DEDB),
    outline = Color(0xFF857370), outlineVariant = Color(0xFFD8C2BE),
    error = Color(0xFFBA1A1A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB4AA), onPrimary = Color(0xFF5F1512),
    primaryContainer = Color(0xFF7D2A24), onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFE7BDB7), onSecondary = Color(0xFF442926),
    secondaryContainer = Color(0xFF5D3F3B), onSecondaryContainer = Color(0xFFFFDAD5),
    tertiary = Color(0xFFE0C38C), tertiaryContainer = Color(0xFF574419),
    background = Color(0xFF1A1110), onBackground = Color(0xFFF1DEDB),
    surface = Color(0xFF1A1110), onSurface = Color(0xFFF1DEDB),
    surfaceVariant = Color(0xFF534341), onSurfaceVariant = Color(0xFFD8C2BE),
    surfaceContainerLowest = Color(0xFF140C0B), surfaceContainerLow = Color(0xFF231918),
    surfaceContainer = Color(0xFF271D1C), surfaceContainerHigh = Color(0xFF322826),
    surfaceContainerHighest = Color(0xFF3D3230),
    outline = Color(0xFFA08C89), outlineVariant = Color(0xFF534341),
    error = Color(0xFFFFB4AB),
)

private val Type = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = when (app.callrecorder.data.AppSettings.theme.value) {
        app.callrecorder.data.AppSettings.Theme.LIGHT -> false
        app.callrecorder.data.AppSettings.Theme.DARK -> true
        app.callrecorder.data.AppSettings.Theme.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, content = content)
}

val Mono = TextStyle(fontFeatureSettings = "tnum")
