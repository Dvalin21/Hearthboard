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
        PersonLabel::class,
        Photo::class,
        Album::class,
        AlbumPhotoCrossRef::class,
        PhotoSyncAccount::class
    ],
    version = 11,
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
    abstract fun photoDao(): PhotoDao
    abstract fun albumDao(): AlbumDao
    abstract fun photoSyncAccountDao(): PhotoSyncAccountDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "openlight.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
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
        private val MIGRATION_10_11 = Migration(10, 11) { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS photos (id TEXT NOT NULL PRIMARY KEY, contentHash TEXT NOT NULL, localPath TEXT NOT NULL, remoteId TEXT NOT NULL DEFAULT '', width INTEGER NOT NULL DEFAULT 0, height INTEGER NOT NULL DEFAULT 0, takenAtMs INTEGER NOT NULL DEFAULT 0, importedAtMs INTEGER NOT NULL DEFAULT 0, sizeBytes INTEGER NOT NULL DEFAULT 0, mimeType TEXT NOT NULL DEFAULT 'image/jpeg', isFavorite INTEGER NOT NULL DEFAULT 0, isArchived INTEGER NOT NULL DEFAULT 0, isTrashed INTEGER NOT NULL DEFAULT 0, trashedAtMs INTEGER NOT NULL DEFAULT 0, caption TEXT NOT NULL DEFAULT '', locationLat REAL NOT NULL DEFAULT 0.0, locationLon REAL NOT NULL DEFAULT 0.0, cameraMake TEXT NOT NULL DEFAULT '', cameraModel TEXT NOT NULL DEFAULT '', syncState TEXT NOT NULL DEFAULT 'LOCAL_ONLY', updatedAtMs INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_photos_hash ON photos(contentHash)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_photos_taken ON photos(takenAtMs)")
            db.execSQL("CREATE TABLE IF NOT EXISTS albums (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, createdAtMs INTEGER NOT NULL DEFAULT 0, updatedAtMs INTEGER NOT NULL DEFAULT 0, coverPhotoId TEXT NOT NULL DEFAULT '', isShared INTEGER NOT NULL DEFAULT 0, remoteId TEXT NOT NULL DEFAULT '')")
            db.execSQL("CREATE TABLE IF NOT EXISTS album_photos (albumId TEXT NOT NULL, photoId TEXT NOT NULL, addedAtMs INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (albumId, photoId), FOREIGN KEY (albumId) REFERENCES albums(id) ON DELETE CASCADE, FOREIGN KEY (photoId) REFERENCES photos(id) ON DELETE CASCADE)")
            db.execSQL("CREATE TABLE IF NOT EXISTS photo_sync_accounts (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, serverUrl TEXT NOT NULL DEFAULT '', username TEXT NOT NULL DEFAULT '', passwordEncrypted TEXT NOT NULL DEFAULT '', syncMode TEXT NOT NULL DEFAULT 'PUSH_ONLY', wifiOnly INTEGER NOT NULL DEFAULT 1, includeVideos INTEGER NOT NULL DEFAULT 0, syncIntervalMinutes INTEGER NOT NULL DEFAULT 30, lastSyncMs INTEGER NOT NULL DEFAULT 0, syncFailCount INTEGER NOT NULL DEFAULT 0, syncBackoffUntil INTEGER NOT NULL DEFAULT 0, cursor INTEGER NOT NULL DEFAULT 0)")
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
