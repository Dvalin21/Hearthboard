# Hearthboard Handoff Document
**Last Updated:** June 11, 2026 | **Commit:** `4560f21` | **Branch:** `main`

---

## Project Overview
**Hearthboard** - Privacy-first, open-source family calendar and task manager for Android 8+ (minSdk 31)
- CalDAV/VTODO sync (Google, Fastmail, Nextcloud, iCloud, any CalDAV)
- Material 3 adaptive design (phones + tablets)
- Zero telemetry, no Google Play Services required
- GPL-3.0, F-Droid compatible

---

## Current Implementation Status: ✅ COMPLETE

All 8 phases of Skylight Calendar 2 spec implementation done.

### Architecture
```
HearthboardApp (Application)
├── data/
│   ├── model/          Room entities (Person, CalendarEvent, Task, etc.)
│   ├── db/             Room database + DAOs
│   ├── repository/     CalendarRepository, TaskRepository, etc.
│   ├── preferences/    DataStore + AES-256/GCM password encryption
│   └── sync/           CalDAVClient, ICalParser, WorkManager sync
└── ui/
    ├── components/     Shared composables
    ├── navigation/     Adaptive nav rail / bottom bar
    ├── viewmodel/      State holders (StateFlow)
    ├── screens/        Calendar, Tasks, Chores, Lists, Meals, People, Recipes, Settings
    └── theme/          Material 3 dynamic/seed theming
```

---

## Key Files Modified (Recent)

| File | Purpose |
|------|---------|
| `Navigation.kt` | Adaptive rail + bottom nav, old English initial, people→settings |
| `CalendarScreen.kt` | Header, filter row, week view (5 days), month view, filter sheet |
| `CalendarViewModel.kt` | taskChoreCounts, nextVacationCountdown flows |
| `Daos.kt` | Task count queries per person |
| `Repositories.kt` | TaskRepository count methods |
| `AppPreferences.kt` | familyName, tempUnit (C/F) |
| `SettingsViewModel.kt` | Exposed familyName, tempUnit |
| `SettingsScreen.kt` | Family name field, temperature unit toggle |
| `SKYLIGHT_IMPLEMENTATION_PLAN.md` | Detailed spec document |

---

## Current Feature Status

### ✅ Navigation
- **Rail (tablet/landscape):** 80dp, grayish bg, icons ABOVE labels, old English initial above Calendar, white rounded pill active
- **Bottom Nav (phone):** Grayish bg, white squared pill active, 4 visible (Calendar, Lists, Tasks, Chores), More sheet for rest
- **Order:** Calendar → Lists → Tasks → Chores → Rewards → Meals → Recipes → Photos → Sleep → Divider → Settings

### ✅ Calendar Views
- **Header (80dp):** "The [LastName] Family" serif + live clock + weather icon + temp (C/F) + Schedule + Filter
- **Controls:** Today + Prev/Next left, Schedule + Filter right (no view tabs)
- **Filter Row:** Vacation countdown | All | Name N (actual active counts!)
- **Month View:** 6-week grid, Mon-Sun 3-letter headers, orange today dot, pastel event blocks + person badges
- **Week View:** 5 days (current day first), bold day headers, rounded event blocks
- **Day View:** Vertical time, rounded event blocks
- **Filter Sheet:** View mode + Person filter

### ✅ Filter Row (NEW)
- **Vacation chip:** "Vacation: X days" from countdown events
- **All chip:** Show all
- **Person chips:** "Name N" where N = active tasks + chores assigned (from DB)
- Colored dots per person color

### ✅ Dark Mode
- All colors via `MaterialTheme.colorScheme` (surfaces, text, primary, outlineVariant, primaryContainer)

### ✅ All Features Preserved
- CalDAV sync (events + VTODO tasks)
- Mealie recipe sync + meal planning + grocery lists
- Chores (kid-friendly, star rewards)
- Lists (color-coded checklists)
- Meals (weekly planner)
- People management
- Recipes (Mealie + local)
- Wall Mode (kiosk display)
- Settings (accounts, theme, PIN, weather, etc.)

---

## Build & Release
```bash
./gradlew assembleDebug  # BUILD SUCCESSFUL
```
- **APK:** `app/build/outputs/apk/debug/app-debug.apk` (~64MB)
- **Release:** https://github.com/Dvalin21/Hearthboard/releases/tag/alpha-latest
- **Minor warnings only:** `MenuBook` → `AutoMirrored.MenuBook`, `Divider` → `HorizontalDivider` (non-blocking)

---

## Next Session Priorities

If continuing work:

1. **Polish/Testing:**
   - Test CalDAV sync with real accounts (Google, Fastmail, Nextcloud)
   - Test Mealie sync + meal planning flow
   - Test Chores star rewards + kids UI
   - Test Wall Mode on tablet

2. **Potential Enhancements:**
   - Add more weather condition icons (thunderstorm, snow, fog)
   - Implement actual vacation event detection (dedicated "vacation" calendar type vs title matching)
   - Person-specific weather/location in filter row
   - Animation polish for filter sheet, rail transitions

3. **F-Droid Preparation:**
   - Screenshots (tablet 1280x800+)
   - Metadata in `app/src/main/play/listings/en-US/`
   - Fastlane config for automated builds

---

## Environment
- **JDK 17+**, Android SDK 34, Gradle 8.7 (wrapper)
- **Build:** `./gradlew assembleDebug`
- **Install:** `adb install app/build/outputs/apk/debug/app-debug.apk`

---

## Key Context for Next Agent
- User wants **pixel-perfect Skylight Calendar 2 fidelity** - refer to `SKYLIGHT_IMPLEMENTATION_PLAN.md`
- **Don't strip features** - every screen/function must remain
- **Think logically** about how picture elements map to code
- User is **Keith (Dvalin21)**, prefers caveman/blunt communication, Linus-style code review
- All clones go to `~/host/<repo>/`, never `/tmp` or random dirs