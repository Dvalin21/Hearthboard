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

            val darkModePref  by prefs.darkMode.collectAsStateWithLifecycle(initialValue = 0)
            val themeSeedHex  by prefs.themeSeedColor.collectAsStateWithLifecycle(initialValue = "")
            val storedPin     by prefs.parentalPin.collectAsStateWithLifecycle(initialValue = "")
            val wallMode      by prefs.wallMode.collectAsStateWithLifecycle(initialValue = false)
            val wallKeepOn    by prefs.wallKeepScreenOn.collectAsStateWithLifecycle(initialValue = true)
            val wallIdleSecs  by prefs.wallIdleTimeoutSeconds.collectAsStateWithLifecycle(initialValue = 0)
            val systemDark    = isSystemInDarkTheme()

            val isDark = when (darkModePref) {
                1    -> false
                2    -> true
                else -> systemDark
            }

            val seedColor = remember(themeSeedHex) {
                if (themeSeedHex.isBlank()) null
                else runCatching { Color(android.graphics.Color.parseColor(themeSeedHex)) }.getOrNull()
            }

            // ── Wall Mode — apply landscape lock + wake lock + immersive fullscreen ──────
            DisposableEffect(wallMode, wallKeepOn) {
                if (wallMode) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    if (wallKeepOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN)
                } else {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                    active            = wallMode,
                    keepScreenOn      = wallKeepOn,
                    scale             = wallScale,
                    idleTimeoutSeconds = wallIdleSecs
                )
            }

            // ── Wall Mode PIN unlock state ──────────────────────────────
            // Wall mode can be entered without PIN, but requires PIN to exit (if PIN is set)
            var wallUnlocked by remember { mutableStateOf(false) }

            // Idle timer for photo frame
            val wallModeScope = rememberCoroutineScope()
            if (wallMode && wallIdleSecs > 0) {
                val idleJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                val resetIdle = {
                    idleJob.value?.cancel()
                    idleJob.value = wallModeScope.launch {
                        delay(wallIdleSecs * 1000L)
                        if (wallMode) { /* photo frame handled in composable */ }
                    }
                }
                LaunchedEffect(resetIdle) { resetIdle() }
            }

            // ── Wall Mode exit & PIN dialog state ───────────────────────
            var showWallExitDialog by remember { mutableStateOf(false) }
            var showPinDialog by remember { mutableStateOf(false) }
            var pinInput by remember { mutableStateOf("") }
            var pinError by remember { mutableStateOf(false) }

            // ── Wall Mode lock on background ────────────────────────────
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                        if (wallMode && storedPin.isNotBlank()) {
                            // Can't directly modify state here, handled below
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // Track wall mode lock state
            var wallLocked by remember { mutableStateOf(false) }

            // Lock when app backgrounded
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                        if (wallMode && storedPin.isNotBlank()) {
                            wallLocked = true
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // Unlock on resume if no PIN
            LaunchedEffect(wallMode, storedPin) {
                if (wallMode && storedPin.isBlank()) {
                    wallLocked = false
                }
            }

            // ── Back handler for wall mode ──────────────────────────────
            // If wall mode is active and locked, intercept back press
            BackHandler(enabled = wallMode && wallLocked) {
                showWallExitDialog = true
            }

            HearthboardTheme(darkTheme = isDark, seedColor = seedColor) {
                CompositionLocalProvider(LocalWallMode provides wallState) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            HearthboardNavHost(app = app)

                            // ── Wall Mode exit dialog: long-press or back press ────────
                            if (wallMode) {
                                // Long-press escape overlay
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onLongPress = { showWallExitDialog = true },
                                                onTap = { },
                                                onDoubleTap = { }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (showWallExitDialog) {
                                        WallModeExitDialog(
                                            onConfirm = {
                                                if (storedPin.isBlank()) {
                                                    wallModeScope.launch { prefs.set(AppPreferences.KEY_WALL_MODE, false) }
                                                    wallLocked = false
                                                } else {
                                                    showPinDialog = true
                                                    pinError = false
                                                    pinInput = ""
                                                }
                                            },
                                            onDismiss = { showWallExitDialog = false }
                                        )
                                    }
                                }

                                // PIN dialog to exit wall mode
                                if (showPinDialog) {
                                    PinDialog(
                                        pinInput = pinInput,
                                        onPinChange = { pinInput = it },
                                        onConfirm = {
                                            if (encryptor.verifyPin(storedPin, pinInput)) {
                                                wallModeScope.launch { prefs.set(AppPreferences.KEY_WALL_MODE, false) }
                                                wallLocked = false
                                                pinInput = ""
                                                showPinDialog = false
                                            } else {
                                                pinError = true
                                                pinInput = ""
                                            }
                                        },
                                        onDismiss = {
                                            pinInput = ""
                                            showPinDialog = false
                                        },
                                        pinError = pinError
                                    )
                                }
                            }
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
                "Wall mode is locked. Do you want to exit wall mode and return to normal operation?",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Exit Wall Mode") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PinDialog(
    pinInput: String,
    onPinChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    pinError: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter PIN to Exit Wall Mode") },
        text = {
            Column {
                Text("Enter your 4-digit PIN to unlock wall mode.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value         = pinInput,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) onPinChange(it) },
                    label         = { Text("4-digit PIN") },
                    isError       = pinError,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true
                )
                if (pinError) Text("Wrong PIN", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (pinInput.length == 4) onConfirm()
                else onPinChange("") // trigger error
            }) { Text("Unlock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}