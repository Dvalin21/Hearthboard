plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.openlight.cal"
    compileSdk = 34

    // ── Signing ────────────────────────────────────────────────
    // Release signing reads from env vars (CI or a local release build):
    //   RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD,
    //   RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD
    // With no key set, assembleRelease produces an *unsigned* APK, which is
    // what F-Droid builds and signs itself. It must never fall back to the
    // debug keystore: that key is public, so anyone could sign an "update"
    // that Android would happily install over ours.
    signingConfigs {
        val storeFilePath = System.getenv("RELEASE_STORE_FILE")
        if (!storeFilePath.isNullOrBlank()) {
            create("release") {
                storeFile     = file(storeFilePath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias      = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword   = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.openlight.cal"
        minSdk = 31          // Android 12+ (was 26 for Android 8.0)
        targetSdk = 34
        versionCode = 41
        versionName = "1.0.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schema export for migrations
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeCompiler {
        // Strong Skipping Mode: composables with unstable params (like List<T>)
        // still skip via equality comparison. Available since Compose Compiler 1.5.1,
        // default since Kotlin 2.0.20. Explicit here for 2.0.0.
        enableStrongSkippingMode = true
    }

    // Adaptive layouts for large screens (tablets 14.5")
    splits {
        abi {
            isEnable = false
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Core desugaring for Java 8+ APIs on older Android
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room (local DB)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (settings persistence)
    implementation(libs.androidx.datastore.preferences)

    // WorkManager (background CalDAV sync)
    implementation(libs.androidx.work.runtime.ktx)

    // Adaptive / large screen support
    implementation(libs.androidx.window)
    implementation(libs.androidx.adaptive)

    // OkHttp (CalDAV HTTP requests)
    implementation(libs.okhttp)
    implementation(libs.osmdroid)
    implementation(libs.androidx.exifinterface)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)

    // Test
    testImplementation(libs.junit)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("xpp3:xpp3:1.1.4c")
    testImplementation("org.robolectric:robolectric:4.13")

    // Instrumented tests
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
