package com.openlight.cal

import android.app.Application
import androidx.room.Room
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.preferences.AppPreferences
import com.openlight.cal.data.repository.*
import com.openlight.cal.data.sync.CalDAVSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OpenLightApp : Application() {

    // Simple manual DI - no Hilt/Dagger to keep F-Droid bundle small
    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "openlight.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val preferences: AppPreferences by lazy { AppPreferences(this) }

    val calendarRepository: CalendarRepository by lazy { CalendarRepository(database) }
    val taskRepository: TaskRepository         by lazy { TaskRepository(database) }
    val personRepository: PersonRepository     by lazy { PersonRepository(database) }
    val accountRepository: AccountRepository   by lazy { AccountRepository(database) }

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
