# OpenLight — Open-Source Family Calendar

**A privacy-first, open-source family calendar and task manager for Android 8+.**
Inspired by Skylight Calendar. Zero telemetry. Fully CalDAV/VTODO compatible.

---

## Features

| Feature | Details |
|---|---|
| **Calendar views** | Month, Week, Day, Agenda |
| **Countdown timers** | Show "X days until…" widgets for special events |
| **Tasks (VTODO)** | Full task management, synced via CalDAV |
| **Checklists** | Color-coded shopping/to-do lists |
| **Meal planner** | Weekly breakfast/lunch/dinner/snack grid |
| **People** | Per-person color coding, all sizes 5.5"–14.5" |
| **CalDAV sync** | Google, Fastmail, Nextcloud, iCloud, any CalDAV |
| **Material 3** | Dynamic theming, light + dark mode |
| **Launcher/Kiosk** | Lock device to OpenLight (tablet kiosk mode) |
| **Parental PIN** | Restrict settings access |
| **Zero telemetry** | No analytics, no crash reporters, no phone-home |
| **F-Droid ready** | GPL-3.0, no proprietary SDKs |

---

## Requirements

- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK 17** (bundled with Android Studio)
- **Android SDK** with API 34 platform

---

## Build Instructions

### 1. Clone / unzip the project

```bash
unzip OpenLight.zip -d OpenLight
cd OpenLight
```

### 2. Open in Android Studio

File → Open → select the `OpenLight` folder.
Let Gradle sync finish (~2–5 minutes first time).

### 3. Build debug APK

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### 4. Build release APK (for sideloading / F-Droid)

First, create a signing keystore (one-time):

```bash
keytool -genkey -v \
  -keystore openlight-release.jks \
  -alias openlight \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Add signing config to `app/build.gradle.kts`:

```kotlin
signingConfigs {
    create("release") {
        storeFile     = file("../openlight-release.jks")
        storePassword = "YOUR_STORE_PASS"
        keyAlias      = "openlight"
        keyPassword   = "YOUR_KEY_PASS"
    }
}
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        ...
    }
}
```

Then build:

```bash
./gradlew assembleRelease
```

APK output: `app/build/outputs/apk/release/app-release.apk`

### 5. Install on a device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Tablet Kiosk Setup (Android 12 tablets)

1. Install OpenLight
2. Open **Settings → Advanced → Kiosk / Launcher Mode** → toggle ON
3. A system prompt will ask to set OpenLight as the **default home app**
4. Optionally set a **Parental Lock PIN** to prevent kids accessing settings

OpenLight intercepts the back button in kiosk mode to prevent leaving the app.

---

## CalDAV Account Setup

| Provider | Server URL |
|---|---|
| Google Calendar | `https://apidata.googleusercontent.com/caldav/v2/` |
| Fastmail | `https://caldav.fastmail.com/` |
| Nextcloud | `https://YOUR_SERVER/remote.php/dav/` |
| iCloud | `https://caldav.icloud.com/` |
| Proton Calendar | `https://calendar.proton.me/` |
| Any CalDAV server | Your server URL |

**Google**: Use an [App Password](https://myaccount.google.com/apppasswords) — not your main password.

**Nextcloud**: Settings → Security → Devices & Sessions → create an app token.

**VTODO (Tasks)**: Any CalDAV server that supports the `VTODO` component will sync tasks automatically. This includes Nextcloud Tasks, Fastmail, and DAViCal.

---

## Screen Size Compatibility

| Screen Size | Layout |
|---|---|
| 5.5" – 6.7" (phones) | Bottom navigation bar |
| 7" – 14.5" (tablets) | Left Navigation Rail |

The layout switches automatically using Jetpack `WindowSizeClass`.

---

## Architecture

```
OpenLightApp (Application class)
├── data/
│   ├── model/          Room entities (Person, CalendarEvent, Task, etc.)
│   ├── db/             Room database + DAOs
│   ├── repository/     Data access layer
│   ├── preferences/    DataStore settings
│   └── sync/           CalDAVClient, ICalParser, SyncWorker
└── ui/
    ├── theme/          Material 3 theme
    ├── components/     Shared composables
    ├── navigation/     Adaptive nav (Rail + BottomBar)
    ├── viewmodel/      State holders
    └── screens/
        ├── calendar/   Month/Week/Day/Agenda + EventEditDialog
        ├── tasks/      Task list + TaskEditDialog
        ├── lists/      Color-coded checklists
        ├── meals/      Weekly meal planner
        ├── people/     Person management with color picker
        └── settings/   Full settings + AccountEditDialog
```

---

## Privacy Policy

**OpenLight collects zero data. Period.**

- No analytics SDK
- No crash reporter
- No network requests except to your own CalDAV server
- No Google Play Services dependency
- No Firebase
- Passwords stored base64-encoded locally (upgrade path: Android Keystore)

---

## License

GPL-3.0 — Free and open source software.
See [LICENSE](LICENSE) for full text.

---

## Contributing

PRs welcome. See `CONTRIBUTING.md`.
Build passes on Android Studio Hedgehog / Gradle 8.7 / AGP 8.5.2.
