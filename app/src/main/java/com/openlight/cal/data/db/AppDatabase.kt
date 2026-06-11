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
        MealPlan::class,
        Reward::class,
        RedeemedReward::class,
        Recipe::class,
        Label::class,
        PersonLabel::class
    ],
    version = 10,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun calendarAccountDao(): CalendarAccountDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun taskDao(): TaskDao
    abstract fun checkListDao(): CheckListDao
    abstract fun mealPlanDao(): MealPlanDao
    abstract fun rewardDao(): RewardDao
    abstract fun redeemedRewardDao(): RedeemedRewardDao
    abstract fun recipeDao(): RecipeDao
    abstract fun labelDao(): LabelDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "openlight.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
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

        private val MIGRATION_5_6 = Migration(5, 6) { db ->
            // Rewards system: two new tables, no changes to existing data.
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS rewards (
                    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    name        TEXT    NOT NULL,
                    emoji       TEXT    NOT NULL DEFAULT '🎁',
                    starCost    INTEGER NOT NULL,
                    description TEXT    NOT NULL DEFAULT '',
                    isEnabled   INTEGER NOT NULL DEFAULT 1,
                    sortOrder   INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS redeemed_rewards (
                    id            INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    rewardId      INTEGER NOT NULL,
                    rewardName    TEXT    NOT NULL,
                    rewardEmoji   TEXT    NOT NULL,
                    personId      INTEGER NOT NULL,
                    cost          INTEGER NOT NULL,
                    redeemedAtMs  INTEGER NOT NULL,
                    note          TEXT    NOT NULL DEFAULT ''
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_redeemed_personId ON redeemed_rewards(personId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_redeemed_redeemedAtMs ON redeemed_rewards(redeemedAtMs)")
        }

        private val MIGRATION_7_8 = Migration(7, 8) { db ->
            // Task schedule start/end times for profile-column timeline.
            // Nullable INTEGER — tasks without a schedule remain null.
            db.execSQL("ALTER TABLE tasks ADD COLUMN startMs INTEGER")
            db.execSQL("ALTER TABLE tasks ADD COLUMN endMs INTEGER")
        }

        private val MIGRATION_8_9 = Migration(8, 9) { db ->
            // Reward renew-after-redemption toggle + optional profile assignment.
            db.execSQL("ALTER TABLE rewards ADD COLUMN renewAfterRedeeming INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE rewards ADD COLUMN assignedPersonId INTEGER NOT NULL DEFAULT 0")
        }

        private val MIGRATION_9_10 = Migration(9, 10) { db ->
            // Labels: color-coded categories for people + cross-reference table.
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS labels (
                    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    name        TEXT    NOT NULL,
                    colorHex    TEXT    NOT NULL DEFAULT '#4CAF50',
                    sortOrder   INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS person_labels (
                    id        INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    personId  INTEGER NOT NULL,
                    labelId   INTEGER NOT NULL,
                    FOREIGN KEY (personId) REFERENCES people(id) ON DELETE CASCADE,
                    FOREIGN KEY (labelId)  REFERENCES labels(id) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_person_labels_personId ON person_labels(personId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_person_labels_labelId  ON person_labels(labelId)")
        }

        private val MIGRATION_6_7 = Migration(6, 7) { db ->
            // Recipes: single self-contained table. Ingredients and
            // instructions are JSON strings, not child tables. No FKs,
            // no cascades, no indexes beyond the implicit one on the
            // primary key — for the family-scale recipe count (tens to
            // low hundreds) the table scan cost is irrelevant.
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS recipes (
                    id                  INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    name                TEXT    NOT NULL,
                    description         TEXT    NOT NULL DEFAULT '',
                    ingredientsJson     TEXT    NOT NULL DEFAULT '[]',
                    instructionsJson    TEXT    NOT NULL DEFAULT '[]',
                    prepTimeMinutes     INTEGER NOT NULL DEFAULT 0,
                    cookTimeMinutes     INTEGER NOT NULL DEFAULT 0,
                    servings            INTEGER NOT NULL DEFAULT 0,
                    imageUrl            TEXT    NOT NULL DEFAULT '',
                    sourceUrl           TEXT    NOT NULL DEFAULT '',
                    tags                TEXT    NOT NULL DEFAULT '',
                    rating              INTEGER NOT NULL DEFAULT 0,
                    notes               TEXT    NOT NULL DEFAULT '',
                    isFavorite          INTEGER NOT NULL DEFAULT 0,
                    createdAtMs         INTEGER NOT NULL,
                    updatedAtMs         INTEGER NOT NULL,
                    mealieId            TEXT    NOT NULL DEFAULT '',
                    mealieLastPushMs    INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            // Index on mealieId for sync lookups (getByMealieId in the DAO).
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_recipes_mealieId ON recipes(mealieId)")
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
