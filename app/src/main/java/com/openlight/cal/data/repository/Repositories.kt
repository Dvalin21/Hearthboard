package com.openlight.cal.data.repository

import com.openlight.cal.data.db.AppDatabase
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

            if (accountId != null && accountId > 0) {
                val account = db.calendarAccountDao().getById(accountId)
                if (account != null) {
                    val ical = ICalParser.serializeEvent(saved)
                    val path = "${account.calendarPath.trimEnd('/')}/$uid.ics"
                    val client = CalDAVClientFactory.create(
                        account.serverUrl,
                        account.username,
                        encryptor.decrypt(account.passwordEncrypted)
                    )
                    val newEtag = client.putIcs(path, ical,
                        if (event.etag.isBlank()) null else event.etag)
                    if (newEtag != null) {
                        db.calendarEventDao().insert(saved.copy(etag = newEtag, calendarPath = path))
                    }
                }
            }
            saved
        }
    }

    suspend fun deleteEvent(event: CalendarEvent) {
        withContext(Dispatchers.IO) {
            db.calendarEventDao().delete(event)
            if (event.calendarPath.isNotBlank() && event.accountId > 0) {
                val account = db.calendarAccountDao().getById(event.accountId) ?: return@withContext
                val client  = CalDAVClientFactory.create(
                    account.serverUrl,
                    account.username,
                    encryptor.decrypt(account.passwordEncrypted)
                )
                client.deleteIcs(event.calendarPath, event.etag.ifBlank { null })
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

            if (accountId != null && accountId > 0) {
                val account = db.calendarAccountDao().getById(accountId)
                if (account != null && account.calendarPath.isNotBlank()) {
                    val ical   = ICalParser.serializeTask(saved)
                    val path   = "${account.calendarPath.trimEnd('/')}/$uid.ics"
                    val client = CalDAVClientFactory.create(
                        account.serverUrl,
                        account.username,
                        encryptor.decrypt(account.passwordEncrypted)
                    )
                    val newEtag = client.putIcs(path, ical,
                        if (task.etag.isBlank()) null else task.etag)
                    if (newEtag != null) {
                        db.taskDao().insert(saved.copy(etag = newEtag, calendarPath = path))
                    }
                }
            }
            saved
        }
    }

    suspend fun setCompleted(id: Long, done: Boolean) {
        val ts = if (done) System.currentTimeMillis() else null
        db.taskDao().setCompleted(id, done, ts)

        withContext(Dispatchers.IO) {
            val updated = db.taskDao().getById(id) ?: return@withContext
            if (updated.accountId > 0 && updated.calendarPath.isNotBlank()) {
                val account = db.calendarAccountDao().getById(updated.accountId) ?: return@withContext
                val client  = CalDAVClientFactory.create(
                    account.serverUrl,
                    account.username,
                    encryptor.decrypt(account.passwordEncrypted)
                )
                val ical    = ICalParser.serializeTask(updated)
                val newEtag = client.putIcs(updated.calendarPath, ical, updated.etag.ifBlank { null })
                if (newEtag != null) {
                    db.taskDao().insert(updated.copy(etag = newEtag))
                }
            }
        }
    }

    suspend fun deleteTask(task: Task) {
        withContext(Dispatchers.IO) {
            db.taskDao().delete(task)
            if (task.calendarPath.isNotBlank() && task.accountId > 0) {
                val account = db.calendarAccountDao().getById(task.accountId) ?: return@withContext
                val client  = CalDAVClientFactory.create(
                    account.serverUrl,
                    account.username,
                    encryptor.decrypt(account.passwordEncrypted)
                )
                client.deleteIcs(task.calendarPath, task.etag.ifBlank { null })
            }
        }
    }
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
}
