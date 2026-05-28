@file:Suppress("DEPRECATION")

package com.openlight.cal.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.openlight.cal.R
import com.openlight.cal.data.db.AppDatabase
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Homescreen widget showing today's date and upcoming events.
 * Updates every 30 minutes (updatePeriodMillis in widget_info).
 * No network calls, no tracking.
 */
class CalendarWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        private const val TAG = "CalendarWidget"

        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.calendar_widget)

            // Today's date
            val today = LocalDate.now()
            val dayOfWeek = today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
            val formatted = today.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
            views.setTextViewText(R.id.widget_date, "$dayOfWeek, $formatted")

            // Get upcoming events
            val db = AppDatabase.getInstance(context)
            val now = System.currentTimeMillis()
            val endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val events = try {
                kotlinx.coroutines.runBlocking {
                    db.calendarEventDao().getInRange(now, endOfDay).take(3)
                }
            } catch (_: Exception) { emptyList() }

            val eventIds = listOf(R.id.widget_event1, R.id.widget_event2, R.id.widget_event3)

            if (events.isEmpty()) {
                views.setViewVisibility(R.id.widget_events_container, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_events_container, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)

                events.forEachIndexed { i, event ->
                    val time = java.time.Instant.ofEpochMilli(event.startMs)
                        .atZone(ZoneId.systemDefault())
                        .toLocalTime()
                        .format(DateTimeFormatter.ofPattern("h:mm"))
                    val text = "$time ${event.title}"
                    views.setTextViewText(eventIds[i], text)
                }
                // Hide unused event slots
                for (i in events.size until eventIds.size) {
                    views.setViewVisibility(eventIds[i], android.view.View.GONE)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}