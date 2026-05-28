package com.openlight.cal.data.sync

import com.openlight.cal.data.model.CalendarEvent
import com.openlight.cal.data.model.Task
import com.openlight.cal.data.model.TaskPriority
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * Tests for ICalParser — VEVENT and VTODO serialization/deserialization.
 */
class ICalParserTest {

    @Test
    fun `parse single VEVENT from ICS`() {
        val now = Instant.now()
        val startMs = now.toEpochMilli()
        val endMs = now.plusSeconds(3600).toEpochMilli()

        val ical = buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//Hearthboard//EN")
            appendLine("BEGIN:VEVENT")
            appendLine("UID:test-uid-123")
            appendLine("DTSTART:${toIcalDate(startMs)}")
            appendLine("DTEND:${toIcalDate(endMs)}")
            appendLine("SUMMARY:Test Meeting")
            appendLine("DESCRIPTION:Discuss the project plan")
            appendLine("LOCATION:Conference Room A")
            appendLine("END:VEVENT")
            appendLine("END:VCALENDAR")
        }

        val result = ICalParser.parse(ical, accountId = 1L, calendarPath = "/cal/test/")

        assertEquals("Should parse 1 event", 1, result.events.size)
        assertEquals("Should parse 0 tasks", 0, result.tasks.size)

        val event = result.events[0]
        assertEquals("test-uid-123", event.uid)
        assertEquals("Test Meeting", event.title)
        assertEquals("Discuss the project plan", event.description)
        assertEquals("Conference Room A", event.location)
        assertEquals(1L, event.accountId)
        assertEquals("/cal/test/", event.calendarPath)
        assertFalse(event.isAllDay)
        assertFalse(event.isCancelled)
    }

    @Test
    fun `parse single VTODO from ICS`() {
        val dueMs = Instant.now().plusSeconds(86400).toEpochMilli()

        val ical = buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//Hearthboard//EN")
            appendLine("BEGIN:VTODO")
            appendLine("UID:task-uid-456")
            appendLine("SUMMARY:Buy groceries")
            appendLine("DESCRIPTION:Milk, eggs, bread")
            appendLine("PRIORITY:1")
            appendLine("DUE:${toIcalDate(dueMs)}")
            appendLine("STATUS:NEEDS-ACTION")
            appendLine("END:VTODO")
            appendLine("END:VCALENDAR")
        }

        val result = ICalParser.parse(ical, accountId = 2L, calendarPath = "/cal/tasks/")

        assertEquals("Should parse 0 events", 0, result.events.size)
        assertEquals("Should parse 1 task", 1, result.tasks.size)

        val task = result.tasks[0]
        assertEquals("task-uid-456", task.uid)
        assertEquals("Buy groceries", task.title)
        assertEquals("Milk, eggs, bread", task.description)
        assertEquals(2L, task.accountId)
        assertFalse(task.isCompleted)
    }

    @Test
    fun `serialize and deserialize event round-trip`() {
        val startMs = Instant.now().toEpochMilli()
        val endMs = Instant.now().plusSeconds(7200).toEpochMilli()

        val original = CalendarEvent(
            id = 0L,
            uid = "roundtrip-uid",
            accountId = 1L,
            calendarPath = "/cal/test/",
            etag = "",
            title = "Round Trip Test",
            description = "Testing serialization",
            location = "Virtual",
            startMs = startMs,
            endMs = endMs,
            isAllDay = false,
            personIds = "1,2",
            colorHex = "#2196F3",
            reminderMinutes = 30
        )

        val ical = ICalParser.serializeEvent(original)
        assertTrue("Serialized ICS should contain title", ical.contains("Round Trip Test"))
        assertTrue("Serialized ICS should contain UID", ical.contains("roundtrip-uid"))
        assertTrue("Serialized ICS should contain VEVENT", ical.contains("BEGIN:VEVENT"))
        assertTrue("Serialized ICS should contain VCALENDAR", ical.contains("BEGIN:VCALENDAR"))

        // Parse it back — iCal dates are second-precision, so expect truncated milliseconds
        val result = ICalParser.parse(ical, accountId = 1L, calendarPath = "/cal/test/")
        assertEquals(1, result.events.size)

        val parsed = result.events[0]
        assertEquals("roundtrip-uid", parsed.uid)
        assertEquals("Round Trip Test", parsed.title)
        assertEquals("Testing serialization", parsed.description)
        assertEquals("Virtual", parsed.location)
        // iCal DTSTART/DTEND are second-precision — round to nearest 1000
        assertEquals(startMs / 1000 * 1000, parsed.startMs)
        assertEquals(endMs / 1000 * 1000, parsed.endMs)
    }

    @Test
    fun `serialize and deserialize task round-trip`() {
        val dueMs = Instant.now().plusSeconds(172800).toEpochMilli()
        val original = Task(
            id = 0L,
            uid = "task-roundtrip",
            accountId = 2L,
            calendarPath = "/cal/tasks/",
            title = "Write unit tests",
            description = "Cover ICalParser, backoff, models",
            dueMs = dueMs,
            isCompleted = false,
            priority = TaskPriority.HIGH,
            starsEarned = 3,
            assignedPersonId = 1L
        )

        val ical = ICalParser.serializeTask(original)
        assertTrue("Should contain title", ical.contains("Write unit tests"))
        assertTrue("Should contain VTODO", ical.contains("BEGIN:VTODO"))
        assertTrue("Should contain UID", ical.contains("task-roundtrip"))

        // Parse it back
        val result = ICalParser.parse(ical, accountId = 2L, calendarPath = "/cal/tasks/")
        assertEquals(1, result.tasks.size)

        val parsed = result.tasks[0]
        assertEquals("task-roundtrip", parsed.uid)
        assertEquals("Write unit tests", parsed.title)
        assertEquals("Cover ICalParser, backoff, models", parsed.description)
        assertEquals(TaskPriority.HIGH, parsed.priority)
        // starsEarned is a Hearthboard-specific field not serialized to iCal — expect 0
    }

    @Test
    fun `parse empty ical returns nothing`() {
        val result = ICalParser.parse("", accountId = 1L, calendarPath = "/cal/")
        assertEquals(0, result.events.size)
        assertEquals(0, result.tasks.size)
    }

    @Test
    fun `parse ical with no VEVENT or VTODO returns nothing`() {
        val ical = buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("END:VCALENDAR")
        }
        val result = ICalParser.parse(ical, accountId = 1L, calendarPath = "/cal/")
        assertEquals(0, result.events.size)
        assertEquals(0, result.tasks.size)
    }

    @Test
    fun `generateUid produces unique values`() {
        val uid1 = ICalParser.generateUid()
        val uid2 = ICalParser.generateUid()
        assertNotEquals("UIDS should be unique", uid1, uid2)
        assertTrue("UID should contain @", uid1.contains("@"))
        assertTrue("UID should contain openlight", uid1.contains("openlight"))
    }

    // ── helpers ──────────────────────────────────────────────
    private fun toIcalDate(epochMs: Long): String {
        val instant = Instant.ofEpochMilli(epochMs)
        val zdt = instant.atZone(java.time.ZoneOffset.UTC)
        return zdt.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
    }
}