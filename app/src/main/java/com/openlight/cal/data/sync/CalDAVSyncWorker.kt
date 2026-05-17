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

        fun schedulePeriodicSync(context: Context, intervalMinutes: Long = 30) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<CalDAVSyncWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
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

        var anyError = false
        for (account in accounts) {
            try {
                syncAccount(account, db)
                db.calendarAccountDao().update(
                    account.copy(lastSyncMs = System.currentTimeMillis())
                )
                Log.i(TAG, "Synced account: ${account.displayName}")
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed for ${account.displayName}: ${e.message}")
                anyError = true
            }
        }
        if (anyError) Result.retry() else Result.success()
    }

    private suspend fun syncAccount(account: CalendarAccount, db: AppDatabase) {
        val encryptor = EncryptedPassword(applicationContext)
        val pass      = encryptor.decrypt(account.passwordEncrypted)
        val client    = CalDAVClient(account.serverUrl, account.username, pass)

        // If calendar path not discovered yet, discover it
        val path = account.calendarPath.ifBlank {
            val cals = client.discoverCalendars()
            cals.firstOrNull()?.path ?: return
        }

        // Check ctag – if unchanged, skip
        val newCtag = client.getCTag(path)
        if (newCtag.isNotBlank() && newCtag == account.ctag) {
            Log.d(TAG, "No changes for ${account.displayName} (ctag match)")
            return
        }

        // Get server ETag list
        val serverEtags  = client.getETagList(path)
        val localEvents  = db.calendarEventDao().getByAccount(account.id)
        val localByHref  = localEvents.associateBy { it.calendarPath }
        val localTasks   = db.taskDao().getByAccount(account.id)
        val localTasksByHref = localTasks.associateBy { it.calendarPath }

        // Find new/changed items — fetch if no local resource matches the etag
        val toFetch = serverEtags.filter { (href, etag) ->
            val eventMatch = localByHref[href]?.etag == etag
            val taskMatch  = localTasksByHref[href]?.etag == etag
            !eventMatch && !taskMatch
        }.map { it.href }

        // Fetch changed items via individual GET (not multiGet REPORT - SOGo returns 501)
        for (href in toFetch) {
            val res = client.fetchIcs(href) ?: continue
            val parsed = ICalParser.parse(res.ical, account.id, res.href)
            // Upsert events
            for (event in parsed.events) {
                val existing = db.calendarEventDao().getByUid(event.uid, account.id)
                db.calendarEventDao().insert(
                    event.copy(
                        id           = existing?.id ?: 0,
                        etag         = res.etag,
                        calendarPath = res.href
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

        // Delete events removed from server
        val serverHrefs = serverEtags.map { it.href }.toSet()
        for (event in localEvents) {
            if (event.calendarPath !in serverHrefs && event.calendarPath.isNotBlank()) {
                db.calendarEventDao().delete(event)
            }
        }

        // Delete tasks removed from server
        for (task in localTasks) {
            if (task.calendarPath !in serverHrefs && task.calendarPath.isNotBlank()) {
                db.taskDao().delete(task)
            }
        }

        // Update ctag
        if (newCtag.isNotBlank()) {
            db.calendarAccountDao().update(account.copy(ctag = newCtag, calendarPath = path))
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
