package com.openlight.cal.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.model.Photo
import com.openlight.cal.data.model.PhotoSyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

class PhotoRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val photoDao = db.photoDao()
    private val photosDir = File(context.filesDir, "photos").apply { mkdirs() }

    /** Copy a picked photo into app-private storage and persist a Photo row. */
    suspend fun importPhoto(uri: Uri): Photo = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot read $uri")
        val hash = sha256(bytes)
        // dedup
        photoDao.getByHash(hash)?.let { return@withContext it }
        val id = UUID.randomUUID().toString()
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val ext = when {
            mimeType.contains("png") -> "png"
            mimeType.contains("webp") -> "webp"
            mimeType.contains("gif") -> "gif"
            mimeType.contains("heic") -> "heic"
            else -> "jpg"
        }
        val file = File(photosDir, "$id.$ext")
        FileOutputStream(file).use { it.write(bytes) }
        // decode dimensions
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)

        // Extract EXIF metadata
        var takenAt = 0L
        var cameraMake = ""
        var cameraModel = ""
        var lat = 0.0
        var lon = 0.0
        try {
            val exif = androidx.exifinterface.media.ExifInterface(file.absolutePath)
            val dateStr = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL)
            if (dateStr != null) {
                try {
                    val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.getDefault())
                    takenAt = sdf.parse(dateStr)?.time ?: 0L
                } catch (_: Exception) { }
            }
            cameraMake = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MAKE) ?: ""
            cameraModel = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MODEL) ?: ""
            val latLong = exif.latLong
            if (latLong != null) {
                lat = latLong[0]
                lon = latLong[1]
            }
        } catch (_: Exception) { }

        val photo = Photo(
            id = id,
            contentHash = hash,
            localPath = file.absolutePath,
            width = opts.outWidth,
            height = opts.outHeight,
            importedAtMs = System.currentTimeMillis(),
            sizeBytes = bytes.size.toLong(),
            mimeType = mimeType,
            syncState = PhotoSyncState.LOCAL_ONLY,
            updatedAtMs = System.currentTimeMillis()
        )
        photoDao.insert(photo)
        photo
    }

    suspend fun getAll(): List<Photo> = photoDao.getAll()
    fun getAllFlow() = photoDao.getAllFlow()
    suspend fun getById(id: String): Photo? = photoDao.getById(id)
    suspend fun delete(photo: Photo) = withContext(Dispatchers.IO) {
        File(photo.localPath).delete()
        photoDao.delete(photo)
    }
    suspend fun setFavorite(id: String, value: Boolean) {
        photoDao.getById(id)?.let { photoDao.update(it.copy(isFavorite = value, updatedAtMs = System.currentTimeMillis())) }
    }
    suspend fun setArchived(id: String, value: Boolean) {
        photoDao.getById(id)?.let { photoDao.update(it.copy(isArchived = value, updatedAtMs = System.currentTimeMillis())) }
    }
    suspend fun setTrashed(id: String, value: Boolean) {
        photoDao.getById(id)?.let {
            photoDao.update(it.copy(isTrashed = value, trashedAtMs = if (value) System.currentTimeMillis() else 0L, updatedAtMs = System.currentTimeMillis()))
        }
    }
    /** Delete local copies of photos that are synced to server. Keeps Room metadata. */
    suspend fun freeUpSpace(): Int = withContext(Dispatchers.IO) {
        var freed = 0
        val synced = photoDao.getAll().filter { it.syncState == com.openlight.cal.data.model.PhotoSyncState.SYNCED }
        for (photo in synced) {
            val file = File(photo.localPath)
            if (file.exists() && file.delete()) {
                freed++
                // Keep metadata but clear local path
                photoDao.update(photo.copy(localPath = "", updatedAtMs = System.currentTimeMillis()))
            }
        }
        freed
    }

    /** Simple perceptual hash (average hash) for duplicate detection. */
    fun computePHash(bitmap: android.graphics.Bitmap): Long {
        val small = android.graphics.Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        val pixels = IntArray(64)
        small.getPixels(pixels, 0, 8, 0, 0, 8, 8)
        val avg = pixels.map { (it shr 16 and 0xFF) + (it shr 8 and 0xFF) + (it and 0xFF) }.average()
        var hash = 0L
        for (i in pixels.indices) {
            val gray = (pixels[i] shr 16 and 0xFF) + (pixels[i] shr 8 and 0xFF) + (pixels[i] and 0xFF)
            if (gray > avg) hash = hash or (1L shl i)
        }
        return hash
    }

    /** Find duplicate photos by perceptual hash (hamming distance <= 5). */
    suspend fun findDuplicates(): List<List<Photo>> = withContext(Dispatchers.IO) {
        val photos = photoDao.getAll()
        val groups = mutableMapOf<Long, MutableList<Photo>>()
        for (photo in photos) {
            if (photo.localPath.isEmpty()) continue
            val bmp = android.graphics.BitmapFactory.decodeFile(photo.localPath) ?: continue
            val hash = computePHash(bmp)
            bmp.recycle()
            // Find existing group with hamming distance <= 5
            var found = false
            for ((existingHash, group) in groups) {
                if (hammingDistance(hash, existingHash) <= 5) {
                    group.add(photo)
                    found = true
                    break
                }
            }
            if (!found) groups[hash] = mutableListOf(photo)
        }
        groups.values.filter { it.size > 1 }.toList()
    }

    private fun hammingDistance(a: Long, b: Long): Int {
        var x = a xor b
        var count = 0
        while (x != 0L) { count += (x and 1).toInt(); x = x ushr 1 }
        return count
    }

    suspend fun purgeOldTrash() {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        photoDao.purgeOldTrash(cutoff)
    }

    private fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
