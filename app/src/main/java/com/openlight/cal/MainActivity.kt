package com.openlight.cal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openlight.cal.ui.navigation.OpenLightNavHost
import com.openlight.cal.ui.theme.OpenLightTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val app   = application as OpenLightApp
            val prefs = app.preferences

            val darkModePref by prefs.darkMode.collectAsStateWithLifecycle(initialValue = 0)
            val themeSeedHex by prefs.themeSeedColor.collectAsStateWithLifecycle(initialValue = "#2196F3")
            val kioskMode   by prefs.kioskMode.collectAsStateWithLifecycle(initialValue = false)
            val pin         by prefs.parentalPin.collectAsStateWithLifecycle(initialValue = "")
            val systemDark  = isSystemInDarkTheme()

            val isDark = when (darkModePref) {
                1    -> false   // Light
                2    -> true    // Dark
                else -> systemDark  // System
            }

            val seedColor = remember(themeSeedHex) {
                runCatching { Color(android.graphics.Color.parseColor(themeSeedHex)) }.getOrNull()
            }

            // ── Kiosk lock state ───────────────────────────────
            var unlocked by remember { mutableStateOf(!kioskMode || pin.isBlank()) }

            // Re-lock when app goes to background
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        unlocked = !kioskMode || pin.isBlank()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // Update unlocked when kiosk/pin settings change
            LaunchedEffect(kioskMode, pin) {
                if (!kioskMode || pin.isBlank()) unlocked = true
            }

            OpenLightTheme(darkTheme = isDark, seedColor = seedColor) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        OpenLightNavHost(app = app)

                        // ── Kiosk PIN overlay ──────────────────
                        if (kioskMode && pin.isNotBlank() && !unlocked) {
                            KioskPinOverlay(
                                correctPin = pin,
                                onUnlock   = { unlocked = true }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Kiosk: intercept back press to prevent leaving ────────
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val app   = application as OpenLightApp
        val kioskActive = runCatching {
            var kiosk = false
            kotlinx.coroutines.runBlocking {
                app.preferences.kioskMode.collect {
                    kiosk = it
                    throw kotlinx.coroutines.CancellationException()
                }
            }
            kiosk
        }.getOrElse { false }

        if (!kioskActive) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}

@Composable
private fun KioskPinOverlay(
    correctPin: String,
    onUnlock: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    // Auto-submit on 4 digits
    LaunchedEffect(input) {
        if (input.length == 4) {
            if (input == correctPin) {
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
                "OpenLight Locked",
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
