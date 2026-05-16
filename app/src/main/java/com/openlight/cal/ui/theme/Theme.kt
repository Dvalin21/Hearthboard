package com.openlight.cal.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ─────────────────────────────────────────────────────────────
// Colors
// ─────────────────────────────────────────────────────────────
val OpenLightBlue   = Color(0xFF1565C0)
val OpenLightGreen  = Color(0xFF2E7D32)
val OpenLightOrange = Color(0xFFE65100)
val OpenLightPurple = Color(0xFF6A1B9A)
val OpenLightTeal   = Color(0xFF00695C)

// Person palette - 12 distinct accessible colors
val PersonColors = listOf(
    "#F44336", "#E91E63", "#9C27B0", "#673AB7",
    "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
    "#009688", "#4CAF50", "#8BC34A", "#FF9800",
    "#FF5722", "#795548", "#607D8B", "#9E9E9E"
)

private val LightColorScheme = lightColorScheme(
    primary          = Color(0xFF1565C0),
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    secondary        = Color(0xFF2E7D32),
    onSecondary      = Color.White,
    tertiary         = Color(0xFFE65100),
    background       = Color(0xFFF8F9FA),
    surface          = Color.White,
    surfaceVariant   = Color(0xFFEEF2F8),
    outline          = Color(0xFFBCC3CE),
    error            = Color(0xFFB00020)
)

private val DarkColorScheme = darkColorScheme(
    primary          = Color(0xFF8BB4FF),
    onPrimary        = Color(0xFF002475),
    primaryContainer = Color(0xFF003C9A),
    secondary        = Color(0xFF81C784),
    onSecondary      = Color(0xFF003A00),
    tertiary         = Color(0xFFFFAB76),
    background       = Color(0xFF121212),
    surface          = Color(0xFF1E1E1E),
    surfaceVariant   = Color(0xFF2A2A2A),
    outline          = Color(0xFF555555),
    error            = Color(0xFFCF6679)
)

// ─────────────────────────────────────────────────────────────
// Typography — clean, readable, no emojis
// ─────────────────────────────────────────────────────────────
val OpenLightTypography = Typography(
    displayLarge  = TextStyle(fontWeight = FontWeight.W300, fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontWeight = FontWeight.W300, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall  = TextStyle(fontWeight = FontWeight.W400, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.W600, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium= TextStyle(fontWeight = FontWeight.W600, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.W600, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge    = TextStyle(fontWeight = FontWeight.W500, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium   = TextStyle(fontWeight = FontWeight.W500, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall    = TextStyle(fontWeight = FontWeight.W500, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge     = TextStyle(fontWeight = FontWeight.W400, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium    = TextStyle(fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall     = TextStyle(fontWeight = FontWeight.W400, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge    = TextStyle(fontWeight = FontWeight.W500, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium   = TextStyle(fontWeight = FontWeight.W500, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall    = TextStyle(fontWeight = FontWeight.W500, fontSize = 11.sp, lineHeight = 16.sp)
)

// ─────────────────────────────────────────────────────────────
// Theme Composable
// ─────────────────────────────────────────────────────────────
@Composable
fun OpenLightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,   // disable dynamic color — we use seeded M3
    seedColor: Color? = null,        // user-chosen theme seed from settings
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        seedColor != null -> {
            // Apply user-chosen seed as primary accent in the base scheme
            val base = if (darkTheme) DarkColorScheme else LightColorScheme
            base.copy(primary = seedColor)
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // Dynamic color disabled intentionally — full user control via settings
            if (darkTheme) DarkColorScheme else LightColorScheme
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
        typography  = OpenLightTypography,
        content     = content
    )
}
