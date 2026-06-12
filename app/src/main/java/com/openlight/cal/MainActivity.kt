package com.openlight.cal

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openlight.cal.data.preferences.AppPreferences
import com.openlight.cal.ui.navigation.HearthboardNavHost
import com.openlight.cal.ui.theme.HearthboardTheme
import com.openlight.cal.ui.theme.LocalWallMode
import com.openlight.cal.ui.theme.WallModeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val app        = application as HearthboardApp
            val prefs      = app.preferences
            val encryptor  = app.encryptor

            val darkModePref by prefs.darkMode.collectAsStateWithLifecycle(initialValue = 0)
            val themeSeedHex by prefs.themeSeedColor.collectAsStateWithLifecycle(initialValue = "")
            val kioskMode   by prefs.kioskMode.collectAsStateWithLifecycle(initialValue = false)
            val storedPin   by prefs.parentalPin.collectAsStateWithLifecycle(initialValue = "")
            val wallMode    by prefs.wallMode.collectAsStateWithLifecycle(initialValue = false)
            val wallKeepOn  by prefs.wallKeepScreenOn.collectAsStateWithLifecycle(initialValue = true)
            val wallIdleSecs by prefs.wallIdleTimeoutSeconds.collectAsStateWithLifecycle(initialValue = 0)
            val systemDark  = isSystemInDarkTheme()

            val isDark = when (darkModePref) {
                1    -> false   // Light
                2    -> true    // Dark
                else -> systemDark  // System
            }

            val seedColor = remember(themeSeedHex) {
                if (themeSeedHex.isBlank()) null
                else runCatching { Color(android.graphics.Color.parseColor(themeSeedHex)) }.getOrNull()
            }

            // ── Wall Mode — apply landscape lock + wake lock + immersive fullscreen ──────
            DisposableEffect(wallMode, wallKeepOn) {
                if (wallMode) {
                    requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    if (wallKeepOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    // Fullscreen immersive mode - hide navigation bar
                    window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN)
                } else {
                    requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    // Restore system UI
                    window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
                }
            }

            val wallScale = if (wallMode) 1.35f else 1.0f
            val wallState = remember(wallMode, wallKeepOn, wallIdleSecs, wallScale) {
                WallModeState(
                    active           = wallMode,
                    keepScreenOn     = wallKeepOn,
                    scale            = wallScale,
                    idleTimeoutSeconds = wallIdleSecs
                )
            }

            // ── Kiosk mode REMOVED — only Wall Mode remains ──────
            // (kioskMode, parentalPin, unlocked, verifyPin, KioskPinOverlay removed)

            // ── Wall Mode: photo frame idle tracker + escape overlay ──
            var showWallExitDialog by remember { mutableStateOf(false) }
            var showPhotoFrame by remember { mutableStateOf(false) }
            val wallModeScope = rememberCoroutineScope()

            // Idle timer for photo frame
            if (wallMode && wallIdleSecs > 0) {
                val idleJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                val resetIdle = {
                    idleJob.value?.cancel()
                    idleJob.value = wallModeScope.launch {
                        delay(wallIdleSecs * 1000L)
                        if (wallMode && !showWallExitDialog && !showPhotoFrame) {
                            showPhotoFrame = true
                        }
                    }
                }
                // Reset on any pointer input at root
                LaunchedEffect(resetIdle) { resetIdle() }
            }

            HearthboardTheme(darkTheme = isDark, seedColor = seedColor) {
                CompositionLocalProvider(LocalWallMode provides wallState) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            HearthboardNavHost(app = app)

                            // ── Wall Mode escape overlay: long-press triggers dialog ──
                            if (wallMode && !showPhotoFrame) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onLongPress = {
                                                    wallModeScope.launch {
                                                        delay(2000)
                                                        showWallExitDialog = true
                                                    }
                                                },
                                                onTap = { showWallExitDialog = false },
                                                onDoubleTap = { showWallExitDialog = false }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (showWallExitDialog) {
                                        WallModeExitDialog(
                                            onConfirm = {
                                                wallModeScope.launch {
                                                    prefs.set(AppPreferences.KEY_WALL_MODE, false)
                                                }
                                                showWallExitDialog = false
                                            },
                                            onDismiss = { showWallExitDialog = false }
                                        )
                                    }
                                }
                            }

                            // ── Photo frame screensaver ────────────────
                            // TODO: implement photo frame screensaver when images available
                            // if (showPhotoFrame) {
                            //     PhotoFrameScreensaver(
                            //         images = emptyList(), // TODO: load from Photos screen / gallery
                            //         onExit = { showPhotoFrame = false }
                            //     )
                            // }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WallModeExitDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exit Wall Mode?") },
        text = {
            Text(
                "Long-press detected. Do you want to exit wall mode and return to normal operation?",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Exit Wall Mode")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
