# F-Droid Screenshot Capture Guide

## Required Screenshots for F-Droid Listing

### Phone Screenshots (1080x1920 or similar 9:16 ratio)
1. `phone_01_calendar_month.png` - Month view with events
2. `phone_02_calendar_week.png` - Week view
3. `phone_03_calendar_day.png` - Day view
4. `phone_04_calendar_agenda.png` - Agenda view
5. `phone_05_tasks.png` - Tasks screen with chores/stars
6. `phone_06_people.png` - People management
7. `phone_07_meals.png` - Meal planner
8. `phone_08_recipes.png` - Recipes with Mealie sync
9. `phone_09_settings.png` - Settings screen
10. `phone_10_setup.png` - CalDAV account setup

### Tablet Screenshots (1200x1920 or 4:3 ratio for 8-10" tablets)
1. `tablet_01_calendar_rail.png` - Month view with nav rail
2. `tablet_02_calendar_week.png` - Week view on tablet
3. `tablet_03_wall_mode.png` - Wall/kiosk mode active
4. `tablet_04_tasks_tablet.png` - Tasks with chore chart

## How to Capture

### On the physical tablet (device 32112536954):
```bash
# Screenshot to device
adb -s 32112536954 shell screencap -p /sdcard/screenshot.png
# Pull to host
adb -s 32112536954 pull /sdcard/screenshot.png ./fastlane/metadata/android/en-US/screenshots/phone_01_calendar_month.png

# For tablet layout, ensure app is in landscape or use tablet AVD
adb -s 32112536954 shell am start -n com.openlight.cal/.MainActivity
```

### Using Android Studio Device Mirroring or scrcpy:
```bash
scrcpy -s 32112536954
# Use scrcpy's screenshot button (Ctrl+Shift+S) or menu
```

## Graphics Requirements

### Feature Graphic (1024x500)
- Path: `fastlane/metadata/android/en-US/graphics/featureGraphic.png`
- Shows app name, key features visually
- No text smaller than 24pt

### App Icon (512x512)
- Path: `fastlane/metadata/android/en-US/graphics/icon.png`
- Can reuse mipmap/ic_launcher at 512x512
- `convert app/src/main/res/mipmap-xxxhdpi/ic_launcher.png -resize 512x512 fastlane/metadata/android/en-US/graphics/icon.png`

### Promo Graphic (optional, 180x120)
- Path: `fastlane/metadata/android/en-US/graphics/promoGraphic.png`

## F-Droid Specific Notes
- All screenshots must be PNG format
- No device frames, no status bars (use `adb shell wm dismiss-keyguard` then capture)
- English locale screenshots go in `en-US/screenshots/`
- For other locales, create `fastlane/metadata/android/<locale>/screenshots/`

## Automated Capture (future)
Consider adding a screenshot test using Compose Preview Screenshot Testing or Robolectric for CI.
