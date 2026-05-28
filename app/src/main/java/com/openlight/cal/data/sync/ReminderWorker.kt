package com.openlight.cal.data.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that checks for upcoming event reminders and fires notifications.
 * Runs every 15 minutes. Lightweight — only queries the database and fires notifications
 * for events starting within the next 5 minutes with active reminder windows.
 *
 * This replaces AlarmManager-based scheduling (which is restricted on Android 12+)
 * with a simple polling approach using WorkManager's battery-friendly periodic API.
 */
class ReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val WORK_NAME = "event_reminder_check"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .build()  // No network needed — all data is local
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        NotificationHelper.fireDueReminders(applicationContext)
        return Result.success()
    }
}
