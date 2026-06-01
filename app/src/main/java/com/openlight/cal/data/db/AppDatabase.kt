package com.openlight.cal.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import com.openlight.cal.data.db.dao.*
import com.openlight.cal.data.model.*

@TypeConverters(Converters::class)
@Database(
    entities = [
        Person::class,
        CalendarAccount::class,
        CalendarEvent::class,
        Task::class,
        CheckList::class,
        CheckListItem::class,
        MealPlan::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun calendarAccountDao(): CalendarAccountDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun taskDao(): TaskDao
    abstract fun checkListDao(): CheckListDao
    abstract fun mealPlanDao(): MealPlanDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "openlight.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = Migration(1, 2) { db ->
            db.execSQL("ALTER TABLE people ADD COLUMN role TEXT NOT NULL DEFAULT 'PARENT'")
            db.execSQL("ALTER TABLE people ADD COLUMN caregiverPersonId INTEGER NOT NULL DEFAULT 0")
        }

        private val MIGRATION_2_3 = Migration(2, 3) { db ->
            db.execSQL("ALTER TABLE people ADD COLUMN email TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE calendar_events ADD COLUMN organizerEmail TEXT NOT NULL DEFAULT ''")
        }

        private val MIGRATION_3_4 = Migration(3, 4) { db ->
            db.execSQL("ALTER TABLE calendar_accounts ADD COLUMN syncFailCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE calendar_accounts ADD COLUMN syncBackoffUntil INTEGER NOT NULL DEFAULT 0")
        }

        private val MIGRATION_4_5 = Migration(4, 5) { db ->
            // Task.isChore flag added to support the Chores screen.
            // Default 0 (false) so every existing row becomes a regular
            // task and nothing about user data changes on upgrade.
            db.execSQL("ALTER TABLE tasks ADD COLUMN isChore INTEGER NOT NULL DEFAULT 0")
        }
    }
}

class Converters {
    @TypeConverter
    fun fromAccountType(value: AccountType): String = value.name

    @TypeConverter
    fun toAccountType(value: String): AccountType = AccountType.valueOf(value)

    @TypeConverter
    fun fromTaskPriority(value: TaskPriority): String = value.name

    @TypeConverter
    fun toTaskPriority(value: String): TaskPriority = TaskPriority.valueOf(value)

    @TypeConverter
    fun fromMealSlot(value: MealSlot): String = value.name

    @TypeConverter
    fun toMealSlot(value: String): MealSlot = MealSlot.valueOf(value)

    @TypeConverter
    fun fromPersonRole(value: PersonRole): String = value.name

    @TypeConverter
    fun toPersonRole(value: String): PersonRole = PersonRole.valueOf(value)
}
