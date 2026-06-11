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

// ═══════════════════════════════════════════════════════════════
// Person palette — 16 muted, accessible colors (unchanged)
// Ordered for maximum perceptual distance between adjacent picks
// ═══════════════════════════════════════════════════════════════
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

// ═══════════════════════════════════════════════════════════════
// SKYLIGHT PALETTE — Warm, paper-like, readable at 3–6 ft
// ═══════════════════════════════════════════════════════════════
// Light (daymode) — cream paper, warm slate ink
// Surface ramp:
//   #FDFBF7  background          warm white (paper)
//   #F5F2EB  surface             card / panel
//   #E8E4DB  surface-variant     dividers, chips, rail background
//   #DDD9D0  surface-container   elevated cards
// Primary (slate):     #3D556B  / on-primary #FDFBF7
// Secondary (terracotta): #A86B4F / on-secondary #FDFBF7
// Tertiary (sage):     #6B8A6B  / on-tertiary #FDFBF7
// Outline:             #C4C0B8  / outline-variant #E8E4DB
// Text primary:        #1C2228  / muted: #7A7770
private val LightColorScheme = lightColorScheme(
    primary                = Color(0xFF3D556B),
    onPrimary              = Color(0xFFFDFBF7),
    primaryContainer       = Color(0xFFD0DCE8),
    onPrimaryContainer     = Color(0xFF0F1A24),
    secondary              = Color(0xFFA86B4F),
    onSecondary            = Color(0xFFFDFBF7),
    secondaryContainer     = Color(0xFFEADCCF),
    onSecondaryContainer   = Color(0xFF3A1F0E),
    tertiary               = Color(0xFF6B8A6B),
    onTertiary             = Color(0xFFFDFBF7),
    tertiaryContainer      = Color(0xFFD4E5D4),
    onTertiaryContainer    = Color(0xFF1A301A),
    error                  = Color(0xFFB3423E),
    onError                = Color(0xFFFDFBF7),
    errorContainer         = Color(0xFFF2D5D4),
    onErrorContainer       = Color(0xFF3A0E0D),
    background             = Color(0xFFFDFBF7),
    onBackground           = Color(0xFF1C2228),
    surface                = Color(0xFFF5F2EB),
    onSurface              = Color(0xFF1C2228),
    surfaceVariant         = Color(0xFFE8E4DB),
    onSurfaceVariant       = Color(0xFF7A7770),
    outline                = Color(0xFFC4C0B8),
    outlineVariant         = Color(0xFFE8E4DB),
    inverseSurface         = Color(0xFF2A2F36),
    inverseOnSurface       = Color(0xFFF5F2EB),
    inversePrimary         = Color(0xFFB9C7D2),
    surfaceTint            = Color(0xFF3D556B),
    scrim                  = Color.Black,
    surfaceBright          = Color(0xFFFDFBF7),
    surfaceDim             = Color(0xFFE0DDD5),
    surfaceContainerLow    = Color(0xFFEEEBE3),
    surfaceContainer       = Color(0xFFE8E4DB),
    surfaceContainerHigh   = Color(0xFFE0DDD5),
    surfaceContainerHighest= Color(0xFFD8D5CC)
)

private val DarkColorScheme = darkColorScheme(
    primary                = Color(0xFFB9C7D2),
    onPrimary              = Color(0xFF1C2228),
    primaryContainer       = Color(0xFF3D556B),
    onPrimaryContainer     = Color(0xFFD0DCE8),
    secondary              = Color(0xFFE2B594),
    onSecondary            = Color(0xFF3A1F0E),
    secondaryContainer     = Color(0xFF7A4F30),
    onSecondaryContainer   = Color(0xFFEADCCF),
    tertiary               = Color(0xFFC4E0C4),
    onTertiary             = Color(0xFF1A301A),
    tertiaryContainer      = Color(0xFF4A6E4A),
    onTertiaryContainer    = Color(0xFFD4E5D4),
    error                  = Color(0xFFF2B8B5),
    onError                = Color(0xFF3A0E0D),
    errorContainer         = Color(0xFF7A302D),
    onErrorContainer       = Color(0xFFF2D5D4),
    background             = Color(0xFF1A1E22),
    onBackground           = Color(0xFFE8E4DB),
    surface                = Color(0xFF22272D),
    onSurface              = Color(0xFFE8E4DB),
    surfaceVariant         = Color(0xFF3A3F46),
    onSurfaceVariant       = Color(0xFFB8B4AC),
    outline                = Color(0xFF6A6D74),
    outlineVariant         = Color(0xFF3A3F46),
    inverseSurface         = Color(0xFFE8E4DB),
    inverseOnSurface       = Color(0xFF1C2228),
    inversePrimary         = Color(0xFF3D556B),
    surfaceTint            = Color(0xFFB9C7D2),
    scrim                  = Color.Black,
    surfaceBright          = Color(0xFF2D3239),
    surfaceDim             = Color(0xFF1A1E22),
    surfaceContainerLow    = Color(0xFF242930),
    surfaceContainer       = Color(0xFF2A2F36),
    surfaceContainerHigh   = Color(0xFF32373F),
    surfaceContainerHighest= Color(0xFF3A3F46)
)

// ════════════════════════════════════════════════════════════════
// Shapes — softer, friendlier (Skylight uses generous rounding)
// ═══════════════════════════════════════════════════════════════
val HearthboardShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),   // chips, avatars
    small      = RoundedCornerShape(12.dp),  // buttons, cards
    medium     = RoundedCornerShape(16.dp),  // dialogs, sheets
    large      = RoundedCornerShape(20.dp),  // large sheets
    extraLarge = RoundedCornerShape(28.dp)   // full-screen modals
)

// ═══════════════════════════════════════════════════════════════
// Typography — larger, airier, readable at arm's length + beyond
// Letter-spacing opened up for distance viewing
// Base scale bumped +2sp across the board vs M3 default
// ═══════════════════════════════════════════════════════════════
val HearthboardTypography = Typography(
    displayLarge  = TextStyle(fontWeight = FontWeight.W300, fontSize = 64.sp, lineHeight = 72.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.W300, fontSize = 52.sp, lineHeight = 60.sp, letterSpacing = 0.sp),
    displaySmall  = TextStyle(fontWeight = FontWeight.W400, fontSize = 44.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.W600, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineMedium= TextStyle(fontWeight = FontWeight.W600, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.W600, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    titleLarge    = TextStyle(fontWeight = FontWeight.W500, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    titleMedium   = TextStyle(fontWeight = FontWeight.W500, fontSize = 18.sp, lineHeight = 26.sp, letterSpacing = 0.15.sp),
    titleSmall    = TextStyle(fontWeight = FontWeight.W500, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    bodyLarge     = TextStyle(fontWeight = FontWeight.W400, fontSize = 18.sp, lineHeight = 26.sp, letterSpacing = 0.3.sp),
    bodyMedium    = TextStyle(fontWeight = FontWeight.W400, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.25.sp),
    bodySmall     = TextStyle(fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    labelLarge    = TextStyle(fontWeight = FontWeight.W500, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    labelMedium   = TextStyle(fontWeight = FontWeight.W500, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.3.sp),
    labelSmall    = TextStyle(fontWeight = FontWeight.W500, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp)
)

// ═══════════════════════════════════════════════════════════════
// Theme composable
// ═══════════════════════════════════════════════════════════════
@Composable
fun HearthboardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    seedColor: Color? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
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