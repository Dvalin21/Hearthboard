package com.openlight.cal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ─────────────────────────────────────────────────────────────
// AppHeader — Skylight-style info bar
// Shows person avatar (initial in colored circle) on the LEFT,
// then date/day info, with weather on the right.
// ─────────────────────────────────────────────────────────────
@Composable
fun AppHeader(
    date: LocalDate,
    temperature: String?,
    personInitial: String?,
    personColor: Color?,
    modifier: Modifier = Modifier
) {
    val dateStr  = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Person avatar (LEFT side) ─────────────────────
        if (personInitial != null && personColor != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(personColor)
            ) {
                Text(
                    text       = personInitial,
                    color      = Color.White,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center
                )
            }
            Spacer(Modifier.width(10.dp))
        }

        // ── Date ─────────────────────────────────────────
        Text(
            text       = dateStr,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurface,
            modifier   = Modifier.weight(1f)
        )

        // ── Weather ──────────────────────────────────────
        if (temperature != null) {
            Text(
                text       = temperature,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
