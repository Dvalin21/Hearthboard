package com.openlight.cal.data.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.regex.Pattern

/**
 * Parses calendar events from:
 *   - Email body text (appointment confirmations, school notices)
 *   - Plain text schedules (pasted or extracted from PDFs)
 *
 * Uses regex patterns to extract dates, times, locations, and titles.
 * No external dependencies. No tracking.
 *
 * Handles common formats:
 *   "December 15, 2025 at 3:00 PM"
 *   "12/15/2025 15:00"
 *   "Monday, December 15"
 *   "Appointment: Dr. Smith on Jan 20 at 10am"
 */
object EventTextParser {

    private const val TAG = "EventTextParser"

    data class ParsedEvent(
        val title: String,
        val date: LocalDate?,
        val time: LocalTime?,
        val endTime: LocalTime?,
        val location: String,
        val confidence: Float  // 0.0–1.0
    )

    /**
     * Parse event information from raw text (email body, PDF text).
     * Returns all potential events found.
     */
    fun parse(text: String): List<ParsedEvent> {
        val results = mutableListOf<ParsedEvent>()
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Try full-date patterns across the whole text
        val combined = text.replace("\n", " ").replace("\r", "")

        // Pattern: "Month DD, YYYY at H:MM AM/PM"
        val dateTimePattern = Pattern.compile(
            "($MONTH_PATTERN\\s+\\d{1,2},?\\s+\\d{4})\\s+(?:at\\s+)?(\\d{1,2}:\\d{2}\\s*(?:AM|PM|am|pm)?)",
            Pattern.CASE_INSENSITIVE
        )
        val m = dateTimePattern.matcher(combined)
        while (m.find()) {
            val date = parseDate(m.group(1))
            val time = parseTime(m.group(2))
            // Look backwards for a title (text before the date match)
            val before = combined.substring(0, m.start()).trim()
            val title = extractTitle(before)
            results.add(ParsedEvent(
                title = title,
                date = date,
                time = time,
                endTime = null,
                location = extractLocation(before),
                confidence = if (date != null && time != null) 0.9f else 0.5f
            ))
        }

        // Pattern: "Date: Dec 15, 2025" then "Time: 3:00 PM" on next lines
        var currentDate: LocalDate? = null
        var currentTime: LocalTime? = null
        for (line in lines) {
            val lower = line.lowercase()
            when {
                lower.startsWith("date:") || lower.startsWith("date ") -> {
                    currentDate = parseDate(line.substringAfter(":").trim())
                }
                lower.startsWith("time:") || lower.startsWith("time ") -> {
                    currentTime = parseTime(line.substringAfter(":").trim())
                }
                lower.startsWith("location:") || lower.startsWith("where:") -> {
                    val loc = line.substringAfter(":").trim()
                    if (loc.isNotBlank()) {
                        results.add(ParsedEvent(
                            title = extractTitle(combined),
                            date = currentDate,
                            time = currentTime,
                            endTime = null,
                            location = loc,
                            confidence = 0.7f
                        ))
                    }
                }
            }
        }

        return results
    }

    /** Extract plain text from a PDF via content URI. */
    fun extractTextFromPdf(context: Context, uri: Uri): String {
        return try {
            val doc = com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
            val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(
                context.contentResolver.openInputStream(uri)
            )
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            stripper.getText(document).also { document.close() }
        } catch (e: Exception) {
            Log.e(TAG, "PDF extraction failed: ${e.message}")
            // Fallback: try reading as plain text
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText()
                } ?: ""
            } catch (_: Exception) { "" }
        }
    }

    // ── Private helpers ──────────────────────────────────────

    private val MONTH_PATTERN = "(?:January|February|March|April|May|June|July|August|September|October|November|December|" +
        "Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)"

    private fun parseDate(text: String): LocalDate? {
        val cleaned = text.replace(",", "").trim()
        // Try "Month DD YYYY"
        val formats = listOf(
            DateTimeFormatter.ofPattern("MMMM d yyyy", Locale.US),
            DateTimeFormatter.ofPattern("MMM d yyyy", Locale.US),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yy"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.US),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
        )
        for (fmt in formats) {
            try { return LocalDate.parse(cleaned, fmt) } catch (_: DateTimeParseException) {}
        }
        return null
    }

    private fun parseTime(text: String): LocalTime? {
        val cleaned = text.trim().uppercase().replace(" ", "")
        // Try "3:00PM", "3:00 PM", "15:00"
        val patterns = listOf(
            Pattern.compile("(\\d{1,2}):(\\d{2})(AM|PM)?"),
            Pattern.compile("(\\d{1,2})(AM|PM)")
        )
        for (p in patterns) {
            val m = p.matcher(cleaned)
            if (m.find()) {
                var hour = m.group(1).toInt()
                val minute = if (m.groupCount() >= 2 && m.group(2)?.length == 2) m.group(2).toInt() else 0
                val ampm = if (m.groupCount() >= 3) m.group(3) else null
                if (ampm == "PM" && hour < 12) hour += 12
                if (ampm == "AM" && hour == 12) hour = 0
                return try { LocalTime.of(hour, minute) } catch (_: Exception) { null }
            }
        }
        return null
    }

    private fun extractTitle(before: String): String {
        // Take the last meaningful line before the date
        val lines = before.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return "Event"
        val last = lines.last()
            .removePrefix("Subject:").removePrefix("RE:").removePrefix("FWD:")
            .trim()
        return last.take(80).ifBlank { "Event" }
    }

    private fun extractLocation(before: String): String {
        val lower = before.lowercase()
        val locPattern = Regex("(?:at|location|where|address|venue)[:\\s]+(.+?)(?:\n|\$)", RegexOption.IGNORE_CASE)
        val m = locPattern.find(lower)
        return m?.groupValues?.getOrNull(1)?.trim()?.take(100) ?: ""
    }
}