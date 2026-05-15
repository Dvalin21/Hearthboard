package com.openlight.cal.data.db

import android.content.Context
import androidx.room.Room

// Add singleton to AppDatabase
fun AppDatabase.Companion.getInstance(context: Context): AppDatabase {
    return INSTANCE ?: synchronized(this) {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "openlight.db"
        )
            .fallbackToDestructiveMigration()
            .build()
            .also { INSTANCE = it }
    }
}

private var INSTANCE: AppDatabase? = null

fun AppDatabase.Companion.get(context: Context) = getInstance(context)
