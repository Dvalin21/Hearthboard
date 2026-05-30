package com.openlight.cal.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ─────────────────────────────────────────────────────────────
// Person palette — 16 muted, accessible colors
// ─────────────────────────────────────────────────────────────
// Replaces the previous saturated Material accents (which clashed with
// the new slate/warm-white surfaces). Each color is muted ~40%, sits
// comfortably on cream/warm-white backgrounds, and stays distinct at
// the small dot/avatar sizes used throughout the app. Ordered for
// maximum perceptual distance between adjacent picks so the first 4–6
// people in a household don't end up with neighboring hues.
val PersonColors = listOf(
    "#7E967B",   // sage
    "#B07D5C",   // terracotta
    "#4A6178",   // slate blue
    "#A36E94",   // dusty plum
    "#C9A14A",   // warm ochre
    "#638F9C",   // dusty teal
    "#9D5848",   // brick
    "#6E8E5A",   // olive
    "#86678A",   // muted lavender
    "#D08868",   // soft coral
    "#5A7588",   // steel
    "#8E6A4B",   // walnut
    "#7E8B47",   // moss
    "#A99060",   // tan
    "#735F8B",   // grape
    "#8A8278"    // warm gray
)

// ─────────────────────────────────────────────────────────────
// Slate + warm white palette
// ─────────────────────────────────────────────────────────────
// Surface ramp (warm whites → grays):
//   #FAFAF7  background          warm white, slightly off-white
//   #F0EFEA  surface             card / panel
//   #DCDAD2  surface-variant     dividers, person-filter chips
// Primary (slate blue):  #4A6178  /  on-primary #FAFAF7
// Secondary (steel):     #B9C7D2  /  on-secondary #1F2A36
// Tertiary (terracotta): #C28860  /  on-tertiary #FAFAF7
// Text: #1F2A36 (primary) / #6A7480 (muted)
private val LightColorScheme = lightColorScheme(
    primary                = Color(0xFF4A6178),
    onPrimary              = Color(0xFFFAFAF7),
    primaryContainer       = Color(0xFFD5DFE9),
    onPrimaryContainer     = Color(0xFF101B26),
    secondary              = Color(0xFF6E7E8C),
    onSecondary            = Color(0xFFFAFAF7),
    secondaryContainer     = Color(0xFFB9C7D2),
    onSecondaryContainer   = Color(0xFF1F2A36),
    tertiary               = Color(0xFFB07D5C),
    onTertiary             = Color(0xFFFAFAF7),
    tertiaryContainer      = Color(0xFFEAD6C5),
    onTertiaryContainer    = Color(0xFF3A1F0E),
    error                  = Color(0xFFA94442),
    onError                = Color(0xFFFAFAF7),
    errorContainer         = Color(0xFFF2D5D4),
    onErrorContainer       = Color(0xFF3A0E0D),
    background             = Color(0xFFFAFAF7),
    onBackground           = Color(0xFF1F2A36),
    surface                = Color(0xFFF0EFEA),
    onSurface              = Color(0xFF1F2A36),
    surfaceVariant         = Color(0xFFDCDAD2),
    onSurfaceVariant       = Color(0xFF6A7480),
    outline                = Color(0xFFB6B5AE),
    outlineVariant         = Color(0xFFDCDAD2),
    inverseSurface         = Color(0xFF2D3845),
    inverseOnSurface       = Color(0xFFF0EFEA),
    inversePrimary         = Color(0xFFB9C7D2),
    surfaceTint            = Color(0xFF4A6178),
    scrim                  = Color.Black
)

private val DarkColorScheme = darkColorScheme(
    primary                = Color(0xFFB9C7D2),
    onPrimary              = Color(0xFF1F2A36),
    primaryContainer       = Color(0xFF35475A),
    onPrimaryContainer     = Color(0xFFD5DFE9),
    secondary              = Color(0xFF9CA9B5),
    onSecondary            = Color(0xFF1F2A36),
    secondaryContainer     = Color(0xFF3F4A55),
    onSecondaryContainer   = Color(0xFFD5DFE9),
    tertiary               = Color(0xFFE2B594),
    onTertiary             = Color(0xFF3A1F0E),
    tertiaryContainer      = Color(0xFF7A4F30),
    onTertiaryContainer    = Color(0xFFEAD6C5),
    error                  = Color(0xFFE5A8A6),
    onError                = Color(0xFF3A0E0D),
    errorContainer         = Color(0xFF6B2A28),
    onErrorContainer       = Color(0xFFF2D5D4),
    background             = Color(0xFF14181E),
    onBackground           = Color(0xFFE6E6E2),
    surface                = Color(0xFF1C2128),
    onSurface              = Color(0xFFE6E6E2),
    surfaceVariant         = Color(0xFF2A3038),
    onSurfaceVariant       = Color(0xFFB6BCC3),
    outline                = Color(0xFF4D535B),
    outlineVariant         = Color(0xFF2A3038),
    inverseSurface         = Color(0xFFE6E6E2),
    inverseOnSurface       = Color(0xFF1F2A36),
    inversePrimary         = Color(0xFF4A6178),
    surfaceTint            = Color(0xFFB9C7D2),
    scrim                  = Color.Black
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
        // Custom seed: derive a tonal palette from the user's color,
        // but keep our designed neutrals so the rest of the UI doesn't
        // get clobbered by a single seed. Only `primary` is overridden.
        seedColor != null -> {
            val base = if (darkTheme) DarkColorScheme else LightColorScheme
            base.copy(
                primary       = seedColor,
                surfaceTint   = seedColor,
                inversePrimary= seedColor
            )
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    // Edge-to-edge is enabled in MainActivity; the status bar should be
    // transparent and let the app draw underneath. We only set the bar
    // icon color (light/dark) here, not its background — setting the
    // background fights edge-to-edge and is deprecated in API 35+.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars     = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = HearthboardTypography,
        shapes      = HearthboardShapes,
        content     = content
    )
}
