package com.openlight.cal.data.backup

import android.net.Uri
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.model.CalendarEvent
import com.openlight.cal.data.model.Person
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Regression: BackupManager.restore() cleared the database and then inserted
 * row by row with no transaction. A malformed backup therefore left the user
 * with an empty database and a "Restore failed" message — and the backup file
 * was the only remaining copy.
 *
 * The failure below is a valid-schema backup whose `tasks` array holds a
 * non-object entry: parseable JSON, rejected by JSONArray.getJSONObject().
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class BackupRestoreAtomicityTest {

    private val context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        AppDatabase.getInstance(context).close()
    }

    @Test
    fun `a restore that fails midway leaves the existing data untouched`() = runBlocking {
        val db = AppDatabase.getInstance(context)
        db.personDao().insert(Person(name = "Existing", colorHex = "#123456"))
        db.calendarEventDao().insert(
            CalendarEvent(uid = "keep-me", accountId = 0L, title = "Piano lesson",
                startMs = 1_000L, endMs = 2_000L)
        )
        val peopleBefore = db.personDao().getAll().size
        val eventsBefore = db.calendarEventDao().getAll().size
        assertTrue(peopleBefore > 0 && eventsBefore > 0)

        val backup = File(context.cacheDir, "bad-backup.json").apply {
            writeText("""
                {
                  "version": 1,
                  "people": [ { "id": 7, "name": "Someone", "colorHex": "#FFFFFF" } ],
                  "tasks": [ 1 ]
                }
            """.trimIndent())
        }

        val result = BackupManager.restore(context, Uri.fromFile(backup))

        assertFalse("restore must report failure", result.success)
        assertEquals("people must not be wiped", peopleBefore, db.personDao().getAll().size)
        assertEquals("events must not be wiped", eventsBefore, db.calendarEventDao().getAll().size)
        assertEquals(
            "Existing",
            db.personDao().getAll().first { it.name == "Existing" }.name
        )
    }
}
