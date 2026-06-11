package com.openlight.cal.data.db.dao

import androidx.room.*
import com.openlight.cal.data.model.*
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────────
// Person DAO
// ─────────────────────────────────────────────────────────────
@Dao
interface PersonDao {
    @Query("SELECT * FROM people ORDER BY sortOrder, name")
    fun getAllFlow(): Flow<List<Person>>

    @Query("SELECT * FROM people ORDER BY sortOrder, name")
    suspend fun getAll(): List<Person>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun getById(id: Long): Person?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(person: Person): Long

    @Update
    suspend fun update(person: Person)

    @Delete
    suspend fun delete(person: Person)

    @Query("SELECT COUNT(*) FROM people")
    suspend fun count(): Int
}

// ─────────────────────────────────────────────────────────────
// Calendar Account DAO
// ─────────────────────────────────────────────────────────────
@Dao
interface CalendarAccountDao {
    @Query("SELECT * FROM calendar_accounts ORDER BY displayName")
    fun getAllFlow(): Flow<List<CalendarAccount>>

    @Query("SELECT * FROM calendar_accounts ORDER BY displayName")
    suspend fun getAll(): List<CalendarAccount>

    @Query("SELECT * FROM calendar_accounts WHERE id = :id")
    suspend fun getById(id: Long): CalendarAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: CalendarAccount): Long

    @Update
    suspend fun update(account: CalendarAccount)

    @Delete
    suspend fun delete(account: CalendarAccount)
}

// ─────────────────────────────────────────────────────────────
// Calendar Event DAO
// ─────────────────────────────────────────────────────────────
@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events WHERE isCancelled = 0 ORDER BY startMs")
    fun getAllFlow(): Flow<List<CalendarEvent>>

    @Query("""
        SELECT * FROM calendar_events 
        WHERE isCancelled = 0 AND startMs >= :startMs AND startMs < :endMs 
        ORDER BY startMs
    """)
    fun getInRangeFlow(startMs: Long, endMs: Long): Flow<List<CalendarEvent>>

    @Query("""
        SELECT * FROM calendar_events 
        WHERE isCancelled = 0 AND startMs >= :startMs AND startMs < :endMs 
        ORDER BY startMs
    """)
    suspend fun getInRange(startMs: Long, endMs: Long): List<CalendarEvent>

    @Query("SELECT * FROM calendar_events WHERE uid = :uid AND accountId = :accountId LIMIT 1")
    suspend fun getByUid(uid: String, accountId: Long): CalendarEvent?

    @Query("SELECT * FROM calendar_events WHERE accountId = :accountId")
    suspend fun getByAccount(accountId: Long): List<CalendarEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: CalendarEvent): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<CalendarEvent>)

    @Update
    suspend fun update(event: CalendarEvent)

    @Delete
    suspend fun delete(event: CalendarEvent)

    @Query("DELETE FROM calendar_events WHERE accountId = :accountId")
    suspend fun deleteByAccount(accountId: Long)

    @Query("DELETE FROM calendar_events WHERE uid = :uid")
    suspend fun deleteByUid(uid: String)

    /** Used by HearthboardApp's auto-archive on startup: deletes
     *  events whose start time is older than the configured cutoff. */
    @Query("DELETE FROM calendar_events WHERE startMs < :cutoffMs")
    suspend fun deleteBefore(cutoffMs: Long): Int

    /** Used by BackupManager export to enumerate every event. */
    @Query("SELECT * FROM calendar_events ORDER BY startMs")
    suspend fun getAll(): List<CalendarEvent>

    @Query("SELECT * FROM calendar_events WHERE isCountdown = 1 AND isCancelled = 0 AND startMs > :nowMs ORDER BY startMs LIMIT 10")
    fun getCountdownsFlow(nowMs: Long): Flow<List<CalendarEvent>>
}

// ─────────────────────────────────────────────────────────────
// Task DAO
// ─────────────────────────────────────────────────────────────
@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isCompleted, priority DESC, dueMs, sortOrder")
    fun getAllFlow(): Flow<List<Task>>

    /** Used by BackupManager export to enumerate every task. */
    @Query("SELECT * FROM tasks ORDER BY isCompleted, priority DESC, dueMs, sortOrder")
    suspend fun getAll(): List<Task>

    @Query("SELECT * FROM tasks WHERE assignedPersonId = :personId ORDER BY isCompleted, priority DESC, dueMs")
    fun getByPersonFlow(personId: Long): Flow<List<Task>>

    /** Sum of stars earned by completing tasks/chores assigned to a person.
     *  Used to compute the Rewards-screen balance:
     *      balance = starsEarnedByPerson - starsSpentByPerson
     */
    @Query("""
        SELECT COALESCE(SUM(starsEarned), 0) FROM tasks
        WHERE assignedPersonId = :personId
          AND isCompleted = 1
    """)
    suspend fun starsEarnedByPerson(personId: Long): Int

    /** Same as starsEarnedByPerson but Flow-backed for reactive UI. */
    @Query("""
        SELECT COALESCE(SUM(starsEarned), 0) FROM tasks
        WHERE assignedPersonId = :personId
          AND isCompleted = 1
    """)
    fun starsEarnedByPersonFlow(personId: Long): Flow<Int>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY priority DESC, dueMs, sortOrder")
    fun getActiveFlow(): Flow<List<Task>>

    /** Used by ChoresScreen: kid-friendly chores only, excludes completed ones. */
    @Query("SELECT * FROM tasks WHERE isChore = 1 AND isCompleted = 0 ORDER BY sortOrder, dueMs")
    fun getActiveChoresFlow(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE listId = :listId ORDER BY isCompleted, sortOrder")
    fun getByListFlow(listId: Long): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE uid = :uid AND accountId = :accountId LIMIT 1")
    suspend fun getByUid(uid: String, accountId: Long): Task?

    @Query("SELECT * FROM tasks WHERE accountId = :accountId")
    suspend fun getByAccount(accountId: Long): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<Task>)

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("UPDATE tasks SET isCompleted = :done, completedMs = :ts WHERE id = :id")
    suspend fun setCompleted(id: Long, done: Boolean, ts: Long?)
}

// ─────────────────────────────────────────────────────────────
// CheckList DAO
// ─────────────────────────────────────────────────────────────
@Dao
interface CheckListDao {
    @Query("SELECT * FROM checklists ORDER BY sortOrder, name")
    fun getAllFlow(): Flow<List<CheckList>>

    @Query("SELECT * FROM checklist_items WHERE listId = :listId ORDER BY isChecked, sortOrder")
    fun getItemsFlow(listId: Long): Flow<List<CheckListItem>>

    /** Used by BackupManager export to enumerate every checklist. */
    @Query("SELECT * FROM checklists ORDER BY sortOrder, name")
    suspend fun getAll(): List<CheckList>

    /** Used by BackupManager export to enumerate every item across all lists. */
    @Query("SELECT * FROM checklist_items ORDER BY listId, sortOrder")
    suspend fun getAllItems(): List<CheckListItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: CheckList): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: CheckListItem): Long

    @Update
    suspend fun updateList(list: CheckList)

    @Update
    suspend fun updateItem(item: CheckListItem)

    @Delete
    suspend fun deleteList(list: CheckList)

    @Delete
    suspend fun deleteItem(item: CheckListItem)

    @Query("UPDATE checklist_items SET isChecked = :checked WHERE id = :id")
    suspend fun setItemChecked(id: Long, checked: Boolean)

    @Query("DELETE FROM checklist_items WHERE listId = :listId AND isChecked = 1")
    suspend fun clearCheckedItems(listId: Long)
}

// ─────────────────────────────────────────────────────────────
// Meal Plan DAO
// ─────────────────────────────────────────────────────────────
@Dao
interface MealPlanDao {
    @Query("SELECT * FROM meal_plans WHERE dateIso >= :startDate AND dateIso <= :endDate ORDER BY dateIso, slot")
    fun getWeekFlow(startDate: String, endDate: String): Flow<List<MealPlan>>

    @Query("SELECT * FROM meal_plans WHERE dateIso = :date ORDER BY slot")
    fun getDayFlow(date: String): Flow<List<MealPlan>>

    /** Used by BackupManager export to enumerate every meal plan entry. */
    @Query("SELECT * FROM meal_plans ORDER BY dateIso, slot")
    suspend fun getAll(): List<MealPlan>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meal: MealPlan)

    @Delete
    suspend fun delete(meal: MealPlan)
}

// ─────────────────────────────────────────────────────────────
// REWARDS — catalog + redemption history
// ─────────────────────────────────────────────────────────────

@Dao
interface RewardDao {
    @Query("SELECT * FROM rewards ORDER BY sortOrder, name")
    fun getAllFlow(): Flow<List<Reward>>

    /** Shop-view: only enabled rewards. */
    @Query("SELECT * FROM rewards WHERE isEnabled = 1 ORDER BY sortOrder, starCost")
    fun getEnabledFlow(): Flow<List<Reward>>

    /** Used by BackupManager export. */
    @Query("SELECT * FROM rewards ORDER BY sortOrder, name")
    suspend fun getAll(): List<Reward>

    @Query("SELECT * FROM rewards WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): Reward?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reward: Reward): Long

    @Delete
    suspend fun delete(reward: Reward)
}

@Dao
interface RedeemedRewardDao {
    @Query("SELECT * FROM redeemed_rewards ORDER BY redeemedAtMs DESC")
    fun getHistoryFlow(): Flow<List<RedeemedReward>>

    @Query("SELECT * FROM redeemed_rewards WHERE personId = :personId ORDER BY redeemedAtMs DESC")
    fun getHistoryForPersonFlow(personId: Long): Flow<List<RedeemedReward>>

    /** Used by BackupManager export. */
    @Query("SELECT * FROM redeemed_rewards ORDER BY redeemedAtMs")
    suspend fun getAll(): List<RedeemedReward>

    /**
     * Per-person stars spent (sum of cost). Used to compute current balance.
     * Returns 0 if no redemptions yet.
     */
    @Query("SELECT COALESCE(SUM(cost), 0) FROM redeemed_rewards WHERE personId = :personId")
    suspend fun starsSpentByPerson(personId: Long): Int

    /** Same but as a Flow for live balance display. */
    @Query("SELECT COALESCE(SUM(cost), 0) FROM redeemed_rewards WHERE personId = :personId")
    fun starsSpentByPersonFlow(personId: Long): Flow<Int>

    @Insert
    suspend fun insert(redeemed: RedeemedReward): Long

    @Delete
    suspend fun delete(redeemed: RedeemedReward)
}

// ─────────────────────────────────────────────────────────────
// RECIPES
// ─────────────────────────────────────────────────────────────

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY isFavorite DESC, name COLLATE NOCASE")
    fun getAllFlow(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): Recipe?

    @Query("SELECT * FROM recipes WHERE id = :id LIMIT 1")
    fun getFlow(id: Long): Flow<Recipe?>

    /** Used by sync to find a local row by its Mealie counterpart. */
    @Query("SELECT * FROM recipes WHERE mealieId = :mealieId LIMIT 1")
    suspend fun getByMealieId(mealieId: String): Recipe?

    /** Recipes that have local edits since their last push. */
    @Query("""
        SELECT * FROM recipes
        WHERE updatedAtMs > mealieLastPushMs
        ORDER BY updatedAtMs DESC
    """)
    suspend fun getDirty(): List<Recipe>

    /** Free-text search across name + description + tags + notes. */
    @Query("""
        SELECT * FROM recipes
        WHERE name        LIKE '%' || :q || '%'
           OR description LIKE '%' || :q || '%'
           OR tags        LIKE '%' || :q || '%'
           OR notes       LIKE '%' || :q || '%'
        ORDER BY isFavorite DESC, name COLLATE NOCASE
    """)
    fun searchFlow(q: String): Flow<List<Recipe>>

    /** Used by BackupManager export. */
    @Query("SELECT * FROM recipes ORDER BY id")
    suspend fun getAll(): List<Recipe>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recipe: Recipe): Long

    @Delete
    suspend fun delete(recipe: Recipe)
}

// ─────────────────────────────────────────────────────────────
// LABELS — §11 color-coded person categories
// ─────────────────────────────────────────────────────────────

@Dao
interface LabelDao {
    @Query("SELECT * FROM labels ORDER BY sortOrder, name COLLATE NOCASE")
    fun getAllFlow(): Flow<List<Label>>

    @Query("SELECT * FROM labels ORDER BY sortOrder, name COLLATE NOCASE")
    suspend fun getAll(): List<Label>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(label: Label): Long

    @Delete
    suspend fun delete(label: Label)

    // ── Person-Label assignments ──────────────────────────

    @Query("SELECT l.* FROM labels l INNER JOIN person_labels pl ON pl.labelId = l.id WHERE pl.personId = :personId ORDER BY l.sortOrder, l.name")
    fun getLabelsForPersonFlow(personId: Long): Flow<List<Label>>

    @Query("SELECT l.* FROM labels l INNER JOIN person_labels pl ON pl.labelId = l.id WHERE pl.personId = :personId ORDER BY l.sortOrder, l.name")
    suspend fun getLabelsForPerson(personId: Long): List<Label>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun assignLabel(row: PersonLabel)

    @Query("DELETE FROM person_labels WHERE personId = :personId AND labelId = :labelId")
    suspend fun unassignLabel(personId: Long, labelId: Long)

    @Query("DELETE FROM person_labels WHERE personId = :personId")
    suspend fun unassignAllLabels(personId: Long)

    @Query("SELECT COUNT(*) FROM person_labels WHERE labelId = :labelId")
    suspend fun labelUsageCount(labelId: Long): Int
}
