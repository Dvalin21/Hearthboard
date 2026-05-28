package com.openlight.cal.data.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.openlight.cal.MainActivity
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.model.CalendarEvent

/**
 * Notification helper for event reminders.
 * Uses the modern NotificationCompat pattern. No third-party SDKs.
 */
object NotificationHelper {

    private const val CHANNEL_ID = "event_reminders"
    private const val CHANNEL_NAME = "Event Reminders"
    private const val CHANNEL_DESC = "Reminders for upcoming calendar events"

    /** Create the notification channel (safe to call multiple times). */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESC
            enableVibration(true)
            setShowBadge(true)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    /**
     * Fire a reminder notification for an event.
     * Called from the alarm receiver when it's time.
     */
    fun showEventReminder(context: Context, event: CalendarEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                return  // User hasn't granted notification permission yet
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summary = if (event.title.length > 40) event.title.take(37) + "..." else event.title
        val body = if (event.location.isNotBlank()) "At ${event.location}" else "Tap to view"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(summary)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Use unique notification ID per event to avoid overwriting
        val notifId = (event.id % Int.MAX_VALUE).toInt() + 1000
        NotificationManagerCompat.from(context).notify(notifId, notification)
    }

    /**
     * Check if any events are due for a reminder right now and fire notifications.
     * Called periodically from the reminder worker.
     */
    fun fireDueReminders(context: Context) {
        val db = AppDatabase.getInstance(context)
        val now = System.currentTimeMillis()
        val fiveMinAgo = now - 300_000L
        val fiveMinFromNow = now + 300_000L

        // Grab events starting within the next 5 minutes
        val upcoming = kotlinx.coroutines.runBlocking {
            db.calendarEventDao().getInRange(fiveMinAgo, fiveMinFromNow)
        }
        for (event in upcoming) {
            if (event.isCancelled) continue
            val reminderMs = when {
                event.reminderMinutes > 0 -> event.reminderMinutes * 60_000L
                else -> 15 * 60_000L  // default 15 minutes
            }
            val reminderTime = event.startMs - reminderMs
            // Fire if the reminder window is active (within 5 min of the reminder time)
            if (reminderTime in (now - 600_000L)..now) {
                showEventReminder(context, event)
            }
        }
    }
}
