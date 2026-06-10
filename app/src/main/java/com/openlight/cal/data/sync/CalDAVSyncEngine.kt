package com.openlight.cal.data.sync

import android.util.Log
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.model.CalendarAccount
import com.openlight.cal.data.model.CalendarEvent
import com.openlight.cal.data.model.Task
import com.openlight.cal.data.preferences.EncryptedPassword
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Shared CalDAV sync engine used by both the background WorkManager worker
 * and the manual "Sync now" path in Settings.
 *
 * Owns the HTTP/database/sync rules. Neither UI nor Worker should do raw
 * CalDAV plumbing — they call this and react to the result.
 */
class CalDAVSyncEngine(
    private val db: AppDatabase,
    private val encryptor: EncryptedPassword,
    private val nowMs: Long = System.currentTimeMillis()
) {

    data class SyncResult(
        val accountId: Long,
        val accountName: String,
        val eventsImported: Int,
        val tasksImported: Int,
        val calendarsExamined: Int,
        val error: String? = null
    ) {
        val isSuccess: Boolean get() = error == null
    }

    suspend fun syncAccount(account: CalendarAccount): SyncResult =
        withContext(Dispatchers.IO) {
            val TAG = "CalDAVSyncEngine"
            return@withContext try {
                val pass = encryptor.decrypt(account.passwordEncrypted)
                val client = CalDAVClientFactory.create(account.serverUrl, account.username, pass)

                val allCalendars = client.discoverCalendars()
                if (allCalendars.isEmpty()) {
                    Log.w(TAG, "No calendars discovered for ${account.displayName}")
                    return@withContext SyncResult(
                        accountId = account.id,
                        accountName = account.displayName,
                        eventsImported = 0,
                        tasksImported = 0,
                        calendarsExamined = 0
                    )
                }

                // Pre-fetch local state keyed by href
                val localEvents = db.calendarEventDao().getByAccount(account.id)
                val localEventPaths = localEvents.associateBy { it.calendarPath }
                val localTasks = db.taskDao().getByAccount(account.id)
                val localTaskPaths = localTasks.associateBy { it.calendarPath }

                val emailToPersonId = db.personDao().getAll()
                    .filter { it.email.isNotBlank() }
                    .associateBy { it.email.lowercase() }

                val masterServerHrefs = mutableSetOf<String>()
                var eventsImported = 0
                var tasksImported = 0

                for (cal in allCalendars) {
                    val serverEtags = client.getETagList(cal.path)
                    masterServerHrefs.addAll(serverEtags.map { it.href })
                    if (serverEtags.isEmpty()) continue

                    val toFetch = serverEtags.filter { entry ->
                        val eventMatch = localEventPaths[entry.href]?.etag == entry.etag
                        val taskMatch  = localTaskPaths[entry.href]?.etag == entry.etag
                        !eventMatch && !taskMatch
                    }.map { it.href }

                    // Fetch ICS files concurrently in batches of 6 to avoid
                    // overwhelming the CalDAV server. Within each batch all
                    // fetches run in parallel via async/awaitAll.
                    coroutineScope {
                        toFetch.chunked(6).forEach { batch ->
                            val fetched: List<Pair<String, CalDAVClient.IcsResource?>> =
                                batch.map { href -> async { href to client.fetchIcs(href) } }
                                    .awaitAll()
                            for ((href, res) in fetched) {
                                if (res == null) continue
                                val parsed = ICalParser.parse(res.ical, account.id, res.href)

                                for (event in parsed.events) {
                                    val existing = db.calendarEventDao().getByUid(event.uid, account.id)
                                    val personId = if (
                                        event.organizerEmail.isNotBlank()
                                        && existing?.personIds.isNullOrBlank()
                                    ) {
                                        emailToPersonId[event.organizerEmail.lowercase()]?.id
                                    } else null

                                    db.calendarEventDao().insert(
                                        event.copy(
                                            id           = existing?.id ?: 0,
                                            etag         = res.etag,
                                            calendarPath = res.href,
                                            colorHex     = existing?.colorHex
                                                ?.takeIf { it.isNotBlank() }
                                                ?: account.colorHex,
                                            personIds    = personId?.toString()
                                                ?: (existing?.personIds ?: "")
                                        )
                                    )
                                    eventsImported++
                                }

                                for (task in parsed.tasks) {
                                    val existing = db.taskDao().getByUid(task.uid, account.id)
                                    db.taskDao().insert(
                                        task.copy(
                                            id           = existing?.id ?: 0,
                                            etag         = res.etag,
                                            calendarPath = res.href
                                        )
                                    )
                                    tasksImported++
                                }
                            }
                        }
                    }
                }

                // Deletion pass: drop local rows absent from every server calendar
                for (event in localEvents) {
                    if (event.calendarPath.isNotBlank()
                        && event.calendarPath !in masterServerHrefs
                    ) {
                        db.calendarEventDao().delete(event)
                    }
                }
                for (task in localTasks) {
                    if (task.calendarPath.isNotBlank()
                        && task.calendarPath !in masterServerHrefs
                    ) {
                        db.taskDao().delete(task)
                    }
                }

                // Update ctag + primary calendar path + lastSyncMs
                val primaryPath = allCalendars.first().path
                val newCtag = client.getCTag(primaryPath)
                db.calendarAccountDao().update(
                    account.copy(
                        ctag         = if (newCtag.isNotBlank()) newCtag else account.ctag,
                        calendarPath = primaryPath,
                        lastSyncMs   = nowMs
                    )
                )

                SyncResult(
                    accountId       = account.id,
                    accountName     = account.displayName,
                    eventsImported  = eventsImported,
                    tasksImported   = tasksImported,
                    calendarsExamined = allCalendars.size
                )
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed for ${account.displayName}: ${e.message}", e)
                SyncResult(
                    accountId       = account.id,
                    accountName     = account.displayName,
                    eventsImported  = 0,
                    tasksImported   = 0,
                    calendarsExamined = 0,
                    error           = e.message ?: e.toString()
                )
            }
        }
}
