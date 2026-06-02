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

// ─────────────────────────────────────────────────────────────
// REWARDS
// ─────────────────────────────────────────────────────────────
// A Reward is something a person can spend stars on (a treat, a privilege,
// screen time, a small gift, etc.). Each redemption creates a RedeemedReward
// row that records the transaction; a person's balance is computed at query
// time as sum(tasks.starsEarned where assignedPersonId=X AND isCompleted)
// minus sum(redeemedRewards.cost where personId=X). We don't denormalize
// the balance onto Person to avoid drift bugs when tasks are edited.

@Immutable
@Entity(tableName = "rewards")
data class Reward(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "🎁",              // shown in shop tiles
    val starCost: Int,
    val description: String = "",
    val isEnabled: Boolean = true,         // soft-hide without deleting
    val sortOrder: Int = 0
)

@Immutable
@Entity(tableName = "redeemed_rewards")
data class RedeemedReward(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rewardId: Long,                    // FK to Reward (no constraint -
                                           //  we keep history even if reward deleted)
    val rewardName: String,                // denormalized for history
    val rewardEmoji: String,               // denormalized for history
    val personId: Long,                    // FK to Person who redeemed
    val cost: Int,                         // denormalized — cost at time of redemption
    val redeemedAtMs: Long = System.currentTimeMillis(),
    val note: String = ""                  // optional parent note
)

// ─────────────────────────────────────────────────────────────
// RECIPE
// ─────────────────────────────────────────────────────────────
// Self-contained recipe entity. App-local is source of truth (per the
// offline-first design choice); Mealie is an optional push-only mirror.
// Ingredients and instructions are stored as JSON arrays in single
// columns rather than child tables because (a) they're always loaded
// together, (b) we never query into them, and (c) it keeps the
// migration simple — one table, no foreign keys, no cascades.
//
// Sync state lives on the row itself:
//   mealieId          = null   → app-only, never been pushed
//   mealieId          = "abc"  → linked to remote recipe abc
//   mealieLastPushMs  = N      → last successful push timestamp
//   updatedAtMs       > push   → local edits since last push
@Immutable
@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    /** JSON array of ingredient strings, e.g. ["2 cups flour", "1 tsp salt"].
     *  Stored as JSON for simplicity; parsed by the UI/repo layer. */
    val ingredientsJson: String = "[]",
    /** JSON array of instruction step strings. */
    val instructionsJson: String = "[]",
    val prepTimeMinutes: Int = 0,        // 0 = not specified
    val cookTimeMinutes: Int = 0,        // 0 = not specified
    val servings: Int = 0,               // 0 = not specified
    val imageUrl: String = "",           // remote URL or empty
    val sourceUrl: String = "",          // recipe origin (Mealie, website, etc.)
    val tags: String = "",               // comma-separated free-form tags
    val rating: Int = 0,                 // 0-5, 0 = unrated
    val notes: String = "",              // private family notes
    val isFavorite: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    // ── Mealie integration (optional) ─────────────────────────
    val mealieId: String = "",           // empty = app-only
    val mealieLastPushMs: Long = 0       // 0 = never synced
)
