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
    private val encryptor: EncryptedPassword
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
                    // Not a success. Discovery swallows its own errors and
                    // returns an empty list for a rejected password, an
                    // unreachable host and a genuinely empty account alike.
                    // Reporting success here reset the failure counter, stamped
                    // lastSyncMs and showed "Synced" while nothing had synced,
                    // so the account never backed off.
                    Log.w(TAG, "No calendars discovered for ${account.displayName}")
                    return@withContext SyncResult(
                        accountId = account.id,
                        accountName = account.displayName,
                        eventsImported = 0,
                        tasksImported = 0,
                        calendarsExamined = 0,
                        error = "No calendars found — check the server URL and password"
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
                // A calendar that did not answer is not an empty calendar. One
                // failed PROPFIND in a multi-calendar account must not authorise
                // deleting that calendar's cached events.
                var anyCalendarFailed = false
                var eventsImported = 0
                var tasksImported = 0

                for (cal in allCalendars) {
                    val serverEtags = client.getETagList(cal.path)
                    if (serverEtags == null) {
                        Log.w(TAG, "Calendar ${cal.path} did not answer — skipping it")
                        anyCalendarFailed = true
                        continue
                    }
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

                // Deletion pass: drop local rows absent from every server calendar.
                // Only when every calendar answered: a partial or transient view
                // must not wipe the cache.
                if (masterServerHrefs.isNotEmpty() && !anyCalendarFailed) {
                    // Re-read from the DB, not from the pre-pass snapshot: an
                    // event whose href changed on the server was re-inserted
                    // above under its new path, and pruning the snapshot would
                    // delete that row by id (the snapshot still holds the old
                    // path, which is no longer in masterServerHrefs).
                    for (event in db.calendarEventDao().getByAccount(account.id)) {
                        if (event.calendarPath.isNotBlank()
                            && event.calendarPath !in masterServerHrefs
                        ) {
                            db.calendarEventDao().delete(event)
                        }
                    }
                    for (task in db.taskDao().getByAccount(account.id)) {
                        if (task.calendarPath.isNotBlank()
                            && task.calendarPath !in masterServerHrefs
                        ) {
                            db.taskDao().delete(task)
                        }
                    }
                }

                // Record what the server told us. Narrow write: the pass
                // outcome (lastSyncMs, fail count, backoff) belongs to the
                // caller and must not be written from here, and nothing else
                // in the row may be reverted from a pre-pass snapshot.
                val primaryPath = allCalendars.first().path
                val newCtag = client.getCTag(primaryPath)
                db.calendarAccountDao().updateSyncTarget(
                    id           = account.id,
                    ctag         = if (newCtag.isNotBlank()) newCtag else account.ctag,
                    calendarPath = primaryPath
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

    /**
     * Persist the outcome of one pass. The bookkeeping columns are written
     * narrow so a pass can never revert the calendarPath/ctag the engine just
     * stored, and a failed pass does not claim a successful lastSyncMs.
     */
    suspend fun recordOutcome(account: CalendarAccount, result: SyncResult) {
        val dao = db.calendarAccountDao()
        if (result.isSuccess) {
            dao.updateSyncState(
                id               = account.id,
                lastSyncMs       = System.currentTimeMillis(),
                syncFailCount    = 0,
                syncBackoffUntil = 0L
            )
        } else {
            val failCount = account.syncFailCount + 1
            dao.updateSyncState(
                id               = account.id,
                lastSyncMs       = account.lastSyncMs,
                syncFailCount    = failCount,
                syncBackoffUntil = System.currentTimeMillis() +
                    CalDAVSyncWorker.backoffMs(failCount)
            )
        }
    }
}
