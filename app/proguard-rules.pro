# Hearthboard ProGuard rules
#
# R8 removes anything that looks unused. Room, serialization, and
# Compose navigation use reflection at runtime — keep those paths.

# ── Room entities + DAOs + database ──────────────────────────
-keep class com.openlight.cal.data.model.** { *; }
-keep class com.openlight.cal.data.db.** { *; }
-keep class com.openlight.cal.data.db.dao.** { *; }

# ── Room type converters (called via reflection) ──────────────
-keep class com.openlight.cal.data.db.Converters { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter <methods>;
}

# ── Preferences + encryption ─────────────────────────────────
-keep class com.openlight.cal.data.preferences.** { *; }

# ── Repositories + sync + weather + contacts ─────────────────
-keep class com.openlight.cal.data.repository.** { *; }
-keep class com.openlight.cal.data.sync.** { *; }
-keep class com.openlight.cal.data.weather.** { *; }
-keep class com.openlight.cal.data.contacts.** { *; }

# ── Application class (manual DI entry point) ────────────────
-keep class com.openlight.cal.HearthboardApp { *; }

# ── OkHttp (used via reflection in some config paths) ────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ── Kotlin coroutines internals ───────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }

# ── WorkManager workers ──────────────────────────────────────
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class com.openlight.cal.data.sync.BootReceiver { *; }
-keep class com.openlight.cal.data.sync.CalDAVSyncWorker { *; }

# ── DataStore ────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }

# ── Compose Navigation + ViewModels ──────────────────────────
-keep class * extends androidx.lifecycle.ViewModel
-keep class * extends androidx.lifecycle.AndroidViewModel

# ── No analytics / tracking SDKs to worry about — there are none.
