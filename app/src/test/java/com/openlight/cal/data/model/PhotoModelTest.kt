package com.openlight.cal.data.model

import org.junit.Assert.*
import org.junit.Test

class PhotoModelTest {
    @Test
    fun `Photo sync state defaults to LOCAL_ONLY`() {
        val p = Photo(
            id = "1", contentHash = "abc", localPath = "/tmp/x.jpg",
            width = 10, height = 10, takenAtMs = 0L, locationLat = 0.0, locationLon = 0.0,
            cameraMake = "", cameraModel = "", importedAtMs = 1L, sizeBytes = 100L,
            mimeType = "image/jpeg", updatedAtMs = 1L
        )
        assertEquals(PhotoSyncState.LOCAL_ONLY, p.syncState)
        assertEquals("", p.remoteId)
    }

    @Test
    fun `Photo copy is independent`() {
        val a = Photo(id = "1", contentHash = "abc", localPath = "/tmp/x.jpg",
            width = 10, height = 10, takenAtMs = 0L, locationLat = 0.0, locationLon = 0.0,
            cameraMake = "", cameraModel = "", importedAtMs = 1L, sizeBytes = 100L,
            mimeType = "image/jpeg", updatedAtMs = 1L)
        val b = a.copy(syncState = PhotoSyncState.SYNCED, remoteId = "r1")
        assertEquals(PhotoSyncState.LOCAL_ONLY, a.syncState)
        assertEquals("", a.remoteId)
        assertEquals(PhotoSyncState.SYNCED, b.syncState)
        assertEquals("r1", b.remoteId)
    }

    @Test
    fun `Album remoteId defaults empty`() {
        val a = Album(id = "1", name = "A")
        assertEquals("", a.remoteId)
        assertEquals(false, a.isShared)
    }
}
