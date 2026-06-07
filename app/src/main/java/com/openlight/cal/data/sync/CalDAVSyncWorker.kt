package com.openlight.cal.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.model.CalendarAccount
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

        private const val BACKOFF_BASE_MS = 300_000L   // 5 minutes
        private const val BACKOFF_MAX_MS  = 18_000_000L // 5 hours
        private const val BACKOFF_MAX_SHIFT = 6

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
        val db        = AppDatabase.getInstance(applicationContext)
        val accountId = inputData.getLong(KEY_ACCOUNT_ID, -1L)
        val accounts: List<CalendarAccount> = if (accountId == -1L) {
            db.calendarAccountDao().getAll().filter { it.enabled }
        } else {
            listOfNotNull(db.calendarAccountDao().getById(accountId))
        }

        if (accounts.isEmpty()) return@withContext Result.success()

        val encryptor = EncryptedPassword(applicationContext)
        val engine    = CalDAVSyncEngine(db, encryptor)
        val now       = System.currentTimeMillis()

        for (account in accounts) {
            if (account.syncBackoffUntil > now && accountId == -1L) {
                Log.i(TAG, "Skipping ${account.displayName} (backoff until ${account.syncBackoffUntil})")
                continue
            }

            val result = engine.syncAccount(account)

            val updated = if (result.isSuccess) {
                account.copy(
                    lastSyncMs    = now,
                    syncFailCount = 0,
                    syncBackoffUntil = 0L
                )
            } else {
                val newFailCount = account.syncFailCount + 1
                val delayMs = backoffMs(newFailCount)
                Log.e(TAG, "Sync failed for ${account.displayName} " +
                        "(fail #$newFailCount, backoff ${delayMs / 60_000}min): ${result.error}")
                account.copy(
                    syncFailCount    = newFailCount,
                    syncBackoffUntil = now + delayMs
                )
            }
            db.calendarAccountDao().update(updated)
        }

        Result.success()
    }
}
