package com.openlight.cal.data.repository

import android.util.Base64
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.model.*
import com.openlight.cal.data.sync.CalDAVClient
import com.openlight.cal.data.sync.CalDAVSyncWorker
import com.openlight.cal.data.sync.ICalParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.*

// ─────────────────────────────────────────────────────────────
// Calendar Repository
// ─────────────────────────────────────────────────────────────
class CalendarRepository(private val db: AppDatabase) {

    fun getEventsInRange(start: Long, end: Long): Flow<List<CalendarEvent>> =
        db.calendarEventDao().getInRangeFlow(start, end)

    fun getCountdowns(nowMs: Long = System.currentTimeMillis()): Flow<List<CalendarEvent>> =
        db.calendarEventDao().getCountdownsFlow(nowMs)

    suspend fun saveEvent(event: CalendarEvent, accountId: Long?): CalendarEvent {
        return withContext(Dispatchers.IO) {
            val uid      = event.uid.ifBlank { ICalParser.generateUid() }
            val toSave   = event.copy(uid = uid)
            val id       = db.calendarEventDao().insert(toSave)
            val saved    = toSave.copy(id = id)

            // Push to CalDAV if account is set
            if (accountId != null && accountId > 0) {
                val account = db.calendarAccountDao().getById(accountId)
                if (account != null) {
                    val ical = ICalParser.serializeEvent(saved)
                    val path = "${account.calendarPath.trimEnd('/')}/$uid.ics"
                    val client = CalDAVClient(account.serverUrl, account.username,
                        String(Base64.decode(account.passwordEncrypted, Base64.DEFAULT)))
                    val newEtag = client.putIcs(path, ical, if (event.etag.isBlank()) null else event.etag)
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
                val client  = CalDAVClient(account.serverUrl, account.username,
                    String(Base64.decode(account.passwordEncrypted, Base64.DEFAULT)))
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
class TaskRepository(private val db: AppDatabase) {

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
                    val client = CalDAVClient(account.serverUrl, account.username,
                        String(Base64.decode(account.passwordEncrypted, Base64.DEFAULT)))
                    val newEtag = client.putIcs(path, ical, if (task.etag.isBlank()) null else task.etag)
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
    }

    suspend fun deleteTask(task: Task) {
        withContext(Dispatchers.IO) {
            db.taskDao().delete(task)
            if (task.calendarPath.isNotBlank() && task.accountId > 0) {
                val account = db.calendarAccountDao().getById(task.accountId) ?: return@withContext
                val client  = CalDAVClient(account.serverUrl, account.username,
                    String(Base64.decode(account.passwordEncrypted, Base64.DEFAULT)))
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
                Person(name = "Everyone", colorHex = "#607D8B", isDefault = true, sortOrder = 0),
                Person(name = "Me",       colorHex = "#2196F3", sortOrder = 1)
            )
            defaults.forEach { db.personDao().insert(it) }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Account Repository
// ─────────────────────────────────────────────────────────────
class AccountRepository(private val db: AppDatabase) {

    fun getAllFlow(): Flow<List<CalendarAccount>> = db.calendarAccountDao().getAllFlow()

    suspend fun save(account: CalendarAccount): Long =
        db.calendarAccountDao().insert(account)

    suspend fun update(account: CalendarAccount) =
        db.calendarAccountDao().update(account)

    suspend fun delete(account: CalendarAccount) {
        db.calendarAccountDao().delete(account)
        db.calendarEventDao().deleteByAccount(account.id)
    }

    suspend fun testConnection(serverUrl: String, username: String, password: String): List<CalDAVClient.CalendarInfo> {
        val client = CalDAVClient(serverUrl, username, password)
        return client.discoverCalendars()
    }
}
