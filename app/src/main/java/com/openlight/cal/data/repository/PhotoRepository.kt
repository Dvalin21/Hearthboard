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
        val ext = uri.path?.substringAfterLast('.', "jpg") ?: "jpg"
        val file = File(photosDir, "$id.$ext")
        FileOutputStream(file).use { it.write(bytes) }
        // decode dimensions
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val photo = Photo(
            id = id,
            contentHash = hash,
            localPath = file.absolutePath,
            width = opts.outWidth,
            height = opts.outHeight,
            importedAtMs = System.currentTimeMillis(),
            sizeBytes = bytes.size.toLong(),
            mimeType = context.contentResolver.getType(uri) ?: "image/jpeg",
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
    suspend fun purgeOldTrash() {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        photoDao.purgeOldTrash(cutoff)
    }

    private fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
KOTLIN
echo done