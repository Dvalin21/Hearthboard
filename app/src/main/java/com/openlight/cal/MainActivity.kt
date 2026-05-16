package com.openlight.cal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openlight.cal.data.preferences.AppPreferences
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
            val systemDark   = isSystemInDarkTheme()

            val isDark = when (darkModePref) {
                1    -> false   // Light
                2    -> true    // Dark
                else -> systemDark  // System
            }

            val seedColor = remember(themeSeedHex) {
                runCatching { Color(android.graphics.Color.parseColor(themeSeedHex)) }.getOrNull()
            }

            OpenLightTheme(darkTheme = isDark, seedColor = seedColor) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OpenLightNavHost(app = app)
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
            // Read synchronously via runBlocking from prefs flow
            kotlinx.coroutines.runBlocking {
                app.preferences.kioskMode.collect {
                    kiosk = it
                    // cancel after first value
                    throw kotlinx.coroutines.CancellationException()
                }
            }
            kiosk
        }.getOrElse { false }

        if (!kioskActive) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
        // If kiosk active: swallow back press, stay in app
    }
}
