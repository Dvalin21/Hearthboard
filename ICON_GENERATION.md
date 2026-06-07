# Icon Generation for F-Droid

## Current State
App uses Adaptive Icon (XML):
- `app/src/main/res/mipmap/ic_launcher.xml` - adaptive icon definition
- `app/src/main/res/drawable/ic_launcher_foreground.xml` - vector foreground (heart/calendar symbol)
- `app/src/main/res/values/colors.xml` - `ic_launcher_background` color

## Required F-Droid Assets

### High-res Icon (512x512 PNG)
Path: `fastlane/metadata/android/en-US/graphics/icon.png`

**Generation options:**
1. **Android Studio**: Right-click `res` → New → Image Asset → Legacy → keep 512x512 as output
2. **Command line** (requires `aapt2` and `convert`):
   ```bash
   # Build debug APK first
   ./gradlew assembleDebug
   
   # Extract icon from APK
   aapt2 dump resources app/build/outputs/apk/debug/app-debug.apk | grep ic_launcher
   # Or use apktool:
   apktool d app/build/outputs/apk/debug/app-debug.apk -o /tmp/app
   convert /tmp/app/res/mipmap-xxxhdpi/ic_launcher.png -resize 512x512 fastlane/metadata/android/en-US/graphics/icon.png
   ```

### Feature Graphic (1024x500 PNG)
Path: `fastlane/metadata/android/en-US/graphics/featureGraphic.png`
- Design in Figma/Inkscape/GIMP
- Include app name "HearthBoard"
- Show calendar + family + privacy tagline
- Background: theme seed color (#4A6178 or user's chosen seed)
- No text smaller than 24pt

### Promo Graphic (180x120 PNG) - optional
Path: `fastlane/metadata/android/en-US/graphics/promoGraphic.png`

## Quick Generation (one-time, after next successful build)
```bash
cd ~/host/Hearthboard
./gradlew assembleDebug

# If you have ImageMagick and the APK has mipmap-xxxhdpi:
# (adaptive icons don't produce mipmap-XXXhdpi PNGs by default)

# Better: Use Android Studio's Image Asset Studio:
# 1. Open project in Android Studio
# 2. Right-click res folder → New → Image Asset
# 3. Asset Type: "Launcher Icons (Adaptive and Legacy)"
# 4. Legacy tab: enable, set size to 512x512
# 5. Output to: fastlane/metadata/android/en-US/graphics/icon.png

# For feature graphic, create manually in design tool.
```

## Placeholder Files
The following are placeholder paths - replace with real assets:
- `fastlane/metadata/android/en-US/graphics/icon.png` (generate from adaptive icon)
- `fastlane/metadata/android/en-US/graphics/featureGraphic.png` (design)
- `fastlane/metadata/android/en-US/graphics/promoGraphic.png` (optional, design)

## Screenshots
See `SCREENSHOT_GUIDE.md` for capture instructions.
