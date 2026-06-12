package com.openlight.cal.data.repository

import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.mealie.MealieApi
import com.openlight.cal.data.model.*
import com.openlight.cal.data.preferences.EncryptedPassword
import com.openlight.cal.data.sync.CalDAVClient
import com.openlight.cal.data.sync.CalDAVSyncWorker
import com.openlight.cal.data.sync.CalDAVClientFactory
import com.openlight.cal.data.sync.ICalParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.*

// ═════════════════════════════════════════════════════════════
// Shared CalDAV sync helpers — eliminates duplicated client
// creation + putIcs + deleteIcs patterns across repositories.
// ═════════════════════════════════════════════════════════════

/** Push an ICS resource to a CalDAV account. Handles client creation,
 *  credential decryption, URL construction, and ETag management.
 *  @param onEtagUpdate called with (newEtag, calendarPath) on success.
 */
private suspend fun pushIcsToCalDAV(
    db: AppDatabase,
    encryptor: EncryptedPassword,
    accountId: Long?,
    uid: String,
    oldEtag: String,
    ical: String,
    onEtagUpdate: suspend (etag: String, calendarPath: String) -> Unit
) {
    if (accountId == null || accountId <= 0) return
    val account = db.calendarAccountDao().getById(accountId) ?: return
    if (account.calendarPath.isBlank()) return

    val path = "${account.calendarPath.trimEnd('/')}/$uid.ics"
    val client = CalDAVClientFactory.create(
        account.serverUrl,
        account.username,
        encryptor.decrypt(account.passwordEncrypted)
    )
    val newEtag = client.putIcs(path, ical, oldEtag.ifBlank { null })
    if (newEtag != null) {
        onEtagUpdate(newEtag, path)
    }
}

/** Delete an ICS resource from a CalDAV account. */
private suspend fun deleteIcsFromCalDAV(
    db: AppDatabase,
    encryptor: EncryptedPassword,
    accountId: Long,
    calendarPath: String,
    etag: String
) {
    if (calendarPath.isBlank()) return
    val account = db.calendarAccountDao().getById(accountId) ?: return
    val client = CalDAVClientFactory.create(
        account.serverUrl,
        account.username,
        encryptor.decrypt(account.passwordEncrypted)
    )
    client.deleteIcs(calendarPath, etag.ifBlank { null })
}

// ─────────────────────────────────────────────────────────────
// Calendar Repository
// ─────────────────────────────────────────────────────────────
class CalendarRepository(
    private val db: AppDatabase,
    private val encryptor: EncryptedPassword
) {

    fun getEventsInRange(start: Long, end: Long): Flow<List<CalendarEvent>> =
        db.calendarEventDao().getInRangeFlow(start, end)

    fun getCountdowns(nowMs: Long = System.currentTimeMillis()): Flow<List<CalendarEvent>> =
        db.calendarEventDao().getCountdownsFlow(nowMs)

    suspend fun saveEvent(event: CalendarEvent, accountId: Long?): CalendarEvent {
        return withContext(Dispatchers.IO) {
            val uid    = event.uid.ifBlank { ICalParser.generateUid() }
            val toSave = event.copy(uid = uid)
            val id     = db.calendarEventDao().insert(toSave)
            val saved  = toSave.copy(id = id)

            pushIcsToCalDAV(
                db = db, encryptor = encryptor,
                accountId = accountId, uid = uid,
                oldEtag = event.etag,
                ical = ICalParser.serializeEvent(saved),
                onEtagUpdate = { newEtag, path ->
                    db.calendarEventDao().insert(saved.copy(etag = newEtag, calendarPath = path))
                }
            )
            saved
        }
    }

    suspend fun deleteEvent(event: CalendarEvent) {
        withContext(Dispatchers.IO) {
            db.calendarEventDao().delete(event)
            if (event.calendarPath.isNotBlank() && event.accountId > 0) {
                deleteIcsFromCalDAV(db, encryptor, event.accountId, event.calendarPath, event.etag)
            }
        }
    }

    fun getMonthRange(year: Int, month: Int): Pair<Long, Long> {
        val start = LocalDate.of(year, month, 1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end   = LocalDate.of(year, month, 1).plusMonths(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return start to end
    }
}

// ─────────────────────────────────────────────────────────────
// Task Repository
// ─────────────────────────────────────────────────────────────
class TaskRepository(
    private val db: AppDatabase,
    private val encryptor: EncryptedPassword
) {

    fun getAllTasksFlow(): Flow<List<Task>> = db.taskDao().getAllFlow()
    fun getActiveFlow(): Flow<List<Task>>   = db.taskDao().getActiveFlow()
    fun getByPersonFlow(personId: Long): Flow<List<Task>> = db.taskDao().getByPersonFlow(personId)

    suspend fun saveTask(task: Task, accountId: Long? = null): Task {
        return withContext(Dispatchers.IO) {
            val uid    = task.uid.ifBlank { ICalParser.generateUid() }
            val toSave = task.copy(uid = uid)
            val id     = db.taskDao().insert(toSave)
            val saved  = toSave.copy(id = id)

            pushIcsToCalDAV(
                db = db, encryptor = encryptor,
                accountId = accountId, uid = uid,
                oldEtag = task.etag,
                ical = ICalParser.serializeTask(saved),
                onEtagUpdate = { newEtag, path ->
                    db.taskDao().insert(saved.copy(etag = newEtag, calendarPath = path))
                }
            )
            saved
        }
    }

    suspend fun setCompleted(id: Long, done: Boolean) {
        val ts = if (done) System.currentTimeMillis() else null
        db.taskDao().setCompleted(id, done, ts)

        withContext(Dispatchers.IO) {
            val updated = db.taskDao().getById(id) ?: return@withContext
            if (updated.accountId > 0 && updated.calendarPath.isNotBlank()) {
                pushIcsToCalDAV(
                    db = db, encryptor = encryptor,
                    accountId = updated.accountId, uid = updated.uid,
                    oldEtag = updated.etag,
                    ical = ICalParser.serializeTask(updated),
                    onEtagUpdate = { newEtag, _ ->
                        db.taskDao().insert(updated.copy(etag = newEtag))
                    }
                )
            }
        }
    }

    suspend fun deleteTask(task: Task) {
        withContext(Dispatchers.IO) {
            db.taskDao().delete(task)
            if (task.calendarPath.isNotBlank() && task.accountId > 0) {
                deleteIcsFromCalDAV(db, encryptor, task.accountId, task.calendarPath, task.etag)
            }
        }
    }

    suspend fun getActiveTaskCountByPerson(personId: Long): Int = db.taskDao().getActiveTaskCountByPerson(personId)
    suspend fun getActiveChoreCountByPerson(personId: Long): Int = db.taskDao().getActiveChoreCountByPerson(personId)
    suspend fun getActiveTotalCountByPerson(personId: Long): Int = db.taskDao().getActiveTotalCountByPerson(personId)
}

// ─────────────────────────────────────────────────────────────
// Person Repository
// ─────────────────────────────────────────────────────────────
class PersonRepository(private val db: AppDatabase) {

    fun getAllFlow(): Flow<List<Person>> = db.personDao().getAllFlow()

    suspend fun save(person: Person): Long = db.personDao().insert(person)
    suspend fun update(person: Person)     = db.personDao().update(person)
    suspend fun delete(person: Person)     = db.personDao().delete(person)

    suspend fun seedDefaultPeople() {
        if (db.personDao().count() == 0) {
            val defaults = listOf(
                Person(name = "Everyone", colorHex = "#8A8278", isDefault = true, sortOrder = 0),
                Person(name = "Me",       colorHex = "#7E967B", sortOrder = 1)
            )
            defaults.forEach { db.personDao().insert(it) }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Account Repository
// ─────────────────────────────────────────────────────────────
class AccountRepository(
    private val db: AppDatabase,
    private val encryptor: EncryptedPassword
) {

    fun getAllFlow(): Flow<List<CalendarAccount>> = db.calendarAccountDao().getAllFlow()

    suspend fun save(account: CalendarAccount): Long {
        // If account exists and password changed, evict old client
        val existing = db.calendarAccountDao().getById(account.id)
        if (existing != null && existing.passwordEncrypted != account.passwordEncrypted) {
            CalDAVClientFactory.evict(existing.serverUrl, existing.username)
        }
        return db.calendarAccountDao().insert(account)
    }

    suspend fun update(account: CalendarAccount) =
        db.calendarAccountDao().update(account)

    suspend fun delete(account: CalendarAccount) {
        db.calendarAccountDao().delete(account)
        db.calendarEventDao().deleteByAccount(account.id)
    }

    suspend fun testConnection(serverUrl: String, username: String, password: String)
        : List<CalDAVClient.CalendarInfo> {
        val client = CalDAVClientFactory.create(serverUrl, username, password)
        return client.discoverCalendars()
    }
}

// ─────────────────────────────────────────────────────────────
// Recipe Repository
// ─────────────────────────────────────────────────────────────
class RecipeRepository(private val db: AppDatabase) {

    fun getAllFlow(): Flow<List<Recipe>> = db.recipeDao().getAllFlow()
    fun searchFlow(q: String): Flow<List<Recipe>> = db.recipeDao().searchFlow(q)

    suspend fun getLocalFlow(): Flow<List<Recipe>> =
        db.recipeDao().getAllFlow()
            .map { it.filter { it.mealieId.isBlank() } }

    suspend fun getSyncedFlow(): Flow<List<Recipe>> =
        db.recipeDao().getAllFlow()
            .map { it.filter { it.mealieId.isNotBlank() } }

    suspend fun saveLocal(recipe: Recipe): Long =
        withContext(Dispatchers.IO) {
            db.recipeDao().upsert(recipe.copy(createdAtMs = System.currentTimeMillis(), updatedAtMs = System.currentTimeMillis()))
        }

    suspend fun delete(recipe: Recipe) =
        withContext(Dispatchers.IO) {
            db.recipeDao().delete(recipe)
        }

    /**
     * Push a local recipe to the Mealie server (one-way publish).
     * On success the recipe's mealieId and mealieLastPushMs are updated.
     * On failure the recipe is unchanged and the exception is returned.
     */
    suspend fun pushToMealie(recipe: Recipe, serverUrl: String, token: String): Result<Recipe> =
        withContext(Dispatchers.IO) {
            try {
                val api = MealieApi(serverUrl, token)
                val slug = api.createRecipe(
                    name        = recipe.name,
                    description = recipe.description,
                    ingredients = parseRecipeJsonList(recipe.ingredientsJson),
                    instructions = parseRecipeJsonList(recipe.instructionsJson),
                    recipeYield  = if (recipe.servings > 0) "${recipe.servings} servings" else "",
                    totalTime    = formatRecipeTime(recipe.prepTimeMinutes, recipe.cookTimeMinutes)
                )
                if (slug == null) {
                    Result.failure(Exception("Mealie rejected the recipe"))
                } else {
                    val updated = recipe.copy(
                        mealieId         = slug,
                        mealieLastPushMs = System.currentTimeMillis(),
                        updatedAtMs      = System.currentTimeMillis()
                    )
                    db.recipeDao().upsert(updated)
                    Result.success(updated)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

private fun parseRecipeJsonList(json: String): List<String> {
    return try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun formatRecipeTime(prepMin: Int, cookMin: Int): String {
    val total = prepMin + cookMin
    if (total <= 0) return ""
    return if (total >= 60) {
        "${total / 60}h ${total % 60}m"
    } else {
        "${total} minutes"
    }
}

// ─────────────────────────────────────────────────────────────
// LABELS — §11 color-coded person categories
// ─────────────────────────────────────────────────────────────
class LabelRepository(private val db: AppDatabase) {

    val labelsFlow: Flow<List<Label>> = db.labelDao().getAllFlow()

    suspend fun getAll(): List<Label> = db.labelDao().getAll()

    suspend fun save(label: Label): Long = db.labelDao().upsert(label)

    suspend fun delete(label: Label) = db.labelDao().delete(label)

    /** Get labels assigned to a specific person (Flow for reactive UI). */
    fun getLabelsForPersonFlow(personId: Long): Flow<List<Label>> =
        db.labelDao().getLabelsForPersonFlow(personId)

    suspend fun getLabelsForPerson(personId: Long): List<Label> =
        db.labelDao().getLabelsForPerson(personId)

    suspend fun assignLabel(personId: Long, labelId: Long) =
        db.labelDao().assignLabel(PersonLabel(personId = personId, labelId = labelId))

    suspend fun unassignLabel(personId: Long, labelId: Long) =
        db.labelDao().unassignLabel(personId, labelId)

    suspend fun unassignAllLabels(personId: Long) =
        db.labelDao().unassignAllLabels(personId)

    suspend fun labelUsageCount(labelId: Long): Int =
        db.labelDao().labelUsageCount(labelId)
}
