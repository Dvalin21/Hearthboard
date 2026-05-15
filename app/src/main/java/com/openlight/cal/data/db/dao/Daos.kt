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

    @Query("SELECT * FROM tasks WHERE assignedPersonId = :personId ORDER BY isCompleted, priority DESC, dueMs")
    fun getByPersonFlow(personId: Long): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY priority DESC, dueMs, sortOrder")
    fun getActiveFlow(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE listId = :listId ORDER BY isCompleted, sortOrder")
    fun getByListFlow(listId: Long): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE uid = :uid AND accountId = :accountId LIMIT 1")
    suspend fun getByUid(uid: String, accountId: Long): Task?

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meal: MealPlan)

    @Delete
    suspend fun delete(meal: MealPlan)
}
