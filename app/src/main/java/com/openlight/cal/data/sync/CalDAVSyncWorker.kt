package com.openlight.cal.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.model.CalendarAccount
import com.openlight.cal.data.model.CalendarEvent
import com.openlight.cal.data.model.Task
import com.openlight.cal.data.preferences.EncryptedPassword
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class CalDAVSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "CalDAVSyncWorker"
        const val WORK_NAME_PERIODIC = "caldav_periodic_sync"
        const val WORK_NAME_ONETIME  = "caldav_onetime_sync"
        const val KEY_ACCOUNT_ID     = "account_id"  // -1 = sync all

        // Per-account exponential backoff: 5min, 10min, 20min, 40min, 80min, 160min, 300min (capped)
        private const val BACKOFF_BASE_MS = 300_000L     // 5 minutes
        private const val BACKOFF_MAX_MS  = 18_000_000L   // 5 hours
        private const val BACKOFF_MAX_SHIFT = 6           // 2^6 = 64, applied to 5min base

        /** Calculate backoff delay in ms for a given consecutive failure count. */
        fun backoffMs(failCount: Int): Long {
            val shift = failCount.coerceIn(0, BACKOFF_MAX_SHIFT)
            return minOf(BACKOFF_BASE_MS shl shift, BACKOFF_MAX_MS)
        }

        fun schedulePeriodicSync(context: Context, intervalMinutes: Long = 30) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<CalDAVSyncWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun syncNow(context: Context, accountId: Long = -1L) {
            val data = workDataOf(KEY_ACCOUNT_ID to accountId)
            val req  = OneTimeWorkRequestBuilder<CalDAVSyncWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONETIME,
                ExistingWorkPolicy.REPLACE,
                req
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db          = AppDatabase.getInstance(applicationContext)
        val accountId   = inputData.getLong(KEY_ACCOUNT_ID, -1L)
        val accounts: List<CalendarAccount> = if (accountId == -1L) {
            db.calendarAccountDao().getAll().filter { it.enabled }
        } else {
            listOfNotNull(db.calendarAccountDao().getById(accountId))
        }

        val now = System.currentTimeMillis()

        for (account in accounts) {
            // Skip accounts in backoff period (but always allow manual sync)
            if (account.syncBackoffUntil > now && accountId == -1L) {
                Log.i(TAG, "Skipping ${account.displayName} (backoff until ${account.syncBackoffUntil})")
                continue
            }

            try {
                syncAccount(account, db)
                // Success: reset failure state
                if (account.syncFailCount != 0 || account.syncBackoffUntil != 0L) {
                    db.calendarAccountDao().update(
                        account.copy(
                            lastSyncMs       = now,
                            syncFailCount    = 0,
                            syncBackoffUntil = 0L
                        )
                    )
                } else {
                    db.calendarAccountDao().update(
                        account.copy(lastSyncMs = now)
                    )
                }
                Log.i(TAG, "Synced account: ${account.displayName}")
            } catch (e: Exception) {
                val newFailCount = account.syncFailCount + 1
                val delayMs = backoffMs(newFailCount)
                val backoffUntil = now + delayMs
                Log.e(TAG, "Sync failed for ${account.displayName} " +
                        "(fail #$newFailCount, backoff ${delayMs / 60_000}min): ${e.message}")
                db.calendarAccountDao().update(
                    account.copy(
                        syncFailCount    = newFailCount,
                        syncBackoffUntil = backoffUntil
                    )
                )
            }
        }
        // Always return success — per-account backoff is handled by the data model.
        // WorkManager retry would resync ALL accounts, which is wasteful.
        Result.success()
    }

    private suspend fun syncAccount(account: CalendarAccount, db: AppDatabase) {
        val encryptor = EncryptedPassword(applicationContext)
        val pass      = encryptor.decrypt(account.passwordEncrypted)
        val client    = CalDAVClient(account.serverUrl, account.username, pass)

        // Always run discovery to find ALL calendars (personal + inbox + shared)
        val allCalendars = client.discoverCalendars()
        if (allCalendars.isEmpty()) {
            Log.w(TAG, "No calendars discovered for ${account.displayName}")
            return
        }

        // Pre-fetch local state
        val localEvents = db.calendarEventDao().getByAccount(account.id)
        val localByHref = localEvents.associateBy { it.calendarPath }
        val localTasks  = db.taskDao().getByAccount(account.id)
        val localTasksByHref = localTasks.associateBy { it.calendarPath }

        // Build email→personId lookup for organizer matching
        val emailToPersonId = db.personDao().getAll()
            .filter { it.email.isNotBlank() }
            .associateBy { it.email.lowercase() }

        val masterServerHrefs = mutableSetOf<String>()

        for (cal in allCalendars) {
            Log.d(TAG, "Syncing calendar: ${cal.displayName} (${cal.path})")

            // Get server ETag list for this calendar
            val serverEtags = client.getETagList(cal.path)
            masterServerHrefs.addAll(serverEtags.map { it.href })

            if (serverEtags.isEmpty()) continue

            // Find new/changed items
            val toFetch = serverEtags.filter { (href, etag) ->
                val eventMatch = localByHref[href]?.etag == etag
                val taskMatch  = localTasksByHref[href]?.etag == etag
                !eventMatch && !taskMatch
            }.map { it.href }

            // Fetch changed items via individual GET
            for (href in toFetch) {
                val res = client.fetchIcs(href) ?: continue
                val parsed = ICalParser.parse(res.ical, account.id, res.href)
                // Upsert events
                for (event in parsed.events) {
                    val existing = db.calendarEventDao().getByUid(event.uid, account.id)
                    // Match organizer email to a known Person
                    val personId = if (event.organizerEmail.isNotBlank() && existing?.personIds.isNullOrBlank()) {
                        emailToPersonId[event.organizerEmail.lowercase()]?.id
                    } else null
                    db.calendarEventDao().insert(
                        event.copy(
                            id           = existing?.id ?: 0,
                            etag         = res.etag,
                            calendarPath = res.href,
                            colorHex     = existing?.colorHex?.takeIf { it.isNotBlank() } ?: account.colorHex,
                            personIds    = personId?.toString() ?: (existing?.personIds ?: "")
                        )
                    )
                }
                // Upsert tasks
                for (task in parsed.tasks) {
                    val existing = db.taskDao().getByUid(task.uid, account.id)
                    db.taskDao().insert(
                        task.copy(
                            id           = existing?.id ?: 0,
                            etag         = res.etag,
                            calendarPath = res.href
                        )
                    )
                }
            }
        }

        // Single deletion pass: remove items not present on ANY server calendar
        for (event in localEvents) {
            if (event.calendarPath.isNotBlank() && event.calendarPath !in masterServerHrefs) {
                db.calendarEventDao().delete(event)
            }
        }
        for (task in localTasks) {
            if (task.calendarPath.isNotBlank() && task.calendarPath !in masterServerHrefs) {
                db.taskDao().delete(task)
            }
        }

        // Update ctag from primary calendar
        val primaryPath = allCalendars.first().path
        val newCtag = client.getCTag(primaryPath)
        if (newCtag.isNotBlank()) {
            db.calendarAccountDao().update(account.copy(ctag = newCtag, calendarPath = primaryPath))
        } else {
            db.calendarAccountDao().update(account.copy(calendarPath = primaryPath))
        }
    }
}

// Boot receiver to reschedule sync after reboot
class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
        if (intent.action == android.content.Intent.ACTION_BOOT_COMPLETED) {
            CalDAVSyncWorker.schedulePeriodicSync(context)
        }
    }
}
