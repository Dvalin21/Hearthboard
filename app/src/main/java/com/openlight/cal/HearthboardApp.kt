package com.openlight.cal

import android.app.Application
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.preferences.AppPreferences
import com.openlight.cal.data.preferences.EncryptedPassword
import com.openlight.cal.data.repository.*
import com.openlight.cal.data.sync.CalDAVSyncWorker
import com.openlight.cal.data.sync.NotificationHelper
import com.openlight.cal.data.sync.ReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HearthboardApp : Application() {

    // Simple manual DI — no Hilt/Dagger to keep F-Droid bundle small.
    // Uses AppDatabase.getInstance() singleton so CalDAVSyncWorker
    // and UI screens share the same Room instance.
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val preferences: AppPreferences by lazy { AppPreferences(this) }

    /** AES-256/GCM password encryptor backed by Android Keystore */
    val encryptor: EncryptedPassword by lazy { EncryptedPassword(this) }

    val calendarRepository: CalendarRepository by lazy { CalendarRepository(database, encryptor) }
    val taskRepository: TaskRepository         by lazy { TaskRepository(database, encryptor) }
    val personRepository: PersonRepository     by lazy { PersonRepository(database) }
    val accountRepository: AccountRepository   by lazy { AccountRepository(database, encryptor) }
    val rewardRepository: RewardRepository     by lazy { RewardRepository(database) }
    val recipeRepository: RecipeRepository     by lazy { RecipeRepository(database) }

    override fun onCreate() {
        super.onCreate()

        // PDFBox: previous code called PDFBoxResourceLoader.init() here for
        // "schedule import" — the feature consumer (a PDF parser screen) was
        // never committed, and the com.tom_roush:pdfbox-android dependency
        // is not in app/build.gradle.kts. Re-add init + the dependency when
        // the PDF import feature lands.

        // Create notification channel for event reminders
        NotificationHelper.createChannel(this)

        // Seed default people on first launch
        CoroutineScope(Dispatchers.IO).launch {
            personRepository.seedDefaultPeople()
        }

        // Schedule background CalDAV sync
        CalDAVSyncWorker.schedulePeriodicSync(this)

        // Schedule periodic reminder check (every 15 min)
        ReminderWorker.schedule(this)

        // Auto-archive old events on startup
        CoroutineScope(Dispatchers.IO).launch {
            val months = preferences.autoArchiveMonths.first()
            if (months > 0) {
                val cutoff = System.currentTimeMillis() - (months * 30L * 24L * 3600_000L)
                database.calendarEventDao().deleteBefore(cutoff)
            }
        }
    }
}
