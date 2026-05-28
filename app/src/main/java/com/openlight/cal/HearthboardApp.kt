package com.openlight.cal

import android.app.Application
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.preferences.AppPreferences
import com.openlight.cal.data.preferences.EncryptedPassword
import com.openlight.cal.data.repository.*
import com.openlight.cal.data.sync.CalDAVSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    override fun onCreate() {
        super.onCreate()

        // Seed default people on first launch
        CoroutineScope(Dispatchers.IO).launch {
            personRepository.seedDefaultPeople()
        }

        // Schedule background CalDAV sync
        CalDAVSyncWorker.schedulePeriodicSync(this)
    }
}
