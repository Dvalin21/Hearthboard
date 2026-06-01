package com.openlight.cal.data.backup

import android.content.Context
import android.net.Uri
import com.openlight.cal.HearthboardApp
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.model.*
import com.openlight.cal.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Exports and imports HearthBoard data to/from a JSON file.
 *
 * Export format:
 * {
 *   "version": 1,
 *   "appVersion": "1.0.0-alpha.16",
 *   "exportedAt": 1717000000000,
 *   "people": [ ... ],
 *   "calendarAccounts": [ ... ],
 *   "calendarEvents": [ ... ],
 *   "tasks": [ ... ],
 *   "checklists": [ ... ],
 *   "checklistItems": [ ... ],
 *   "mealPlans": [ ... ]
 * }
 *
 * Preferences (dark mode, theme, PIN, etc.) are excluded from export
 * since they're device-specific. Only the database is portable.
 */
object BackupManager {

    private const val TAG = "BackupManager"
    private const val BACKUP_VERSION = 1
    private const val MIME_TYPE = "application/json"
    const val FILENAME = "HearthBoard-backup.json"

    data class BackupResult(
        val success: Boolean,
        val message: String,
        val eventCount: Int = 0,
        val taskCount: Int = 0
    )

    /** Export database to a JSON file via the given URI (user-picked location). */
    suspend fun export(context: Context, uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            val json = JSONObject().apply {
                put("version", BACKUP_VERSION)
                put("appVersion", "1.0.0-alpha.16")
                put("exportedAt", System.currentTimeMillis())

                // People
                put("people", JSONArray(db.personDao().getAll().map { it.toJson() }))

                // Calendar accounts (strip encrypted passwords — user must re-enter)
                val safeAccounts = db.calendarAccountDao().getAll().map { acc ->
                    acc.toJson().put("passwordEncrypted", "")
                }
                put("calendarAccounts", JSONArray(safeAccounts))

                // Events
                put("calendarEvents", JSONArray(
                    db.calendarEventDao().getAll().map { it.toJson() }
                ))

                // Tasks
                put("tasks", JSONArray(
                    db.taskDao().getAll().map { it.toJson() }
                ))

                // Checklists + items
                put("checklists", JSONArray(
                    db.checkListDao().getAll().map { it.toJson() }
                ))
                put("checklistItems", JSONArray(
                    db.checkListDao().getAllItems().map { it.toJson() }
                ))

                // Meal plans
                put("mealPlans", JSONArray(
                    db.mealPlanDao().getAll().map { it.toJson() }
                ))

                // Rewards catalog + redemption history
                put("rewards", JSONArray(
                    db.rewardDao().getAll().map { it.toJson() }
                ))
                put("redeemedRewards", JSONArray(
                    db.redeemedRewardDao().getAll().map { it.toJson() }
                ))
            }

            context.contentResolver.openOutputStream(uri)?.use { stream ->
                OutputStreamWriter(stream).use { writer ->
                    writer.write(json.toString(2))
                }
            } ?: return@withContext BackupResult(false, "Could not open output stream")

            BackupResult(true, "Backup saved", eventCount = json.getJSONArray("calendarEvents").length(),
                taskCount = json.getJSONArray("tasks").length())
        } catch (e: Exception) {
            BackupResult(false, "Export failed: ${e.message}")
        }
    }

    /** Import database from a JSON file via URI. Replaces all existing data. */
    suspend fun restore(context: Context, uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            } ?: return@withContext BackupResult(false, "Could not open input stream")

            val json = JSONObject(jsonString)
            val version = json.optInt("version", 0)
            if (version != BACKUP_VERSION) {
                return@withContext BackupResult(false,
                    "Unsupported backup version: $version. Expected: $BACKUP_VERSION")
            }

            val db = AppDatabase.getInstance(context)

            // Clear existing data. Room's clearAllTables() drops every row
            // in every table while preserving schema; FK constraint order is
            // handled by Room itself, so this is safer than the per-table
            // cascade that the original (uncommitted) cleanupDao would have
            // implemented.
            db.clearAllTables()

            // People
            val peopleArr = json.optJSONArray("people") ?: JSONArray()
            for (i in 0 until peopleArr.length()) {
                db.personDao().insert(personFromJson(peopleArr.getJSONObject(i)))
            }

            // Calendar accounts (user must re-enter passwords)
            val accountsArr = json.optJSONArray("calendarAccounts") ?: JSONArray()
            for (i in 0 until accountsArr.length()) {
                db.calendarAccountDao().insert(accountFromJson(accountsArr.getJSONObject(i)))
            }

            // Events
            val eventsArr = json.optJSONArray("calendarEvents") ?: JSONArray()
            for (i in 0 until eventsArr.length()) {
                db.calendarEventDao().insert(eventFromJson(eventsArr.getJSONObject(i)))
            }

            // Tasks
            val tasksArr = json.optJSONArray("tasks") ?: JSONArray()
            for (i in 0 until tasksArr.length()) {
                db.taskDao().insert(taskFromJson(tasksArr.getJSONObject(i)))
            }

            // Checklists
            val listsArr = json.optJSONArray("checklists") ?: JSONArray()
            for (i in 0 until listsArr.length()) {
                db.checkListDao().insertList(listFromJson(listsArr.getJSONObject(i)))
            }

            val itemsArr = json.optJSONArray("checklistItems") ?: JSONArray()
            for (i in 0 until itemsArr.length()) {
                db.checkListDao().insertItem(listItemFromJson(itemsArr.getJSONObject(i)))
            }

            // Meal plans
            val mealsArr = json.optJSONArray("mealPlans") ?: JSONArray()
            for (i in 0 until mealsArr.length()) {
                db.mealPlanDao().upsert(mealFromJson(mealsArr.getJSONObject(i)))
            }

            // Rewards
            val rewardsArr = json.optJSONArray("rewards") ?: JSONArray()
            for (i in 0 until rewardsArr.length()) {
                db.rewardDao().upsert(rewardFromJson(rewardsArr.getJSONObject(i)))
            }

            // Redemption history
            val redeemedArr = json.optJSONArray("redeemedRewards") ?: JSONArray()
            for (i in 0 until redeemedArr.length()) {
                db.redeemedRewardDao().insert(redeemedFromJson(redeemedArr.getJSONObject(i)))
            }

            BackupResult(true, "Restore complete — ${eventsArr.length()} events, ${tasksArr.length()} tasks restored",
                eventCount = eventsArr.length(), taskCount = tasksArr.length())
        } catch (e: Exception) {
            BackupResult(false, "Restore failed: ${e.message}")
        }
    }

    // ── JSON serialization helpers ────────────────────────────
    private fun Person.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("colorHex", colorHex)
        put("initial", initial); put("isDefault", isDefault); put("sortOrder", sortOrder)
        put("role", role.name); put("caregiverPersonId", caregiverPersonId); put("email", email)
    }
    private fun CalendarAccount.toJson() = JSONObject().apply {
        put("id", id); put("displayName", displayName); put("accountType", accountType.name)
        put("serverUrl", serverUrl); put("username", username)
        put("passwordEncrypted", passwordEncrypted); put("calendarPath", calendarPath)
        put("colorHex", colorHex); put("enabled", enabled); put("lastSyncMs", lastSyncMs)
        put("syncIntervalMinutes", syncIntervalMinutes); put("ctag", ctag)
        put("syncFailCount", syncFailCount); put("syncBackoffUntil", syncBackoffUntil)
    }
    private fun CalendarEvent.toJson() = JSONObject().apply {
        put("id", id); put("uid", uid); put("accountId", accountId)
        put("calendarPath", calendarPath); put("etag", etag); put("title", title)
        put("description", description); put("location", location); put("startMs", startMs)
        put("endMs", endMs); put("isAllDay", isAllDay); put("recurrenceRule", recurrenceRule)
        put("colorHex", colorHex); put("personIds", personIds); put("isCountdown", isCountdown)
        put("reminderMinutes", reminderMinutes); put("isCancelled", isCancelled)
        put("rawIcal", rawIcal); put("isLocalOnly", isLocalOnly); put("organizerEmail", organizerEmail)
    }
    private fun Task.toJson() = JSONObject().apply {
        put("id", id); put("uid", uid); put("accountId", accountId)
        put("calendarPath", calendarPath); put("etag", etag); put("title", title)
        put("description", description); put("assignedPersonId", assignedPersonId)
        put("dueMs", dueMs ?: JSONObject.NULL)
        put("isCompleted", isCompleted); put("completedMs", completedMs ?: JSONObject.NULL)
        put("priority", priority.name); put("starsEarned", starsEarned)
        put("sortOrder", sortOrder); put("isLocalOnly", isLocalOnly); put("listId", listId)
        put("isChore", isChore)
    }
    private fun CheckList.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("colorHex", colorHex); put("sortOrder", sortOrder)
    }
    private fun CheckListItem.toJson() = JSONObject().apply {
        put("id", id); put("listId", listId); put("text", text)
        put("isChecked", isChecked); put("sortOrder", sortOrder)
    }
private fun MealPlan.toJson() = JSONObject().apply {
        put("dateIso", dateIso); put("slot", slot.name)
        put("title", title); put("notes", notes); put("personIds", personIds)
    }
    private fun Reward.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("emoji", emoji)
        put("starCost", starCost); put("description", description)
        put("isEnabled", isEnabled); put("sortOrder", sortOrder)
    }
    private fun RedeemedReward.toJson() = JSONObject().apply {
        put("id", id); put("rewardId", rewardId); put("rewardName", rewardName)
        put("rewardEmoji", rewardEmoji); put("personId", personId)
        put("cost", cost); put("redeemedAtMs", redeemedAtMs); put("note", note)
    }

    // ── JSON deserialization ──────────────────────────────────
    private fun personFromJson(j: JSONObject) = Person(
        id = j.optLong("id"), name = j.optString("name"),
        colorHex = j.optString("colorHex"), initial = j.optString("initial"),
        isDefault = j.optBoolean("isDefault"), sortOrder = j.optInt("sortOrder"),
        role = try { PersonRole.valueOf(j.optString("role")) } catch (_: Exception) { PersonRole.PARENT },
        caregiverPersonId = j.optLong("caregiverPersonId"), email = j.optString("email")
    )
    private fun accountFromJson(j: JSONObject) = CalendarAccount(
        id = j.optLong("id"), displayName = j.optString("displayName"),
        accountType = try { AccountType.valueOf(j.optString("accountType")) } catch (_: Exception) { AccountType.CALDAV },
        serverUrl = j.optString("serverUrl"), username = j.optString("username"),
        passwordEncrypted = j.optString("passwordEncrypted"), calendarPath = j.optString("calendarPath"),
        colorHex = j.optString("colorHex"), enabled = j.optBoolean("enabled", true),
        lastSyncMs = j.optLong("lastSyncMs"), syncIntervalMinutes = j.optInt("syncIntervalMinutes", 30),
        ctag = j.optString("ctag"), syncFailCount = j.optInt("syncFailCount"),
        syncBackoffUntil = j.optLong("syncBackoffUntil")
    )
    private fun eventFromJson(j: JSONObject) = CalendarEvent(
        id = j.optLong("id"), uid = j.optString("uid"), accountId = j.optLong("accountId"),
        calendarPath = j.optString("calendarPath"), etag = j.optString("etag"),
        title = j.optString("title"), description = j.optString("description"),
        location = j.optString("location"), startMs = j.optLong("startMs"),
        endMs = j.optLong("endMs"), isAllDay = j.optBoolean("isAllDay"),
        recurrenceRule = j.optString("recurrenceRule"), colorHex = j.optString("colorHex"),
        personIds = j.optString("personIds"), isCountdown = j.optBoolean("isCountdown"),
        reminderMinutes = j.optInt("reminderMinutes", -1), isCancelled = j.optBoolean("isCancelled"),
        rawIcal = j.optString("rawIcal"), isLocalOnly = j.optBoolean("isLocalOnly"),
        organizerEmail = j.optString("organizerEmail")
    )
    private fun taskFromJson(j: JSONObject) = Task(
        id = j.optLong("id"), uid = j.optString("uid"), accountId = j.optLong("accountId"),
        calendarPath = j.optString("calendarPath"), etag = j.optString("etag"),
        title = j.optString("title"), description = j.optString("description"),
        assignedPersonId = j.optLong("assignedPersonId"), dueMs = optLongOrNull(j, "dueMs"),
        isCompleted = j.optBoolean("isCompleted"), completedMs = optLongOrNull(j, "completedMs"),
        priority = try { TaskPriority.valueOf(j.optString("priority")) } catch (_: Exception) { TaskPriority.NORMAL },
        starsEarned = j.optInt("starsEarned"), sortOrder = j.optInt("sortOrder"),
        isLocalOnly = j.optBoolean("isLocalOnly"), listId = j.optLong("listId"),
        isChore = j.optBoolean("isChore", false)
    )
    private fun listFromJson(j: JSONObject) = CheckList(
        id = j.optLong("id"), name = j.optString("name"),
        colorHex = j.optString("colorHex"), sortOrder = j.optInt("sortOrder")
    )
    private fun listItemFromJson(j: JSONObject) = CheckListItem(
        id = j.optLong("id"), listId = j.optLong("listId"),
        text = j.optString("text"), isChecked = j.optBoolean("isChecked"),
        sortOrder = j.optInt("sortOrder")
    )
    private fun mealFromJson(j: JSONObject) = MealPlan(
        dateIso = j.optString("dateIso"),
        slot = try { MealSlot.valueOf(j.optString("slot")) } catch (_: Exception) { MealSlot.BREAKFAST },
        title = j.optString("title"), notes = j.optString("notes"),
        personIds = j.optString("personIds")
    )
    private fun rewardFromJson(j: JSONObject) = Reward(
        id = j.optLong("id"), name = j.optString("name"),
        emoji = j.optString("emoji", "🎁"), starCost = j.optInt("starCost"),
        description = j.optString("description"),
        isEnabled = j.optBoolean("isEnabled", true),
        sortOrder = j.optInt("sortOrder")
    )
    private fun redeemedFromJson(j: JSONObject) = RedeemedReward(
        id = j.optLong("id"), rewardId = j.optLong("rewardId"),
        rewardName = j.optString("rewardName"),
        rewardEmoji = j.optString("rewardEmoji", "🎁"),
        personId = j.optLong("personId"), cost = j.optInt("cost"),
        redeemedAtMs = j.optLong("redeemedAtMs"),
        note = j.optString("note")
    )
    private fun optLongOrNull(j: JSONObject, key: String): Long? =
        if (j.isNull(key)) null else j.optLong(key)
}