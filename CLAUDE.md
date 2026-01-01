# T9 App Dialer - Technical Documentation

## Architecture Overview

Single-activity Android application built with Kotlin, using Material Design 3 components, Kotlin coroutines for async operations, and a custom T9 search algorithm with intelligent matching priorities and extensive performance optimizations.

## Project Structure

```
app/src/main/
├── java/com/t9dialer/
│   └── T9Activity.kt          # Main activity with T9 search logic
├── res/
│   ├── drawable/
│   │   ├── ic_settings.xml           # Settings cog icon (16dp)
│   │   ├── ic_sun.xml                # Sun icon for theme toggle
│   │   ├── ic_moon.xml               # Moon icon for theme toggle
│   │   ├── ic_launcher.png           # App launcher icon
│   │   ├── button_ripple.xml         # White ripple effect
│   │   └── button_ripple_red.xml     # Red ripple for CLEAR button
│   ├── layout/
│   │   └── activity_t9.xml           # Main UI layout
│   ├── mipmap/
│   │   └── ic_launcher.png           # App icon
│   ├── values/
│   │   ├── colors.xml                # Light and dark theme colors
│   │   ├── styles.xml                # T9Key button styles
│   │   ├── themes.xml                # Material 3 dialog theme
│   │   └── strings.xml               # App name string
│   └── xml/
│       └── launcher_intent_filter.xml # Launcher category filter
└── AndroidManifest.xml
```

## Key Components

### T9Activity.kt

Main activity handling:
- **App Loading** - Queries PackageManager for launchable apps (lazy-loaded on first key press)
- **T9 Search** - Maps numeric input to alphabetic app names with priority matching
- **Icon Pack Support** - Loads and applies custom icon packs from multiple formats
- **Theme Management** - Light/Dark theme switching with SharedPreferences persistence
- **UI Management** - Dynamic app list rendering with view recycling
- **Performance** - Icon caching, coroutines, early termination optimizations

#### T9 Mapping Logic

```kotlin
private fun stringToT9(text: String): String {
    val t9Map = mapOf(
        'a' to '2', 'b' to '2', 'c' to '2',
        'd' to '3', 'e' to '3', 'f' to '3',
        'g' to '4', 'h' to '4', 'i' to '4',
        'j' to '5', 'k' to '5', 'l' to '5',
        'm' to '6', 'n' to '6', 'o' to '6',
        'p' to '7', 'q' to '7', 'r' to '7', 's' to '7',
        't' to '8', 'u' to '8', 'v' to '8',
        'w' to '9', 'x' to '9', 'y' to '9', 'z' to '9'
    )
    return text.lowercase().map { t9Map[it] ?: ' ' }.joinToString("")
}
```

**Pre-computation**: T9 sequences are computed once during app loading and stored in the `AppInfo` data class for fast matching.

**Match Priorities**:
1. **Priority 0** - Beginning of app name (highest priority)
2. **Priority 1** - Start of any word in app name (medium priority)
3. **Priority 2** - Anywhere in app name (lowest priority)

**Early Termination**: Search stops after finding 3 priority-0 matches for optimal performance.

#### Icon Pack Loading

Supports multiple icon pack formats:
1. **ADW/Nova Launcher** - `org.adw.launcher.THEMES` intent
2. **GO Launcher** - `com.gau.go.launcherex.theme` intent
3. **Icon Pack Studio** - Apps with `appfilter.xml` metadata

Icon pack detection methods:
- **Standard XML** - `res/xml/appfilter.xml` (compiled resource)
- **Raw Resource** - `res/raw/appfilter.xml` (Icon Pack Studio format)

Uses XmlPullParser to parse `<item>` tags with `component` and `drawable` attributes. Component format is flexible, supporting both `ComponentInfo{package/activity}` and simple `package` formats.

### UI Implementation

#### Material Components Used

- `MaterialCardView` - Main container with 16dp rounded corners, 1dp stroke
- `MaterialButton` - T9 keys with TextButton style (borderless)
- `LinearLayout` - Fixed-height app container (120dp) aligned with keyboard

#### Window Positioning

- **Portrait Mode** - Bottom-aligned for one-handed thumb access
- **Landscape Mode** - Bottom-right corner for optimal reachability
- **Dialog Theme** - Activity presented as floating dialog over home screen

#### Dual-Color Text Rendering

Uses `SpannableString` with two spans per button:
- **Number** - 20sp, theme-aware color (white/black)
- **Letters** - 14sp, gray (#808080 dark / #666666 light)
- **Button 1** - Red number (#FF5252) for visual distinction

Applied via `setKeyText()` helper function in `setupKeyboard()`.

#### Press Feedback (StateListDrawable)

Instant visual feedback using `StateListDrawable` instead of traditional ripple:
- **Pressed State** - Semi-transparent overlay (instant appearance)
- **Default State** - Transparent (instant disappearance)
- **No Fade Duration** - `setEnterFadeDuration(0)` and `setExitFadeDuration(0)`
- **Shape** - RoundRectShape with 8dp corners
- **Colors** - Theme-aware white/black for keys 2-9, red for key 1
- **Implementation** - Applied to foreground layer for proper layering

### Layout Structure

```
FrameLayout (rootContainer, clickable for outside-tap detection)
└── MaterialCardView (black/white, 16dp corners, bottom-aligned)
    └── LinearLayout (vertical, mainContainer)
        ├── LinearLayout (horizontal, 120dp fixed height)
        │   └── [App icons added programmatically with weight=1]
        └── LinearLayout (vertical, T9 grid)
            ├── LinearLayout (horizontal, row 1-2-3)
            ├── LinearLayout (horizontal, row 4-5-6)
            └── LinearLayout (horizontal, row 7-8-9)
```

## Theme & Styling

### Theme System

**Light/Dark Theme Support**:
- Toggle via long-press on button 2
- Preference stored in SharedPreferences (`light_theme` boolean)
- Theme icons: Sun (dark theme) / Moon (light theme)
- Applied dynamically via `applyTheme()` function

### Color Palette

#### Dark Theme (Default)
| Color | Hex | Usage |
|-------|-----|-------|
| `dark_background` | #000000 | Background (OLED optimized) |
| `dark_key_number` | #FFFFFF | Button numbers |
| `dark_key_alphabet` | #808080 | Button letters |
| `dark_app_text` | #E0E0E0 | App name labels |
| `dark_app_text_highlight` | #FFFFFF | Matched text in app names |
| `dark_border` | #404040 | Card stroke |
| `dark_ripple` | #99FFFFFF | Press feedback (60% white) |
| `dark_ripple_red` | #99FF5252 | Clear button press (60% red) |

#### Light Theme
| Color | Hex | Usage |
|-------|-----|-------|
| `light_background` | #FFFFFF | Background |
| `light_key_number` | #000000 | Button numbers |
| `light_key_alphabet` | #666666 | Button letters |
| `light_app_text` | #333333 | App name labels |
| `light_app_text_highlight` | #000000 | Matched text in app names |
| `light_border` | #E0E0E0 | Card stroke |
| `light_ripple` | #66000000 | Press feedback (40% black) |
| `light_ripple_red` | #99FF5252 | Clear button press (60% red) |

#### Common Colors
| Color | Hex | Usage |
|-------|-----|-------|
| `key_clear` | #FF5252 | CLEAR button number (always red) |
| `black_pure` | #000000 | Pure black reference |
| `white` | #FFFFFF | Pure white reference |

### Button Styles

**Base Style**: `T9Key` (parent: `Widget.Material3.Button.TextButton`)
- Transparent background
- 75dp minimum height (76dp in layout)
- No elevation or state list animator
- Press feedback set programmatically based on theme

**Clear Variant**: `T9Key.Clear`
- Inherits all from T9Key
- Red press feedback
- Settings icon on right side

## Data Structures

### AppInfo
```kotlin
data class AppInfo(
    val name: String,
    val packageName: String,
    val t9Sequence: String,  // Pre-computed T9 sequence for fast matching
    var icon: Drawable? = null  // Lazy-loaded icon
)
```

**Changes from original**:
- Removed `activityName` (not needed, using launcher intent)
- Added `t9Sequence` for pre-computed T9 mapping
- Made `icon` nullable and mutable for lazy loading

### IconPackInfo
```kotlin
data class IconPackInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable  // Icon pack's own icon for display in selector
)
```

**Added**: `icon` field for displaying icon pack icons in selection dialog.

### MatchInfo
```kotlin
data class MatchInfo(
    val app: AppInfo,
    val matchPosition: Int,      // Position where match was found
    val matchPriority: Int        // 0 = beginning, 1 = word start, 2 = substring
)
```

**New structure** for intelligent match sorting and highlighting.

### Icon Pack Mapping
```kotlin
private val iconPackMappings: MutableMap<String, String> = mutableMapOf()
// Key: "com.example.app" (package name only, simplified from component info)
// Value: "drawable_name"
```

### Performance Structures
```kotlin
private val iconCache = ConcurrentHashMap<String, Drawable>()
private val viewPool = mutableListOf<LinearLayout>()
private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
private var cachedIconPacks: List<IconPackInfo>? = null
```

## Performance Optimizations

1. **Lazy App Loading** - Apps loaded on first key press (not in onCreate) for faster startup
2. **Pre-computed T9 Sequences** - T9 conversion done once during loading, stored in AppInfo
3. **Icon Caching** - Thread-safe ConcurrentHashMap caches icons by pack+package
4. **View Recycling** - LinearLayout views reused from pool instead of recreating
5. **Early Termination** - Search stops after finding 3 priority-0 matches
6. **Coroutines** - Background loading with Dispatchers.IO for apps and icons
7. **Lazy Icon Loading** - Icons only loaded when apps are displayed
8. **Icon Pack Caching** - Icon pack list cached after first scan
9. **Limited Results** - Maximum 3 apps displayed at once

## Build Configuration

- **Min SDK**: 23 (Android 6.0 Marshmallow)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34
- **Version**: 0.0.5 (versionCode 5)
- **Material Components**: 1.11.0
- **AndroidX Core KTX**: 1.12.0
- **Coroutines**: 1.7.3
- **Build Type**: Debug (minification disabled)
- **Java Version**: 17

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

**Supported component formats**:
- `ComponentInfo{com.package/com.package.Activity}`
- `com.package/com.package.Activity`
- `com.package` (simplified, package name only)

## User Interactions

| Action | Behavior |
|--------|----------|
| Press 2-9 | Append digit to search query |
| Press 1 | Clear current search query |
| Long-press 1 | Open icon pack selection dialog |
| Long-press 2 | Toggle light/dark theme |
| Tap app icon | Launch app, finish T9Activity |
| Long-press app | Show context menu (App Info, Play Store) |
| Tap outside card | Finish activity (return to previous screen) |

### Context Menu Options

**App Info** - Opens system settings for the app
**Open in Play Store** - Opens app in Google Play Store (or web fallback)

## Persistence

**SharedPreferences** (`T9Dialer`):
- `light_theme` (Boolean) - Current theme preference
- `icon_pack` (String) - Selected icon pack package name

## Known Limitations

1. **No 0 key** - Only digits 1-9 implemented
2. **Case insensitive** - All searches converted to lowercase
3. **Limited results** - Maximum 3 apps displayed
4. **No app usage tracking** - No frecency-based sorting

## Future Enhancement Ideas

- Frecency sorting (frequency + recency)
- Favorites/pinned apps
- Multiple icon pack layers
- Haptic feedback on key press
- Custom vibration patterns
- Configurable result limit
- 0 key for space or special characters

## Technical Decisions

### Why Material Components?
- Built-in dark theme support
- Standardized visual language
- Typography system
- Better accessibility defaults

### Why Single Activity?
- Launcher should be lightweight
- No navigation complexity needed
- Faster launch time
- Lower memory footprint

### Why Programmatic App Views?
- Variable app count (1-3 shown)
- Simple linear layout sufficient
- View recycling for performance
- Avoids RecyclerView boilerplate

### Why StateListDrawable Instead of RippleDrawable?
- Instant visual feedback (no animation delay)
- More responsive feel for quick searches
- Simpler implementation
- Better control over appearance/disappearance timing

### Why Coroutines Instead of AsyncTask?
- Modern Kotlin approach
- Better structured concurrency
- Automatic cancellation on activity destroy
- More readable async code

### Why Lazy App Loading?
- Faster startup time (activity appears instantly)
- Apps rarely needed if user navigates away immediately
- First key press delay negligible compared to startup improvement

### Why Pre-computed T9 Sequences?
- Eliminates repeated string-to-T9 conversion on every search
- O(1) lookup instead of O(n) conversion
- Trades minimal memory for significant CPU savings

### Why Bottom Positioning?
- One-handed thumb access on modern large-screen phones
- Natural gesture area for right-handed users
- Landscape mode: bottom-right for best reachability

## Development Notes

Built entirely in Termux on Android device. Tested with:
- Icon Pack Studio exports
- Nova Icon Packs
- ADW Launcher icon packs
- Standard launcher icon behavior
- Various app counts (10-200+ installed apps)
- Light and dark themes
- Portrait and landscape orientations

## Resources

- Material Design 3: https://m3.material.io/
- Android Icon Packs: https://developer.android.com/guide/practices/ui_guidelines/icon_design
- T9 Input Method: https://en.wikipedia.org/wiki/T9_(predictive_text)
- Kotlin Coroutines: https://kotlinlang.org/docs/coroutines-overview.html
