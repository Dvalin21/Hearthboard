package com.openlight.cal.data.sync

import android.util.Log
import com.openlight.cal.data.model.CalendarEvent
import com.openlight.cal.data.model.MealSlot
import com.openlight.cal.data.model.Task
import com.openlight.cal.data.model.TaskPriority
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Lightweight iCal (RFC 5545) parser.
 * Handles VEVENT and VTODO components.
 * No external library dependencies → F-Droid compatible.
 */
object ICalParser {

    private const val TAG = "ICalParser"

    // ─────────────────────────────────────────────────────────
    // Parse a full VCALENDAR blob into events + tasks
    // ─────────────────────────────────────────────────────────
    data class ParseResult(
        val events: List<CalendarEvent>,
        val tasks: List<Task>
    )

    fun parse(ical: String, accountId: Long, calendarPath: String): ParseResult {
        val events = mutableListOf<CalendarEvent>()
        val tasks  = mutableListOf<Task>()

        val lines = unfoldLines(ical)
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> {
                    val (component, nextIndex) = extractComponent(lines, i, "VEVENT")
                    parseVEvent(component, accountId, calendarPath)?.let { events.add(it) }
                    i = nextIndex
                }
                line.equals("BEGIN:VTODO", ignoreCase = true) -> {
                    val (component, nextIndex) = extractComponent(lines, i, "VTODO")
                    parseVTodo(component, accountId, calendarPath)?.let { tasks.add(it) }
                    i = nextIndex
                }
                else -> i++
            }
        }
        return ParseResult(events, tasks)
    }

    // ─────────────────────────────────────────────────────────
    // Parse single VEVENT
    // ─────────────────────────────────────────────────────────
    fun parseVEvent(lines: List<String>, accountId: Long, calendarPath: String, etag: String = ""): CalendarEvent? {
        val props = parseProperties(lines)
        return try {
            val uid     = props["UID"] ?: return null
            val summary = props["SUMMARY"] ?: "(No title)"
            val dtstart = props.entries.firstOrNull { it.key.startsWith("DTSTART") }
            val dtend   = props.entries.firstOrNull { it.key.startsWith("DTEND") }
                       ?: props.entries.firstOrNull { it.key.startsWith("DURATION") }
            val allDay  = dtstart?.key?.contains("DATE") == true && !dtstart.key.contains("DATE-TIME")
            val startMs = parseDateTime(dtstart?.key ?: "DTSTART", dtstart?.value ?: return null)
            val endMs   = if (dtend != null && dtend.key.startsWith("DTEND")) {
                              parseDateTime(dtend.key, dtend.value)
                          } else {
                              startMs + 3600_000L // 1hr default
                          }
            val status  = props["STATUS"] ?: ""

            val organizerEmail = extractOrganizerEmail(props)

            CalendarEvent(
                uid            = uid,
                accountId      = accountId,
                calendarPath   = calendarPath,
                etag           = etag,
                title          = summary,
                description    = props["DESCRIPTION"] ?: "",
                location       = props["LOCATION"] ?: "",
                startMs        = startMs,
                endMs          = endMs,
                isAllDay       = allDay,
                recurrenceRule = props["RRULE"] ?: "",
                isCancelled    = status.equals("CANCELLED", ignoreCase = true),
                rawIcal        = lines.joinToString("\r\n"),
                organizerEmail = organizerEmail
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse VEVENT: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────
    // Parse single VTODO
    // ─────────────────────────────────────────────────────────
    fun parseVTodo(lines: List<String>, accountId: Long, calendarPath: String, etag: String = ""): Task? {
        val props = parseProperties(lines)
        return try {
            val uid     = props["UID"] ?: return null
            val summary = props["SUMMARY"] ?: "(No title)"
            val status  = props["STATUS"] ?: ""
            val done    = status.equals("COMPLETED", ignoreCase = true)
            val due     = props.entries.firstOrNull { it.key.startsWith("DUE") }
            val dueMs   = due?.let { runCatching { parseDateTime(it.key, it.value) }.getOrNull() }
            val priorityNum = props["PRIORITY"]?.toIntOrNull() ?: 0
            val priority = when {
                priorityNum in 1..4  -> TaskPriority.HIGH
                priorityNum in 5..5  -> TaskPriority.NORMAL
                priorityNum in 6..9  -> TaskPriority.LOW
                else                 -> TaskPriority.NORMAL
            }

            Task(
                uid         = uid,
                accountId   = accountId,
                calendarPath= calendarPath,
                etag        = etag,
                title       = summary,
                description = props["DESCRIPTION"] ?: "",
                isCompleted = done,
                dueMs       = dueMs,
                priority    = priority
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse VTODO: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────
    // Serialize CalendarEvent → VCALENDAR string
    // ─────────────────────────────────────────────────────────
    fun serializeEvent(event: CalendarEvent): String {
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//OpenLight//OpenLight 1.0//EN")
        sb.appendLine("BEGIN:VEVENT")
        sb.appendLine("UID:${event.uid.ifBlank { generateUid() }}")
        sb.appendLine("SUMMARY:${foldLine(event.title)}")
        if (event.isAllDay) {
            val ld = Instant.ofEpochMilli(event.startMs).atZone(ZoneId.systemDefault()).toLocalDate()
            sb.appendLine("DTSTART;VALUE=DATE:${ld.format(DateTimeFormatter.BASIC_ISO_DATE)}")
            val ldEnd = Instant.ofEpochMilli(event.endMs).atZone(ZoneId.systemDefault()).toLocalDate()
            sb.appendLine("DTEND;VALUE=DATE:${ldEnd.format(DateTimeFormatter.BASIC_ISO_DATE)}")
        } else {
            val utcFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            val start = Instant.ofEpochMilli(event.startMs).atZone(ZoneOffset.UTC)
            val end   = Instant.ofEpochMilli(event.endMs).atZone(ZoneOffset.UTC)
            sb.appendLine("DTSTART:${start.format(utcFmt)}")
            sb.appendLine("DTEND:${end.format(utcFmt)}")
        }
        if (event.organizerEmail.isNotBlank()) sb.appendLine("ORGANIZER:mailto:${event.organizerEmail}")
        if (event.description.isNotBlank())    sb.appendLine("DESCRIPTION:${foldLine(event.description)}")
        if (event.location.isNotBlank())       sb.appendLine("LOCATION:${foldLine(event.location)}")
        if (event.recurrenceRule.isNotBlank()) sb.appendLine("RRULE:${event.recurrenceRule}")
        val dtstamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .format(Instant.now().atZone(ZoneOffset.UTC))
        sb.appendLine("DTSTAMP:$dtstamp")
        sb.appendLine("END:VEVENT")
        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }

    // ─────────────────────────────────────────────────────────
    // Serialize Task → VCALENDAR with VTODO
    // ─────────────────────────────────────────────────────────
    fun serializeTask(task: Task): String {
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//OpenLight//OpenLight 1.0//EN")
        sb.appendLine("BEGIN:VTODO")
        sb.appendLine("UID:${task.uid.ifBlank { generateUid() }}")
        sb.appendLine("SUMMARY:${foldLine(task.title)}")
        if (task.description.isNotBlank()) sb.appendLine("DESCRIPTION:${foldLine(task.description)}")
        sb.appendLine("STATUS:${if (task.isCompleted) "COMPLETED" else "NEEDS-ACTION"}")
        if (task.dueMs != null) {
            val utcFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            sb.appendLine("DUE:${Instant.ofEpochMilli(task.dueMs).atZone(ZoneOffset.UTC).format(utcFmt)}")
        }
        val prio = when (task.priority) {
            TaskPriority.HIGH   -> 1
            TaskPriority.NORMAL -> 5
            TaskPriority.LOW    -> 9
        }
        sb.appendLine("PRIORITY:$prio")
        val dtstamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .format(Instant.now().atZone(ZoneOffset.UTC))
        sb.appendLine("DTSTAMP:$dtstamp")
        sb.appendLine("END:VTODO")
        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }

    // ─────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────

    private fun unfoldLines(ical: String): List<String> {
        val result = mutableListOf<String>()
        val raw = ical.lines()
        var cur = StringBuilder()
        for (line in raw) {
            when {
                line.startsWith(" ") || line.startsWith("\t") -> cur.append(line.trimStart())
                else -> {
                    if (cur.isNotEmpty()) result.add(cur.toString())
                    cur = StringBuilder(line)
                }
            }
        }
        if (cur.isNotEmpty()) result.add(cur.toString())
        return result
    }

    private fun extractComponent(lines: List<String>, startIdx: Int, name: String): Pair<List<String>, Int> {
        val component = mutableListOf<String>()
        var i = startIdx
        while (i < lines.size) {
            component.add(lines[i])
            if (lines[i].trim().equals("END:$name", ignoreCase = true)) {
                return component to (i + 1)
            }
            i++
        }
        return component to i
    }

    private fun parseProperties(lines: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (line in lines) {
            val colonIdx = line.indexOf(':')
            if (colonIdx <= 0) continue
            val key   = line.substring(0, colonIdx).trim().uppercase()
            val value = line.substring(colonIdx + 1)
            // Store first occurrence (except UID which we always want)
            if (!map.containsKey(key) || key == "UID") {
                map[key] = value
            }
        }
        return map
    }

    private val UTC_FMT_FULL   = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val LOCAL_FMT_FULL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DATE_FMT       = DateTimeFormatter.ofPattern("yyyyMMdd")

    private fun parseDateTime(key: String, value: String): Long {
        val clean = value.trim()
        return when {
            clean.endsWith("Z") -> {
                LocalDateTime.parse(clean, UTC_FMT_FULL)
                    .toInstant(ZoneOffset.UTC).toEpochMilli()
            }
            clean.contains("T") -> {
                // Local time - assume system timezone
                LocalDateTime.parse(clean, LOCAL_FMT_FULL)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            else -> {
                // DATE value
                LocalDate.parse(clean, DATE_FMT)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
    }

    /** Extract email from ORGANIZER property (handles ORGANIZER;CN=... format). */
    private fun extractOrganizerEmail(props: Map<String, String>): String {
        val entry = props.entries.firstOrNull { it.key.startsWith("ORGANIZER") } ?: return ""
        return entry.value.removePrefix("mailto:").trim()
    }

    private fun foldLine(input: String): String =
        input.replace("\n", "\\n").replace("\r", "")

    fun generateUid(): String =
        "${System.currentTimeMillis()}-${(Math.random() * 1_000_000).toLong()}@openlight"
}
