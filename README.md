# OpenLight — Open-Source Family Calendar

**A privacy-first, open-source family calendar and task manager for Android 8+.**

CalDAV/VTODO sync. Material 3 adaptive design. Zero telemetry. GPL-3.0.

---

## Features

| Feature | Details |
|---|---|
| **Calendar views** | Month, Week, Day, Agenda — adaptive to screen size |
| **Tasks (VTODO)** | Full task management with priority, assignment, CalDAV sync |
| **Countdown timers** | Show "X days until…" for special events |
| **Checklists** | Color-coded shopping, to-do, grocery lists |
| **Meal planner** | Weekly breakfast/lunch/dinner/snack grid per person |
| **People** | Per-person color coding and task assignment |
| **CalDAV sync** | Google Calendar, Fastmail, Nextcloud, iCloud, any CalDAV server |
| **Delta sync** | CTag/ETag-based — only fetches what changed |
| **Material 3** | Light + dark mode, seed color theming |
| **Adaptive layout** | Bottom nav on phones, nav rail on tablets (5.5"–14.5") |
| **Parental PIN** | Optional PIN to restrict settings access |
| **Zero telemetry** | No analytics, no crash reporters, no network calls except to *your* CalDAV server |

---

## Privacy & Security

OpenLight is designed for users who care about who has access to their family's data.

| Property | Detail |
|---|---|
| **No telemetry** | Zero analytics SDKs. No Firebase. No crash reporters. No phone-home. |
| **No Google Play Services** | No dependency on proprietary Google libraries. Works fully on de-Googled devices. |
| **Network** | The only network requests are HTTP calls to your CalDAV server. Nothing else. |
| **Password storage** | AES-256/GCM encryption backed by Android Keystore (hardware-backed on supported devices). Existing base64-stored passwords are transparently upgraded on first access. |
| **Permissions** | Every permission has a documented purpose. No `REQUEST_INSTALL_PACKAGES` or other unnecessary permissions. |
| **F-Droid compatible** | Single universal APK (no ABI split). No proprietary dependencies. GPL-3.0. |

---

## Screenshots

<!-- Add screenshots here before F-Droid submission -->

---

## Requirements

- **JDK 17+**
- **Android SDK** platform 34 + build-tools 34.0.0
- **Gradle 8.7** (wrapped)

---

## Build Instructions

```bash
# Debug APK (unsigned)
./gradlew assembleDebug

# APK location
ls app/build/outputs/apk/debug/app-debug.apk

# Install via ADB
adb install app/build/outputs/apk/debug/app-debug.apk
```

For a signed release APK, create a keystore and add signing config to `app/build.gradle.kts`:

```bash
keytool -genkey -v \
  -keystore openlight-release.jks \
  -alias openlight \
  -keyalg RSA -keysize 2048 -validity 10000
```

Then build with `./gradlew assembleRelease`.

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

**VTODO (Tasks)**: Any CalDAV server that supports the `VTODO` component will sync tasks automatically. Nextcloud Tasks, Fastmail, and DAViCal all support this.

---

## Architecture

```
OpenLightApp (Application class)
├── data/
│   ├── model/          Room entities (Person, CalendarEvent, Task, etc.)
│   ├── db/             Room database + DAOs with singleton lifecycle
│   ├── repository/     Data access layer for events, tasks, people, accounts
│   ├── preferences/    DataStore settings + AES-256/GCM password encryption
│   └── sync/           CalDAVClient (OkHttp), ICalParser, WorkManager sync worker
└── ui/
    ├── theme/          Material 3 dynamic/seed theming
    ├── components/     Shared composables
    ├── navigation/     Adaptive nav rail / bottom bar via WindowSizeClass
    ├── viewmodel/      State holders exposing StateFlow
    └── screens/
        ├── calendar/   Month/Week/Day/Agenda + EventEditDialog
        ├── tasks/      Task list + TaskEditDialog
        ├── lists/      Color-coded checklists
        ├── meals/      Weekly meal planner
        ├── people/     Person management with color picker
        └── settings/   Account management, theme, PIN, sync controls
```

### Key design decisions

- **Manual DI** — No Hilt/Dagger. Keeps the APK small and avoids proprietary annotation processors.
- **Single Room instance** — Both UI screens and background sync workers share one database connection.
- **No destructive migrations** — Schema changes require explicit Room migrations. The app will not silently delete user data.
- **Delta sync** — CalDAV CTag/ETag comparison determines what changed; only modified resources are fetched via REPORT multi-get.

---

## Screen Size Compatibility

| Screen Class | Width | Navigation |
|---|---|---|
| Compact (phones) | < 600dp | Bottom navigation bar |
| Medium (small tablets) | 600–840dp | Navigation rail |
| Expanded (large tablets) | > 840dp | Navigation rail |

The layout adapts automatically via Jetpack `WindowSizeClass`.

---

## Background Sync

OpenLight uses Android `WorkManager` for periodic CalDAV sync:

- **Interval**: Every 30 minutes (configurable per account)
- **Constraints**: Network connection required
- **Backoff**: Exponential backoff starting at 5 minutes on failure
- **Reboot**: Sync reschedules automatically after device restart
- **Manual**: "Sync now" button in Settings triggers immediate one-shot sync

---

## License

GPL-3.0 — Free and open source software.
See [LICENSE](LICENSE) for the full text.

---

## Contributing

PRs welcome. Build passes on JDK 17, AGP 8.5.2, Kotlin 2.0.0.

Code style: Standard Kotlin with 4-space indent. Compose UI follows Material 3 guidelines.
