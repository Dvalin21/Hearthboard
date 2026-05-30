package com.openlight.cal.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

// ─────────────────────────────────────────────────────────────
// PERSON  – family member with color-coded identity
// role: PARENT (manages own + dependents), CHILD (auto-accepts parent events),
//        DEPENDENT (care recipient managed by caregiver)
// caregiverPersonId: links DEPENDENT to the PARENT who manages their scheduling
// ─────────────────────────────────────────────────────────────
enum class PersonRole { PARENT, CHILD, DEPENDENT }

@Immutable
@Entity(tableName = "people")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String,               // e.g. "#4CAF50" – user-modifiable
    val initial: String = name.firstOrNull()?.uppercase() ?: "?",
    val isDefault: Boolean = false,     // "Everyone" / unassigned slot
    val sortOrder: Int = 0,
    val role: PersonRole = PersonRole.PARENT,
    val caregiverPersonId: Long = 0L,   // 0 = no caregiver (self-managed)
    val email: String = ""              // matches ORGANIZER mailto from CalDAV
)

// ─────────────────────────────────────────────────────────────
// CALENDAR ACCOUNT  – CalDAV or ICS-URL account
// ─────────────────────────────────────────────────────────────
enum class AccountType { CALDAV, ICS_URL, LOCAL }

@Immutable
@Entity(tableName = "calendar_accounts")
data class CalendarAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val accountType: AccountType = AccountType.CALDAV,
    val serverUrl: String = "",
    val username: String = "",
    val passwordEncrypted: String = "",  // base64 obfuscated at rest
    val calendarPath: String = "",       // discovered CalDAV calendar path
    val colorHex: String = "#4A6178",     // slate (theme primary)
    val enabled: Boolean = true,
    val lastSyncMs: Long = 0L,
    val syncIntervalMinutes: Int = 30,
    val ctag: String = "",               // CalDAV change-tag for delta sync
    val syncFailCount: Int = 0,          // consecutive failures, drives backoff
    val syncBackoffUntil: Long = 0       // epoch ms — skip sync until this time
)

// ─────────────────────────────────────────────────────────────
// CALENDAR EVENT  – VEVENT mirror
// ─────────────────────────────────────────────────────────────
@Immutable
@Entity(tableName = "calendar_events")
data class CalendarEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = "",               // iCal UID for deduplication
    val accountId: Long = 0L,
    val calendarPath: String = "",
    val etag: String = "",              // CalDAV ETag for updates
    val title: String,
    val description: String = "",
    val location: String = "",
    val startMs: Long,
    val endMs: Long,
    val isAllDay: Boolean = false,
    val recurrenceRule: String = "",    // RRULE string
    val colorHex: String = "",          // overrides account color if set
    val personIds: String = "",         // comma-separated Person IDs
    val isCountdown: Boolean = false,   // show countdown widget
    val reminderMinutes: Int = -1,      // -1 = use default
    val isCancelled: Boolean = false,
    val rawIcal: String = "",           // store original VCALENDAR blob
    val isLocalOnly: Boolean = false,
    val organizerEmail: String = ""     // from ORGANIZER:mailto in ICS, for person matching
)

// ─────────────────────────────────────────────────────────────
// TASK  – VTODO via CalDAV or local
// ─────────────────────────────────────────────────────────────
enum class TaskPriority { HIGH, NORMAL, LOW }

@Immutable
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = "",
    val accountId: Long = 0L,
    val calendarPath: String = "",
    val etag: String = "",
    val title: String,
    val description: String = "",
    val assignedPersonId: Long = 0L,    // 0 = everyone
    val dueMs: Long? = null,
    val isCompleted: Boolean = false,
    val completedMs: Long? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val starsEarned: Int = 0,
    val sortOrder: Int = 0,
    val isLocalOnly: Boolean = false,
    val listId: Long = 0L,              // optional group
    /** True for kid-friendly chore tasks shown on the Chores screen.
     *  Chores are always local-only (no CalDAV sync) and are surfaced
     *  separately from regular tasks. Added without changing the
     *  database VERSION because Room handles new boolean columns with
     *  a default value via a Migration; if you bump the schema, add
     *  a matching ALTER TABLE in the migration. */
    val isChore: Boolean = false
)

// ─────────────────────────────────────────────────────────────
// CHECKLIST  – custom color-coded list (groceries, to-dos, etc.)
// ─────────────────────────────────────────────────────────────
@Immutable
@Entity(tableName = "checklists")
data class CheckList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String = "#FF9800",
    val sortOrder: Int = 0
)

@Immutable
@Entity(tableName = "checklist_items")
data class CheckListItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val text: String,
    val isChecked: Boolean = false,
    val sortOrder: Int = 0
)

// ─────────────────────────────────────────────────────────────
// MEAL PLAN
// ─────────────────────────────────────────────────────────────
enum class MealSlot { BREAKFAST, LUNCH, DINNER, SNACK }

@Immutable
@Entity(tableName = "meal_plans", primaryKeys = ["dateIso", "slot"])
data class MealPlan(
    val dateIso: String,           // "2025-03-15"
    val slot: MealSlot,
    val title: String,
    val notes: String = "",
    val personIds: String = ""     // comma-separated
)
