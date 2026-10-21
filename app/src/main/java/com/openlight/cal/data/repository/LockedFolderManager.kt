package com.openlight.cal.data.repository

import android.content.Context
import android.content.SharedPreferences
import java.io.File

class LockedFolderManager(private val context: Context) {
    private val lockedDir = File(context.filesDir, "locked_photos").apply { mkdirs() }
    private val prefs: SharedPreferences = context.getSharedPreferences("locked_folder", Context.MODE_PRIVATE)

    fun isSetUp(): Boolean = prefs.getBoolean("setup_done", false)
    fun hasPin(): Boolean = prefs.getString("pin_hash", null) != null

    fun setPin(pin: String) {
        prefs.edit().putString("pin_hash", pin.hashCode().toString()).putBoolean("setup_done", true).apply()
    }

    fun verifyPin(pin: String): Boolean {
        return prefs.getString("pin_hash", null) == pin.hashCode().toString()
    }

    fun getLockedDir(): File = lockedDir
    fun listLockedPhotos(): List<File> = lockedDir.listFiles()?.toList() ?: emptyList()

    fun movePhotoToLocked(photoFile: File): File {
        val dest = File(lockedDir, photoFile.name)
        photoFile.renameTo(dest)
        return dest
    }

    fun movePhotoFromLocked(photoFile: File): File {
        val dest = File(context.filesDir, "photos/${photoFile.name}")
        photoFile.renameTo(dest)
        return dest
    }
}
