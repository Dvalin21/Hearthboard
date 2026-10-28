package com.openlight.cal.data.db

import androidx.room.Room
import com.openlight.cal.data.model.CalendarAccount
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Regression: a sync pass must not write a whole CalendarAccount row back from
 * a snapshot taken before the pass. CalDAVSyncEngine discovers and stores
 * `calendarPath` and `ctag` mid-pass; the worker then wrote its pre-pass row
 * back, setting calendarPath to "". pushIcsToCalDAV() returns early on a blank
 * calendarPath, so local event edits were never uploaded and nothing reported
 * an error.
 *
 * The full-row @Update(...) calls below are what the old code did. They must
 * not be reintroduced: the narrow DAO updates are what keep the columns apart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class CalendarAccountWriteTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertAccount(): CalendarAccount {
        val dao = db.calendarAccountDao()
        val id = dao.insert(
            CalendarAccount(displayName = "family", serverUrl = "http://host", username = "u")
        )
        return dao.getById(id)!!
    }

    @Test
    fun `sync bookkeeping write keeps the calendarPath the engine discovered`() = runBlocking {
        val dao = db.calendarAccountDao()
        val snapshot = insertAccount()               // read before the pass: calendarPath = ""

        // Engine discovered the primary calendar during the pass.
        dao.updateSyncTarget(snapshot.id, ctag = "ctag-1", calendarPath = "/cal/primary/")

        // Worker records the pass outcome from the snapshot it was given.
        dao.updateSyncState(snapshot.id, lastSyncMs = 456L, syncFailCount = 0, syncBackoffUntil = 0L)

        val after = dao.getById(snapshot.id)!!
        assertEquals("/cal/primary/", after.calendarPath)
        assertEquals("ctag-1", after.ctag)
        assertEquals(456L, after.lastSyncMs)
    }

    @Test
    fun `credential change during a pass is not reverted by the outcome write`() = runBlocking {
        val dao = db.calendarAccountDao()
        val snapshot = insertAccount()

        // User re-entered the password in Settings while the pass was running.
        // Settings reads the row fresh before saving, so only the password moves.
        dao.updateSyncTarget(snapshot.id, ctag = "ctag-2", calendarPath = "/cal/primary/")
        val fresh = dao.getById(snapshot.id)!!
        dao.update(fresh.copy(passwordEncrypted = "v2:new-password"))

        dao.updateSyncState(snapshot.id, lastSyncMs = 789L, syncFailCount = 0, syncBackoffUntil = 0L)

        val after = dao.getById(snapshot.id)!!
        assertEquals("v2:new-password", after.passwordEncrypted)
        assertEquals("/cal/primary/", after.calendarPath)
    }

    @Test
    fun `a failed pass keeps the previous lastSyncMs and sets a backoff`() = runBlocking {
        val dao = db.calendarAccountDao()
        val snapshot = insertAccount()
        dao.updateSyncState(snapshot.id, lastSyncMs = 1000L, syncFailCount = 0, syncBackoffUntil = 0L)
        val beforeFailure = dao.getById(snapshot.id)!!

        // Two consecutive failures, as the worker computes them.
        dao.updateSyncState(beforeFailure.id, lastSyncMs = beforeFailure.lastSyncMs,
            syncFailCount = 1, syncBackoffUntil = 2_000_000L)
        dao.updateSyncState(beforeFailure.id, lastSyncMs = beforeFailure.lastSyncMs,
            syncFailCount = 2, syncBackoffUntil = 3_000_000L)

        val after = dao.getById(snapshot.id)!!
        assertEquals("a failed pass must not claim a sync", 1000L, after.lastSyncMs)
        assertEquals(2, after.syncFailCount)
        assertTrue(after.syncBackoffUntil > 0L)
    }
}
