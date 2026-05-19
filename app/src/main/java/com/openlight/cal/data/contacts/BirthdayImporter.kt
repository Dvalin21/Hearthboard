package com.openlight.cal.data.contacts

import android.content.ContentResolver
import android.provider.ContactsContract
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter

/** A contact with a known birthday. */
data class ContactBirthday(
    val contactId: Long,
    val displayName: String,
    /** The birthday as MonthDay (year may be unknown). */
    val monthDay: MonthDay,
    /** Year if known, null otherwise. */
    val year: Int? = null
) {
    /** Human-readable date string. */
    val dateLabel: String get() {
        val md = monthDay.format(DateTimeFormatter.ofPattern("MMM d"))
        return if (year != null) "$md, $year" else md
    }

    /** Create next-occurrence LocalDate for this birthday. */
    fun nextDate(): LocalDate {
        val today = LocalDate.now()
        val thisYear = today.with(monthDay)
        return if (!thisYear.isBefore(today)) thisYear else thisYear.plusYears(1)
    }
}

/** Reads contacts with birthday data from the device contacts provider. */
object BirthdayImporter {

    fun queryBirthdays(resolver: ContentResolver): List<ContactBirthday> {
        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Event.START_DATE
        )
        val selection = "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?"
        val selectionArgs = arrayOf(
            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()
        )

        val results = mutableListOf<ContactBirthday>()

        resolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(0)
                val name = cursor.getString(1) ?: continue
                val dateStr = cursor.getString(2) ?: continue

                val birthday = parseBirthday(dateStr) ?: continue
                results.add(ContactBirthday(
                    contactId = contactId,
                    displayName = name,
                    monthDay = birthday.first,
                    year = birthday.second
                ))
            }
        }

        // Deduplicate by contactId (some contacts have multiple birthday entries)
        return results.distinctBy { it.contactId }.sortedBy { it.displayName }
    }

    /** Parse a birthday string from ContactsContract (--MM-DD or YYYY-MM-DD). */
    private fun parseBirthday(dateStr: String): Pair<MonthDay, Int?>? {
        return try {
            when {
                dateStr.startsWith("--") -> {
                    // Format: --MM-DD (no year)
                    val md = MonthDay.parse(dateStr.removePrefix("--"), DateTimeFormatter.ofPattern("MM-dd"))
                    md to null
                }
                dateStr.length == 10 && dateStr[4] == '-' -> {
                    // Format: YYYY-MM-DD
                    val date = LocalDate.parse(dateStr)
                    MonthDay.from(date) to date.year
                }
                else -> null
            }
        } catch (_: Exception) { null }
    }
}
