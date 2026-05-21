package com.openlight.cal.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for CalDAVSyncWorker companion utilities.
 *
 * backoffMs() is a pure function with no Android dependencies.
 */
class CalDAVSyncWorkerTest {

    @Test
    fun `backoffMs starts at 5 minutes`() {
        val ms = CalDAVSyncWorker.backoffMs(0)
        assertEquals("First failure should backoff 5 minutes", 300_000L, ms)
    }

    @Test
    fun `backoffMs doubles each failure`() {
        val results = (0..5).map { CalDAVSyncWorker.backoffMs(it) }
        val expected = listOf(
            300_000L,    // 5 min
            600_000L,    // 10 min
            1_200_000L,  // 20 min
            2_400_000L,  // 40 min
            4_800_000L,  // 80 min
            9_600_000L   // 160 min
        )
        assertEquals("Exponential backoff sequence", expected, results)
    }

    @Test
    fun `backoffMs caps at 5 hours`() {
        val capped = CalDAVSyncWorker.backoffMs(6)
        assertEquals("6th failure should cap at 5 hours", 18_000_000L, capped)
    }

    @Test
    fun `backoffMs does not exceed cap for high failure counts`() {
        val high = CalDAVSyncWorker.backoffMs(100)
        val alsoHigh = CalDAVSyncWorker.backoffMs(50)
        assertEquals("100 failures should still cap at 5 hours", 18_000_000L, high)
        assertEquals("50 failures should still cap at 5 hours", 18_000_000L, alsoHigh)
    }

    @Test
    fun `backoffMs handles negative failure count`() {
        val neg = CalDAVSyncWorker.backoffMs(-1)
        assertEquals("Negative failure count should treat as 0", 300_000L, neg)
    }

    @Test
    fun `backoffMs sequence is monotonic`() {
        val results = (0..10).map { CalDAVSyncWorker.backoffMs(it) }
        for (i in 1 until results.size) {
        assert(results[i] >= results[i - 1]) {
            "backoffMs should never decrease (index $i: ${results[i-1]} > ${results[i]})"
        }
        }
    }

    @Test
    fun `backoffMs 5min base times 2^N`() {
        // Verify the mathematical relationship: backoff = min(5min * 2^N, 5h)
        for (failCount in 0..5) {
            val expected = minOf(300_000L shl failCount, 18_000_000L)
            val actual = CalDAVSyncWorker.backoffMs(failCount)
            assertEquals("backoffMs($failCount) should equal min(5min * 2^$failCount, 5h)",
                expected, actual)
        }
    }
}
