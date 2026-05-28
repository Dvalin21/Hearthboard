package com.openlight.cal.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ─────────────────────────────────────────────────────────────
// Person palette — 16 distinct, accessible colors
// ─────────────────────────────────────────────────────────────
val PersonColors = listOf(
    "#F44336", "#E91E63", "#9C27B0", "#673AB7",
    "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
    "#009688", "#4CAF50", "#8BC34A", "#FF9800",
    "#FF5722", "#795548", "#607D8B", "#9E9E9E"
)

// ─────────────────────────────────────────────────────────────
// Full M3 color schemes — all 25+ color roles populated
// ─────────────────────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary            = Color(0xFF1565C0),
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary          = Color(0xFF2E7D32),
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFFA5D6A7),
    onSecondaryContainer = Color(0xFF002106),
    tertiary           = Color(0xFFE65100),
    onTertiary         = Color.White,
    tertiaryContainer  = Color(0xFFFFD5BD),
    onTertiaryContainer= Color(0xFF2E1500),
    error              = Color(0xFFB00020),
    onError            = Color.White,
    errorContainer     = Color(0xFFFFDAD6),
    onErrorContainer   = Color(0xFF410002),
    background         = Color(0xFFF8F9FA),
    onBackground       = Color(0xFF1A1C1E),
    surface            = Color.White,
    onSurface          = Color(0xFF1A1C1E),
    surfaceVariant     = Color(0xFFEEF2F8),
    onSurfaceVariant   = Color(0xFF43474E),
    outline            = Color(0xFFBCC3CE),
    outlineVariant     = Color(0xFFDDE3EC),
    inverseSurface     = Color(0xFF2F3033),
    inverseOnSurface   = Color(0xFFF2F0F4),
    inversePrimary     = Color(0xFFAAC7FF),
    surfaceTint        = Color(0xFF1565C0),
    scrim              = Color.Black
)

private val DarkColorScheme = darkColorScheme(
    primary            = Color(0xFF8BB4FF),
    onPrimary          = Color(0xFF003A75),
    primaryContainer   = Color(0xFF004C9A),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary          = Color(0xFF81C784),
    onSecondary        = Color(0xFF003A00),
    secondaryContainer = Color(0xFF005300),
    onSecondaryContainer = Color(0xFFA5D6A7),
    tertiary           = Color(0xFFFFB685),
    onTertiary         = Color(0xFF4A2300),
    tertiaryContainer  = Color(0xFF663200),
    onTertiaryContainer= Color(0xFFFFD5BD),
    error              = Color(0xFFFFB4AB),
    onError            = Color(0xFF690005),
    errorContainer     = Color(0xFF93000A),
    onErrorContainer   = Color(0xFFFFDAD6),
    background         = Color(0xFF1A1C1E),
    onBackground       = Color(0xFFE2E2E6),
    surface            = Color(0xFF1E1E1E),
    onSurface          = Color(0xFFE2E2E6),
    surfaceVariant     = Color(0xFF2A2A2A),
    onSurfaceVariant   = Color(0xFFC4C6CF),
    outline            = Color(0xFF555555),
    outlineVariant     = Color(0xFF44474E),
    inverseSurface     = Color(0xFFE2E2E6),
    inverseOnSurface   = Color(0xFF2F3033),
    inversePrimary     = Color(0xFF1565C0),
    surfaceTint        = Color(0xFF8BB4FF),
    scrim              = Color.Black
)

// ─────────────────────────────────────────────────────────────
// Shapes — M3 size hierarchy
// ─────────────────────────────────────────────────────────────
val HearthboardShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

// ─────────────────────────────────────────────────────────────
// Typography — M3 type scale with letter-spacing
// ─────────────────────────────────────────────────────────────
val HearthboardTypography = Typography(
    displayLarge  = TextStyle(fontWeight = FontWeight.W300, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.W300, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
    displaySmall  = TextStyle(fontWeight = FontWeight.W400, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.W600, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineMedium= TextStyle(fontWeight = FontWeight.W600, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.W600, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    titleLarge    = TextStyle(fontWeight = FontWeight.W500, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium   = TextStyle(fontWeight = FontWeight.W500, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall    = TextStyle(fontWeight = FontWeight.W500, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge     = TextStyle(fontWeight = FontWeight.W400, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium    = TextStyle(fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall     = TextStyle(fontWeight = FontWeight.W400, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge    = TextStyle(fontWeight = FontWeight.W500, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium   = TextStyle(fontWeight = FontWeight.W500, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall    = TextStyle(fontWeight = FontWeight.W500, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)

// ─────────────────────────────────────────────────────────────
// Theme composable
// ─────────────────────────────────────────────────────────────
@Composable
fun HearthboardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    seedColor: Color? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Monet from wallpaper (Android 12+)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Custom seed color → tonal palette
        seedColor != null -> {
            if (darkTheme) darkColorScheme(primary = seedColor)
            else lightColorScheme(primary = seedColor)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = HearthboardTypography,
        shapes      = HearthboardShapes,
        content     = content
    )
}