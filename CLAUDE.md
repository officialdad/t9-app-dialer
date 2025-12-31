# T9 App Dialer - Technical Documentation

## Architecture Overview

Single-activity Android application built with Kotlin, using Material Design 3 components and custom T9 search algorithm.

## Project Structure

```
app/src/main/
├── java/com/t9dialer/
│   └── T9Activity.kt          # Main activity with T9 search logic
├── res/
│   ├── drawable/
│   │   ├── ic_settings.xml           # Settings cog icon (16dp, gray)
│   │   ├── button_ripple.xml         # White ripple effect
│   │   └── button_ripple_red.xml     # Red ripple for CLEAR button
│   ├── layout/
│   │   └── activity_t9.xml           # Main UI layout
│   ├── values/
│   │   ├── colors.xml                # OLED black theme colors
│   │   ├── styles.xml                # T9Key button styles
│   │   └── themes.xml                # Material 3 dark dialog theme
│   └── xml/
│       └── launcher_intent_filter.xml # Launcher category filter
└── AndroidManifest.xml
```

## Key Components

### T9Activity.kt

Main activity handling:
- **App Loading** - Queries PackageManager for launchable apps
- **T9 Search** - Maps numeric input to alphabetic app names
- **Icon Pack Support** - Loads and applies custom icon packs
- **UI Management** - Dynamic app list rendering

#### T9 Mapping Logic

```kotlin
private val t9Map = mapOf(
    '2' to "abc", '3' to "def", '4' to "ghi",
    '5' to "jkl", '6' to "mno", '7' to "pqrs",
    '8' to "tuv", '9' to "wxyz"
)
```

Matches app names by converting both query and app name to T9 sequences, then checking if app name starts with query pattern.

#### Icon Pack Loading

Supports two formats:
1. **Standard XML** - `res/xml/appfilter.xml` (compiled resource)
2. **Icon Pack Studio** - `res/raw/appfilter.xml` (raw resource)

Uses XmlPullParser to parse `<item>` tags with `component` and `drawable` attributes.

### UI Implementation

#### Material Components Used

- `MaterialCardView` - Main container with 16dp rounded corners
- `MaterialButton` - T9 keys with TextButton style (borderless)
- `HorizontalScrollView` - Scrollable app icon container

#### Dual-Color Text Rendering

Uses `SpannableString` with two spans per button:
- **Number** - `ForegroundColorSpan(#FFFFFF)` + `AbsoluteSizeSpan(20sp)`
- **Letters** - `ForegroundColorSpan(#808080)` + `AbsoluteSizeSpan(14sp)`

Applied via `setKeyText()` helper function in onCreate.

#### Ripple Effects

- **Foreground layer** - Required because MaterialButton overrides background
- **Mask shape** - Rectangle with 8dp corners for ripple bounds
- **Colors** - White (#55FFFFFF) for keys 2-9, Red (#55FF5252) for key 1

### Layout Structure

```
MaterialCardView (black, 16dp corners)
└── LinearLayout (vertical)
    ├── HorizontalScrollView (120dp height, fixed)
    │   └── LinearLayout (horizontal, center gravity)
    │       └── [App icons added programmatically]
    └── LinearLayout (vertical, T9 grid)
        ├── LinearLayout (horizontal, row 1-2-3)
        ├── LinearLayout (horizontal, row 4-5-6)
        └── LinearLayout (horizontal, row 7-8-9)
```

## Theme & Styling

### Color Palette

| Color | Hex | Usage |
|-------|-----|-------|
| `black_pure` | #000000 | Background (OLED optimized) |
| `key_number` | #FFFFFF | Button numbers |
| `key_alphabet` | #808080 | Button letters |
| `key_clear` | #FF5252 | CLEAR button accent |
| `app_text` | #E0E0E0 | App name labels |

### Button Styles

**Base Style**: `T9Key` (parent: `Widget.Material3.Button.TextButton`)
- Transparent background
- 60dp minimum height
- No elevation or state list animator
- 55% white ripple

**Clear Variant**: `T9Key.Clear`
- Inherits all from T9Key
- 55% red ripple

## Data Structures

### AppInfo
```kotlin
data class AppInfo(
    val name: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable
)
```

### IconPackInfo
```kotlin
data class IconPackInfo(
    val name: String,
    val packageName: String
)
```

### Icon Pack Mapping
```kotlin
private val iconPackMappings = mutableMapOf<String, String>()
// Key: "com.example.app/com.example.MainActivity"
// Value: "drawable_name"
```

## Performance Optimizations

1. **Lazy App Loading** - Apps loaded once in onCreate, cached in memory
2. **Icon Caching** - Icon pack mappings stored in HashMap for O(1) lookup
3. **View Recycling** - App views created programmatically, no RecyclerView overhead
4. **Filtered List** - Only matching apps rendered, UI updates on key press

## Build Configuration

- **Min SDK**: 21 (Android 5.0 Lollipop)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34
- **Material Components**: 1.11.0
- **Build Type**: Debug (minification disabled)

### Building

**Termux (Android):**
```bash
gradle assembleDebug
```
Note: Use `gradle` directly as the Gradle wrapper is not executable in Termux.

**Linux/macOS:**
```bash
./gradlew assembleDebug
```

**Windows:**
```bash
gradlew.bat assembleDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

The Gradle wrapper (`gradlew`/`gradlew.bat`) ensures consistent build environment across platforms by downloading the correct Gradle version automatically. Termux requires system-installed Gradle instead.

## Icon Pack Format

Supported `appfilter.xml` structure:
```xml
<resources>
    <item
        component="ComponentInfo{com.package/com.package.Activity}"
        drawable="icon_name" />
</resources>
```

## User Interactions

| Action | Behavior |
|--------|----------|
| Press 2-9 | Append digit to search query |
| Press 1 | Clear current search query |
| Long-press 1 | Open icon pack selection dialog |
| Tap app icon | Launch app, finish T9Activity |
| Tap outside card | Finish activity (return to previous screen) |

## Known Limitations

1. **No 0 key** - Only digits 1-9 implemented
2. **Prefix matching only** - Cannot search middle of app names
3. **Case insensitive** - All searches converted to lowercase
4. **No fuzzy search** - Exact T9 pattern matching required

## Future Enhancement Ideas

- Frecency sorting (frequency + recency)
- Favorites/pinned apps
- Multiple icon pack layers
- Settings persistence (SharedPreferences)
- Haptic feedback on key press
- Custom vibration patterns

## Technical Decisions

### Why Material Components?
- Built-in dark theme support
- Standardized ripple effects
- Typography system
- Better accessibility defaults

### Why Single Activity?
- Launcher should be lightweight
- No navigation complexity needed
- Faster launch time
- Lower memory footprint

### Why Programmatic App Views?
- Variable app count (3-10+ shown)
- Simple linear layout sufficient
- No item interaction beyond tap
- Avoids RecyclerView boilerplate

### Why Foreground Ripple?
- MaterialButton sets its own background
- Foreground layer renders on top
- Preserves transparent/borderless design
- Proper ripple masking

## Development Notes

Built entirely in Termux on Android device. Tested with:
- Icon Pack Studio exports
- Nova Icon Packs
- Standard launcher icon behavior
- Various app counts (10-200+ installed apps)

## Resources

- Material Design 3: https://m3.material.io/
- Android Icon Packs: https://developer.android.com/guide/practices/ui_guidelines/icon_design
- T9 Input Method: https://en.wikipedia.org/wiki/T9_(predictive_text)
