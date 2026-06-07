package com.openlight.cal

import android.content.pm.ActivityInfo
import android.os.Bundle
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
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

            // ── Wall Mode — apply landscape lock + wake lock ──────
            // requestedOrientation and the keep-screen-on flag are
            // window-level concerns, not Compose; apply them imperatively
            // from a side effect tied to the live preference.
            DisposableEffect(wallMode, wallKeepOn) {
                if (wallMode) {
                    requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    if (wallKeepOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                } else {
                    requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    // Cleanup if the activity goes away — let the system
                    // dim normally again.
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            val wallState = remember(wallMode, wallKeepOn) {
                WallModeState(
                    active       = wallMode,
                    keepScreenOn = wallKeepOn,
                    scale        = if (wallMode) 1.25f else 1.0f
                )
            }

            // ── Kiosk lock state ───────────────────────────────
            var unlocked by remember { mutableStateOf(!kioskMode || storedPin.isBlank()) }

            // Re-lock when app goes to background
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        unlocked = !kioskMode || storedPin.isBlank()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // Update unlocked when kiosk/pin settings change
            LaunchedEffect(kioskMode, storedPin) {
                if (!kioskMode || storedPin.isBlank()) unlocked = true
            }

            // PIN verifier — handles encrypted and legacy plaintext storage
            val verifyPin = remember(storedPin, encryptor) {
                { input: String -> encryptor.verifyPin(storedPin, input) }
            }

            // ── Wall Mode escape: solid-state long-press timer ──
            var showWallExitDialog by remember { mutableStateOf(false) }

            HearthboardTheme(darkTheme = isDark, seedColor = seedColor) {
                CompositionLocalProvider(LocalWallMode provides wallState) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            HearthboardNavHost(app = app)

                            // ── Wall Mode escape overlay: long-press triggers dialog ──
                            if (wallMode) {
                                val wallModeScope = rememberCoroutineScope()
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

                            // ── Kiosk: intercept back press to prevent leaving ──
                            BackHandler(enabled = kioskMode && !unlocked) {
                                // Blocked — PIN overlay handles it
                            }

                            // ── Kiosk PIN overlay ──────────────────
                            if (kioskMode && storedPin.isNotBlank() && !unlocked) {
                                KioskPinOverlay(
                                    onVerify  = verifyPin,
                                    onUnlock  = { unlocked = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun KioskPinOverlay(
    onVerify: (String) -> Boolean,
    onUnlock: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    // Auto-submit on 4 digits
    LaunchedEffect(input) {
        if (input.length == 4) {
            if (onVerify(input)) {
                onUnlock()
            } else {
                error = true
                kotlinx.coroutines.delay(800)
                input = ""
                error = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "HearthBoard Locked",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Enter PIN to unlock",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            // PIN dots display
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (i in 0..3) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                if (i < input.length) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                CircleShape
                            )
                    )
                }
            }

            if (error) {
                Spacer(Modifier.height(8.dp))
                Text("Wrong PIN", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(32.dp))

            // Numeric keypad
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "⌫")
            )

            keys.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { key ->
                        val size = 72.dp
                        when {
                            key.isEmpty() -> Spacer(Modifier.width(size))
                            key == "⌫" -> {
                                IconButton(
                                    onClick = { if (input.isNotEmpty()) { input = input.dropLast(1); error = false } },
                                    modifier = Modifier.size(size)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Backspace, "Delete", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            else -> {
                                FilledTonalButton(
                                    onClick = { if (input.length < 4) { input += key; error = false } },
                                    modifier = Modifier.size(size),
                                    shape = CircleShape
                                ) {
                                    Text(key, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
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
