# Skylight Calendar 2 - Pixel-Perfect Implementation Plan
## Based on detailed picture analysis (cdn.shopify.com/s/files/.../Q1_2026-Cal2_Classic_WHT_PDP-listing-images_00-nobadgewithplus.jpg)

---

## PICTURE ANALYSIS - KEY OBSERVATIONS

### Navigation Rail (Left Sidebar - Tablet/Landscape)
- **Width**: ~80dp, grayish background (not white)
- **Icon + Label Layout**: Icons ABOVE labels (vertical stack), not side-by-side
- **Single-word titles**: "Calendar", "Lists", "Tasks", "Chores", "Rewards", "Meals", "Recipes", "Photos", "Sleep"
- **Chores icon**: DISTINCT from Tasks (not checkmark)
- **Active state**: Squared white background behind icon+label, connecting to content area
- **Inactive**: Gray icons/labels
- **Above Calendar icon**: Single letter (first initial of admin's first name) in OLD ENGLISH font
- **Icons moved down** to accommodate this letter
- **Divider** before Sleep, then People/Settings below divider
- **People moved to Settings** (not in rail)

### Top Header (80dp height)
- **Format**: "The [LastName] Family" (e.g., "The May Family") - NOT "Miller Family"
- **Font**: Serif / Old English style for family name
- **Live clock**: Right of family name
- **Weather**: Temperature with ICON (sun, sun+clouds, clouds, rain, etc.)
- **Temperature unit**: Celsius/Fahrenheit toggle in settings
- **Right side**: Schedule button + Filter button ONLY
- **NO**: View mode tabs (Mo/Wk/Dy/Ag), NO profile avatar

### Filter Row (Below Header, 48dp)
- **Horizontal scrollable chips/cards**
- **Content**: Task/Chore countdowns going across
  - Vacation countdown: "Vacation: 48 days"
  - Account chips: "Dad 1/20", "Elle 1/20", etc. (name + chore/task count)
- **NOT**: Person filter chips with colored dots
- **Style**: Rounded, 32dp tall, colored badges

### Week View (Main Content - 5 Days Visible)
- **5 days at all times** (not 7)
- **Current day first** (leftmost)
- **Day headers**: BOLD, BIGGER font (not 12sp single letters)
- **Format**: "Mon 15", "Tue 16", etc. (3-letter day + date)
- **Today indicator**: Orange dot/ring
- **Everything ROUNDED**: Event blocks, day cells, headers
- **Event blocks**: Pastel colors, time range "9:45 - 11AM", person initial badge
- **All-day strip**: At top
- **Hour labels**: Left gutter, 6am-11pm (or 0-23)

### Bottom Navigation Bar (Phone/Compact)
- **Background**: Grayish (#F5F5F5 or similar), NOT white
- **Active indicator**: SQUARED white background behind icon+label (pill shape connecting to bottom)
- **Inactive**: Gray icons/labels
- **No purple square** (current implementation wrong)
- **Items**: Calendar, Lists, Tasks, Chores (first 4)
- **More sheet**: Rewards, Meals, Recipes, Photos, Sleep, Settings
- **People REMOVED** from nav entirely → moved to Settings

### Sleep Button Position
- In rail: Above Settings (after divider)
- In bottom nav: In "More" sheet

---

## IMPLEMENTATION PHASES

### Phase 1: Navigation Rail Restructure
**Files**: `Navigation.kt`
- Change rail item layout: Column(Icon + Text) vertical stack
- Single-word labels
- Chores icon: `Icons.Filled.LocalLaundry` or `Icons.Filled.HomeRepairService` (distinct from Tasks checkmark)
- Active state: Rounded white background pill covering icon+label, full width
- Inactive: Transparent, gray icon/label
- Add letter above Calendar: First initial of admin (first name), Old English font (`FontFamily.Serif` with custom or `FontFamily.Monospace` fallback)
- Move icons down to accommodate letter
- Grayish rail background: `Color(0xFFF5F5F5)` or `MaterialTheme.colorScheme.surfaceContainerHighest`
- Divider before Sleep, People+Settings below divider
- Remove People from rail entirely

### Phase 2: Header Redesign
**Files**: `CalendarScreen.kt` (SkylightHeader), `AppPreferences.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`
- Family name: "The [LastName] Family" from admin person's last name
- Serif/Old English font for family name (20sp Bold)
- Live clock (14sp)
- Weather: Icon + temperature (e.g., "☀️ 72°" or "⛅ 68°")
- Temperature unit preference (C/F) in Settings → Appearance
- Right side: Schedule button (text) + Filter button (icon)
- Remove: View mode tabs, profile avatar
- Height: 80dp, padding 16dp horizontal

### Phase 3: Filter Row (Task/Chore Countdowns)
**Files**: `CalendarScreen.kt` (SkylightPersonFilterRow → SkylightTaskFilterRow), `CalendarViewModel`
- Replace person chips with task/chore countdown chips
- Data source: Query active chores/tasks per person, vacation events
- Chip format: "Vacation: 48 days" (no dot) + "Dad 3/10" (with count)
- Scrollable horizontal row, 32dp tall, 16dp radius
- Colors: Person's color for dot/badge

### Phase 4: Top Right Controls
**Files**: `CalendarScreen.kt` (CalendarControls)
- Schedule button: Text "Schedule", primary color
- Filter button: Icon `Icons.Filled.FilterList` or similar
- NO view mode tabs (Mo/Wk/Dy/Ag)
- NO profile avatar
- View mode switching: Via Filter button bottom sheet or long-press?

### Phase 5: Week View - 5 Day Layout
**Files**: `CalendarScreen.kt` (SkylightWeekView)
- Show exactly 5 days: current day + next 4
- Day headers: Bold, larger (14-16sp), format "Mon 15"
- Current day: Orange accent
- Day cells: Rounded corners (12dp)
- Event blocks: Pastel, rounded (8dp), time range "9:45 - 11AM", person badge
- All-day strip: Rounded chips
- Hour gutter: 48dp wide, 6am-11pm default
- Everything uses theme colors (dark mode compatible)

### Phase 6: Bottom Navigation Bar
**Files**: `Navigation.kt` (compact mode Scaffold)
- Background: Grayish `Color(0xFFF5F5F5)` / `surfaceContainerHighest`
- Active item: White squared pill background (rounded corners) covering icon+label
- Inactive: Gray icon/label
- Items: Calendar, Lists, Tasks, Chores (4 visible)
- More sheet: Rewards, Meals, Recipes, Photos, Sleep, Settings
- People: REMOVED from nav → Settings only
- Sleep: In More sheet (above Settings)

### Phase 7: Settings Updates
**Files**: `AppPreferences.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`
- Add: Temperature unit (C/F), Admin person selection (for family name)
- Move People management to Settings (already there)
- Family name derived from admin person's last name

### Phase 8: Dark Mode & Polish
- All colors via `MaterialTheme.colorScheme`
- Test dark mode: rail background, header, event blocks, nav bar
- Rounded corners consistent (12dp cells, 8dp events, 16dp chips)
- Old English font for initial: `FontFamily.Serif` with large size

---

## DATA MODEL CHANGES

### AppPreferences Additions
```kotlin
val KEY_TEMP_UNIT = stringPreferencesKey("temp_unit") // "C" or "F"
val KEY_ADMIN_PERSON_ID = longPreferencesKey("admin_person_id") // for family name
```

### CalendarViewModel Additions
- `taskFilterCounts`: Map<PersonId, Int> for active chores/tasks
- `vacationCountdown`: Int days until next vacation event
- `adminPerson`: Person for family name initial + last name

---

## VISUAL SPEC SUMMARY

| Element | Current (Wrong) | Target (Picture) |
|---------|-----------------|------------------|
| Rail layout | Icon + Label horizontal | Icon ABOVE Label vertical |
| Rail active | Purple square 48dp | White rounded pill full-width |
| Rail bg | White | Grayish (#F5F5F5) |
| Calendar letter | None | Old English initial above icon |
| Family name | "Miller Family" | "The [LastName] Family" |
| Header right | Mo/Wk/Dy/Ag tabs + avatar | Schedule btn + Filter btn |
| Filter row | Person chips w/ dots | Task/Chore countdowns |
| Week days | 7 single letters | 5 bold "Mon 15" format |
| Nav bar bg | White | Grayish |
| Nav active | Purple square | White squared pill |
| People in nav | Yes | No (→ Settings) |
| Sleep position | Below divider | Above Settings (in More) |

---

## EXECUTION ORDER

1. **Navigation.kt** - Rail + Bottom nav (Phases 1, 6)
2. **CalendarScreen.kt** - Header, Filter row, Week view (Phases 2, 3, 4, 5)
3. **AppPreferences.kt** + **SettingsViewModel.kt** - New prefs (Phase 7 prep)
4. **SettingsScreen.kt** - Temperature unit, admin person (Phase 7)
5. **CalendarViewModel.kt** - Task/chore counts, admin person
6. **Integration testing** - Build, verify, release

---

## NOTES
- Preserve ALL existing features: CalDAV sync, Mealie, Chores, Recipes, etc.
- Old English font: Use `FontFamily.Serif` with `fontWeight = FontWeight.Bold`, `fontSize = 28.sp` for initial
- Temperature icons: Map weather conditions to icons (sun, cloud, rain, snow, etc.)
- Vacation detection: Events with "vacation" in title or specific calendar
- Admin person: First non-default person created, or configurable in Settings