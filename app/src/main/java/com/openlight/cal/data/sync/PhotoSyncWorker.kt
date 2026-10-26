package com.openlight.cal.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.model.Photo
import com.openlight.cal.data.model.PhotoSyncAccount
import com.openlight.cal.data.model.PhotoSyncMode
import com.openlight.cal.data.model.PhotoSyncState
import com.openlight.cal.data.preferences.AppPreferences
import com.openlight.cal.data.preferences.EncryptedPassword
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────
// PhotoSyncWorker — periodic sync with hearth-sync server
// Follows CalDAVSyncWorker pattern: network constraint, backoff
// ─────────────────────────────────────────────────────────────
class PhotoSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = AppDatabase.getInstance(context)
    private val prefs = AppPreferences(context)  // simplified; use DI in production
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val accounts = db.photoSyncAccountDao().getAll()
            if (accounts.isEmpty()) return@withContext Result.success()

            // Wi-Fi check
            val wifiOnly = accounts.any { it.wifiOnly }
            if (wifiOnly && !isOnWifi()) {
                return@withContext Result.retry()
            }

            var anyFailed = false
            for (account in accounts) {
                val now = System.currentTimeMillis()
                if (account.syncBackoffUntil > now) {
                    android.util.Log.d("PhotoSync", "skipping ${account.username} — backoff until ${account.syncBackoffUntil}")
                    continue
                }

                try {
                    syncAccount(account)
                    // Reset fail count on success
                    db.photoSyncAccountDao().update(account.copy(
                        lastSyncMs = System.currentTimeMillis(),
                        syncFailCount = 0,
                        syncBackoffUntil = 0
                    ))
                } catch (e: Exception) {
                    android.util.Log.e("PhotoSync", "sync failed for ${account.username}: ${e.message}", e)
                    val failCount = account.syncFailCount + 1
                    val backoff = (5L * 60 * 1000) * (1L shl minOf(failCount, 6)) // exponential
                    db.photoSyncAccountDao().update(account.copy(
                        syncFailCount = failCount,
                        syncBackoffUntil = System.currentTimeMillis() + backoff
                    ))
                    anyFailed = true
                }
            }

            if (anyFailed) Result.retry() else Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private suspend fun syncAccount(account: PhotoSyncAccount) {
        val baseUrl = account.serverUrl.trimEnd('/')
        val token = PhotoSyncAuth.login(applicationContext, baseUrl, account.username, account.passwordEncrypted)

        // PUSH: upload pending photos
        if (account.syncMode == PhotoSyncMode.PUSH_ONLY || account.syncMode == PhotoSyncMode.TWO_WAY) {
            val pending = db.photoDao().getPendingUploads()
            for (photo in pending.take(10)) { // batch limit
                uploadPhoto(baseUrl, token, photo)?.let { serverId ->
                    db.photoDao().update(photo.copy(
                        syncState = PhotoSyncState.SYNCED,
                        remoteId = serverId,
                        updatedAtMs = System.currentTimeMillis()
                    ))
                }
            }
        }

        // PUSH metadata changes for already-synced photos
        if (account.syncMode == PhotoSyncMode.PUSH_ONLY || account.syncMode == PhotoSyncMode.TWO_WAY) {
            pushMetadataChanges(baseUrl, token, account)
        }

        // ALBUM SYNC: push local albums, pull remote albums
        if (account.syncMode == PhotoSyncMode.PUSH_ONLY || account.syncMode == PhotoSyncMode.TWO_WAY) {
            pushAlbums(baseUrl, token)
        }
        if (account.syncMode == PhotoSyncMode.PULL_ONLY || account.syncMode == PhotoSyncMode.TWO_WAY) {
            pullAlbums(baseUrl, token)
            pullChanges(baseUrl, token, account)
        }
    }


    private suspend fun uploadPhoto(baseUrl: String, token: String, photo: Photo): String {
        val file = File(photo.localPath)
        if (!file.exists()) return photo.id
        var remoteId = photo.id
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(photo.mimeType.ifBlank { "image/jpeg" }.toMediaType()))
            .apply {
                // Metadata the Photo row already carries (extracted from EXIF at import)
                if (photo.takenAtMs > 0) addFormDataPart("taken_at", photo.takenAtMs.toString())
                if (photo.locationLat != 0.0) addFormDataPart("location_lat", photo.locationLat.toString())
                if (photo.locationLon != 0.0) addFormDataPart("location_lon", photo.locationLon.toString())
                if (photo.cameraMake.isNotBlank()) addFormDataPart("camera_make", photo.cameraMake)
                if (photo.cameraModel.isNotBlank()) addFormDataPart("camera_model", photo.cameraModel)
            }
            .build()
        val req = Request.Builder()
            .url("$baseUrl/api/photos")
            .header("Authorization", "Bearer $token")
            .post(requestBody)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Upload failed: ${resp.code}")
            // Server is authoritative for the id; dedup may return an existing photo.
            val json = JSONObject(resp.body!!.string())
            remoteId = json.optString("id")
        }
        return remoteId
    }

    private suspend fun pullChanges(baseUrl: String, token: String, account: PhotoSyncAccount) {
        var cursor = account.cursor
        var hasMore = true
        while (hasMore) {
            val req = Request.Builder()
                .url("$baseUrl/api/sync/changes?since=$cursor&limit=200")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            val resp = client.newCall(req).execute()
            val json = JSONObject(resp.body!!.string())
            val changes = json.getJSONArray("changes")
            for (i in 0 until changes.length()) {
                android.util.Log.d("PhotoSync", "Processing change $i: ${changes.getJSONObject(i).optString("id")}")
                val change = changes.getJSONObject(i)
                val id = change.getString("id")
                if (change.optBoolean("deleted", false)) {
                    // Soft-delete locally
                    db.photoDao().getById(id)?.let { db.photoDao().update(it.copy(isTrashed = true, trashedAtMs = System.currentTimeMillis())) }
                } else {
                    // Download thumbnail if not exists
                    val photo = db.photoDao().getById(id)
                    if (photo == null) {
                        // New photo from server — download
                        downloadPhoto(baseUrl, token, change)
                    } else {
                        // Apply metadata changes (favorite/archive/trash) for existing photos
                        db.photoDao().update(photo.copy(
                            isFavorite = change.optBoolean("is_favorite", photo.isFavorite),
                            isArchived = change.optBoolean("is_archived", photo.isArchived),
                            isTrashed = change.optBoolean("is_trashed", photo.isTrashed),
                            updatedAtMs = change.optLong("updated_at", photo.updatedAtMs)
                        ))
                    }
                }
            }
            cursor = json.getLong("next_cursor")
            hasMore = json.optBoolean("has_more", false)
        }
        // Update cursor
        db.photoSyncAccountDao().update(account.copy(cursor = cursor))
        // Ack
        val ackBody = JSONObject().apply {
            put("device_id", "hearthboard")
            put("cursor", cursor)
        }
        val ackReq = Request.Builder()
            .url("$baseUrl/api/sync/ack")
            .header("Authorization", "Bearer $token")
            .post(ackBody.toString().toByteArray().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(ackReq).execute()
    }

    private suspend fun downloadPhoto(baseUrl: String, token: String, change: JSONObject) {
        val id = change.getString("id")
        val contentHash = change.optString("content_hash", "")
        // Check dedup
        if (contentHash.isNotEmpty()) {
            db.photoDao().getByHash(contentHash)?.let { return } // already have it
        }
        // Download the full-resolution content (thumbnail would degrade the photo)
        val ext = change.optString("mime_type", "image/jpeg").substringAfterLast('/').takeIf {
            setOf("jpeg", "png", "webp", "gif").contains(it)
        } ?: "jpg"
        val req = Request.Builder()
            .url("$baseUrl/api/photos/$id/content")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) return
        val bytes = resp.body!!.bytes()
        val dir = File(applicationContext.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "$id.$ext")
        file.writeBytes(bytes)
        android.util.Log.d("PhotoSync", "Downloaded photo $id to ${file.absolutePath} (${bytes.size} bytes)")
        val photo = com.openlight.cal.data.model.Photo(
            id = id,
            contentHash = contentHash,
            localPath = file.absolutePath,
            remoteId = id,
            width = change.optInt("width", 0),
            height = change.optInt("height", 0),
            takenAtMs = change.optLong("taken_at", 0),
            importedAtMs = System.currentTimeMillis(),
            sizeBytes = bytes.size.toLong(),
            mimeType = change.optString("mime_type", "image/jpeg"),
            syncState = com.openlight.cal.data.model.PhotoSyncState.SYNCED,
            updatedAtMs = change.optLong("updated_at", System.currentTimeMillis())
        )
        db.photoDao().insert(photo)
    }

    private fun isOnWifi(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private suspend fun pushAlbums(baseUrl: String, token: String) {
        val albumDao = db.albumDao()
        val albums = albumDao.getAll()
        for (album in albums) {
            if (album.remoteId.isNotBlank()) continue
            try {
                val body = org.json.JSONObject().apply { put("name", album.name) }
                val req = Request.Builder()
                    .url("$baseUrl/api/albums")
                    .header("Authorization", "Bearer $token")
                    .post(body.toString().toByteArray().toRequestBody("application/json".toMediaType()))
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val json = org.json.JSONObject(resp.body?.string().orEmpty())
                    val rid = json.optString("id", "")
                    if (rid.isNotBlank()) albumDao.update(album.copy(remoteId = rid, updatedAtMs = System.currentTimeMillis()))
                }
            } catch (_: Exception) { }
        }
    }

    private suspend fun pullAlbums(baseUrl: String, token: String) {
        val albumDao = db.albumDao()
        try {
            val req = Request.Builder()
                .url("$baseUrl/api/albums")
                .header("Authorization", "Bearer $token")
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return
            val arr = org.json.JSONArray(resp.body?.string().orEmpty())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val rid = o.getString("id")
                val name = o.getString("name")
                // skip if already present by remoteId
                if (albumDao.getByRemoteId(rid) != null) continue
                val album = com.openlight.cal.data.model.Album(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    createdAtMs = System.currentTimeMillis(),
                    updatedAtMs = System.currentTimeMillis(),
                    remoteId = rid
                )
                albumDao.insert(album)
            }
        } catch (_: Exception) { }
    }


    private suspend fun pushMetadataChanges(baseUrl: String, token: String, account: PhotoSyncAccount) {
        val changed = db.photoDao().getAll().filter {
            it.syncState == PhotoSyncState.SYNCED && it.remoteId.isNotBlank() && it.updatedAtMs > account.lastSyncMs
        }
        if (changed.isEmpty()) return
        val items = org.json.JSONArray()
        for (p in changed) {
            items.put(org.json.JSONObject().apply {
                put("id", p.remoteId)
                put("content_hash", p.contentHash)
                put("width", p.width)
                put("height", p.height)
                put("taken_at", p.takenAtMs)
                put("imported_at", p.importedAtMs)
                put("size_bytes", p.sizeBytes)
                put("mime_type", p.mimeType)
                put("is_favorite", p.isFavorite)
                put("is_archived", p.isArchived)
                put("is_trashed", p.isTrashed)
                put("caption", p.caption)
                put("updated_at", p.updatedAtMs)
            })
        }
        val body = org.json.JSONObject().apply { put("items", items) }
        try {
            val req = Request.Builder()
                .url("$baseUrl/api/sync/push")
                .header("Authorization", "Bearer $token")
                .post(body.toString().toByteArray().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute()
        } catch (_: Exception) { }
    }

    companion object {
        const val WORK_NAME_PERIODIC = "photo_sync_periodic"

        fun schedulePeriodicSync(context: Context, intervalMinutes: Long = 30) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()
            val request = androidx.work.PeriodicWorkRequestBuilder<PhotoSyncWorker>(
                intervalMinutes, java.util.concurrent.TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun syncNow(context: Context) {
            val req = androidx.work.OneTimeWorkRequestBuilder<PhotoSyncWorker>()
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "photo_sync_now", androidx.work.ExistingWorkPolicy.KEEP, req
            )
        }
    }

}


object PhotoSyncAuth {
    fun login(context: Context, baseUrl: String, username: String, passwordEncrypted: String): String {
        val password = EncryptedPassword(context).decrypt(passwordEncrypted)
        val reqBody = JSONObject().apply {
            put("username", username)
            put("password", password)
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        val req = Request.Builder()
            .url("$baseUrl/api/auth/login")
            .post(reqBody.toString().toByteArray().toRequestBody("application/json".toMediaType()))
            .build()
        val resp = client.newCall(req).execute()
        val body = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) throw Exception("Login failed: ${resp.code} $body")
        return JSONObject(body).getString("access_token")
    }
}
