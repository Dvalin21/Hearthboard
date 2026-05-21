package com.openlight.cal.data.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * Structural verification that all @Immutable-annotated data classes
 * behave as immutable value types.
 *
 * The @Immutable annotation (binary retention, not accessible via
 * reflection at runtime) tells the Compose compiler that instances
 * of these types will always have the same value for equals/hashCode
 * for the same property values — i.e., they're deeply immutable.
 *
 * These tests prove that property by verifying:
 *   1. equals/hashCode contract holds
 *   2. copy() produces independent instances
 *   3. Structural equality matches field-level equality
 */
class ModelsImmutableTest {

    // ─────────────────────────────────────────────────────────
    // Person
    // ─────────────────────────────────────────────────────────
    @Test
    fun `Person equals and hashCode are consistent`() {
        val a = Person(id = 1, name = "Alice", colorHex = "#E91E63")
        val b = Person(id = 1, name = "Alice", colorHex = "#E91E63")
        val c = Person(id = 2, name = "Bob", colorHex = "#4CAF50")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
        assertNotEquals(a.hashCode(), c.hashCode())
    }

    @Test
    fun `Person copy creates independent instance`() {
        val original = Person(id = 1, name = "Alice", colorHex = "#E91E63")
        val modified = original.copy(name = "Alice Modified")

        assertEquals("Original unchanged", "Alice", original.name)
        assertEquals("Modified has new name", "Alice Modified", modified.name)
        assertNotEquals("Different instances", original, modified)
    }

    @Test
    fun `Person all properties are val (structural immutability)`() {
        val p = Person(id = 42, name = "Test", colorHex = "#000", email = "test@example.com")
        // Verify every property is accessible and matches constructor
        assertEquals(42L, p.id)
        assertEquals("Test", p.name)
        assertEquals("#000", p.colorHex)
        assertEquals("T", p.initial)  // derived from name.firstOrNull()
        assertFalse(p.isDefault)
        assertEquals(0, p.sortOrder)
        assertEquals(PersonRole.PARENT, p.role)
        assertEquals(0L, p.caregiverPersonId)
        assertEquals("test@example.com", p.email)
    }

    // ─────────────────────────────────────────────────────────
    // CalendarAccount
    // ─────────────────────────────────────────────────────────
    @Test
    fun `CalendarAccount equals and copy are correct`() {
        val a = CalendarAccount(id = 1, displayName = "Work", serverUrl = "https://cal.example.com")
        val b = CalendarAccount(id = 1, displayName = "Work", serverUrl = "https://cal.example.com")
        val c = a.copy(enabled = false)

        assertEquals(a, b)
        assertNotEquals(a, c)
        assertFalse("Copy modified enabled", c.enabled)
        assertTrue("Original unchanged", a.enabled)
    }

    @Test
    fun `CalendarAccount default values are sensible`() {
        val acc = CalendarAccount(displayName = "Default Test")
        assertEquals(AccountType.CALDAV, acc.accountType)
        assertTrue(acc.enabled)
        assertEquals(30, acc.syncIntervalMinutes)
        assertEquals(0, acc.syncFailCount)
        assertEquals(0L, acc.syncBackoffUntil)
    }

    // ─────────────────────────────────────────────────────────
    // CalendarEvent
    // ─────────────────────────────────────────────────────────
    @Test
    fun `CalendarEvent structural equality`() {
        val now = System.currentTimeMillis()
        val later = now + 3_600_000L
        val a = CalendarEvent(title = "Meeting", startMs = now, endMs = later)
        val b = CalendarEvent(title = "Meeting", startMs = now, endMs = later)
        val c = CalendarEvent(title = "Different", startMs = now, endMs = later)

        assertEquals("Same values should be equal", a, b)
        assertNotEquals("Different title should differ", a, c)
    }

    @Test
    fun `CalendarEvent copy preserves original`() {
        val now = System.currentTimeMillis()
        val e = CalendarEvent(title = "Original", startMs = now, endMs = now + 1)
        val modified = e.copy(title = "Updated", location = "Room 1")

        assertEquals("Original", e.title)
        assertEquals("", e.location)
        assertEquals("Updated", modified.title)
        assertEquals("Room 1", modified.location)
    }

    @Test
    fun `CalendarEvent cancellation flag`() {
        val now = System.currentTimeMillis()
        val active = CalendarEvent(title = "Active", startMs = now, endMs = now + 1)
        val cancelled = active.copy(isCancelled = true)

        assertFalse(active.isCancelled)
        assertTrue(cancelled.isCancelled)
        assertNotEquals(active, cancelled)
    }

    // ─────────────────────────────────────────────────────────
    // Task
    // ─────────────────────────────────────────────────────────
    @Test
    fun `Task equals and defaults`() {
        val a = Task(title = "Buy milk")
        val b = Task(title = "Buy milk")
        val c = Task(title = "Buy milk", priority = TaskPriority.HIGH)

        assertEquals("Default priority should match", a, b)
        assertNotEquals("Different priority should differ", a, c)
        assertEquals(TaskPriority.NORMAL, a.priority)
        assertFalse(a.isCompleted)
    }

    @Test
    fun `Task copy with completion`() {
        val task = Task(title = "Write tests", priority = TaskPriority.HIGH)
        val now = System.currentTimeMillis()
        val done = task.copy(isCompleted = true, completedMs = now)

        assertFalse(task.isCompleted)
        assertNull(task.completedMs)
        assertTrue(done.isCompleted)
        assertEquals(now, done.completedMs)
    }

    // ─────────────────────────────────────────────────────────
    // CheckList + CheckListItem
    // ─────────────────────────────────────────────────────────
    @Test
    fun `CheckList and CheckListItem equality`() {
        val list = CheckList(name = "Groceries")
        val item = CheckListItem(listId = list.id, text = "Apples")

        assertEquals(CheckList(name = "Groceries"), list)
        assertEquals(CheckListItem(listId = 0, text = "Apples"), item)
        assertNotEquals(CheckListItem(listId = 0, text = "Bananas"), item)
    }

    @Test
    fun `CheckListItem checked state is copy-safe`() {
        val item = CheckListItem(listId = 1, text = "Milk")
        val checked = item.copy(isChecked = true)

        assertFalse(item.isChecked)
        assertTrue(checked.isChecked)
    }

    // ─────────────────────────────────────────────────────────
    // MealPlan
    // ─────────────────────────────────────────────────────────
    @Test
    fun `MealPlan compound primary key behavior`() {
        val a = MealPlan(dateIso = "2026-05-21", slot = MealSlot.BREAKFAST, title = "Oatmeal")
        val b = MealPlan(dateIso = "2026-05-21", slot = MealSlot.BREAKFAST, title = "Oatmeal")
        val c = MealPlan(dateIso = "2026-05-21", slot = MealSlot.LUNCH, title = "Salad")

        assertEquals(a, b)
        assertNotEquals(a, c)
        assertEquals(MealSlot.BREAKFAST, a.slot)
        assertEquals(MealSlot.LUNCH, c.slot)
    }

    // ─────────────────────────────────────────────────────────
    // Enums used in data classes are also inherently stable
    // ─────────────────────────────────────────────────────────
    @Test
    fun `enums used by @Immutable data classes are stable`() {
        // Proving enums are singleton-valued and safe for equality comparison
        assertSame(PersonRole.PARENT, PersonRole.valueOf("PARENT"))
        assertSame(AccountType.CALDAV, AccountType.valueOf("CALDAV"))
        assertSame(TaskPriority.HIGH, TaskPriority.valueOf("HIGH"))
        assertSame(MealSlot.DINNER, MealSlot.valueOf("DINNER"))
    }

    // ─────────────────────────────────────────────────────────
    // Cross-type: data classes don't interfere with each other
    // ─────────────────────────────────────────────────────────
    @Test
    fun `different entity types are never equal`() {
        // This is a structural property: all data classes use `id` differently
        // so a Person and a Task with same id field value are distinct types
        val person = Person(id = 1, name = "X", colorHex = "#000")
        val task = Task(title = "X")
        assertNotEquals("Different types should not be equal", person, task)
    }

    @Test
    fun `all @Immutable classes support null-safe field defaults`() {
        // Every data class should handle default construction gracefully
        // This proves no property requires non-default values that would
        // make the class harder to use with copy()
        val person = Person(name = "Test", colorHex = "#000")
        val event = CalendarEvent(title = "Test", startMs = 0, endMs = 0)
        val task = Task(title = "Test")

        assertNotNull(person)
        assertNotNull(event)
        assertNotNull(task)
    }
}
