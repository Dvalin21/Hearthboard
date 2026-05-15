# OpenLight ProGuard rules

# Keep Room entities
-keep class com.openlight.cal.data.model.** { *; }
-keep class com.openlight.cal.data.db.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker

# Keep DataStore
-keep class androidx.datastore.** { *; }

# Prevent stripping iCal parser
-keep class com.openlight.cal.data.sync.** { *; }

# No analytics / tracking SDKs to worry about — there are none.
