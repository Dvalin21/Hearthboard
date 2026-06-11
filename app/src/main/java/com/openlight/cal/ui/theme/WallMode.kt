package com.openlight.cal.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

/**
 * Skylight-style "wall mode" state, exposed through composition.
 *
 * When [active] is true, the app is running on a permanently-mounted
 * tablet acting as a family hub. Components consult this to scale up
 * touch targets, switch to a denser layout, and lean on visual cues
 * suited to ~3-6 ft viewing distance instead of arm's length.
 *
 * Keep this dumb: just flags + a density scalar. Behaviours (orientation
 * lock, keep-screen-on, immersive insets) are wired in MainActivity from
 * the underlying preferences; this object is for *render-time* decisions.
 *
 * New: idleTimeoutSeconds — after this many seconds of no touch input,
 * the app enters photo-frame screensaver mode. Reset on any touch.
 */
@Immutable
data class WallModeState(
    val active: Boolean = false,
    val keepScreenOn: Boolean = false,
    /** UI scale multiplier. 1.0f when off, 1.35f when on (Skylight-style). */
    val scale: Float = 1.0f,
    /** Seconds of inactivity before showing photo frame screensaver. 0 = disabled. */
    val idleTimeoutSeconds: Int = 0
)

/** Default (off) — wrap the app at the top to override. */
val LocalWallMode = compositionLocalOf { WallModeState() }