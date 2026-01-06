<div align="center">

<img width="128" height="128" alt="t9-app-dialer" src="icon.png" />

# T9 App Dialer

**A minimalist Android launcher with T9 keypad search**

[![Latest Release](https://img.shields.io/github/v/release/officialdad/t9-app-dialer)](https://github.com/officialdad/t9-app-dialer/releases/latest)
[![License](https://img.shields.io/github/license/officialdad/t9-app-dialer)](LICENSE)

[<img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Get it on GitHub" height="80">](https://github.com/officialdad/t9-app-dialer/releases/latest)

</div>

---

## Features

- **T9 Search** - Type app names using a classic phone keypad (2=ABC, 3=DEF, etc.)
  - Smart matching: beginning, word start, and substring matching
  - Real-time filtering as you type
  - Match highlighting shows which part of the app name matched

- **Theme Support** - Switch between light and dark themes
  - Theme-aware dialogs and context menus
  - Consistent styling across all UI elements

- **Icon Pack Support** - Use your favorite icon packs
  - Supports ADW, Nova, GO Launcher, and Icon Pack Studio formats
  - Auto-detects installed icon packs

- **Movable Container** - Position the dialer anywhere on screen
  - Independent positions for portrait and landscape orientations
  - Visual move mode with drag-to-position
  - Position persists across app restarts

- **Resizable Container** - Adjust the dialer size to your preference
  - Shrink, expand, or reset to default
  - Separate sizes for portrait and landscape

- **App Management** - Long-press apps for quick actions
  - View app info in system settings
  - Uninstall apps directly
  - Open in Play Store

- **Landscape Mode** - Optimized for one-handed use with separate layout

- **About Dialog** - View version info and GitHub link

## Usage

### Basic Controls

| Action | Result |
|--------|--------|
| Press **2-9** | Search apps using T9 input (e.g., `43556` for "GMAIL") |
| Tap **app icon** | Launch the app |
| Tap **outside container** | Close the dialer |

### Keyboard Shortcuts

| Button | Press | Long-Press |
|--------|-------|------------|
| **1** | Clear search | Open icon pack selector |
| **2** | Type ABC | Toggle light/dark theme |
| **3** | Type DEF | Enter/save move mode |
| **4** | Type GHI | Shrink container |
| **5** | Type JKL | Reset container size |
| **6** | Type MNO | Expand container |
| **7** | Type PQRS | - |
| **8** | Type TUV | - |
| **9** | Type WXYZ | Show About dialog |

### Move Mode

1. **Long-press 3** to enter move mode (border highlights)
2. **Drag** the container to your desired position
3. **Long-press 3** again to save and exit move mode

### App Context Menu

**Long-press any app** in the search results to:
- View **App Info** in system settings
- **Uninstall** the app
- Open in **Play Store**

## Installation

### Recommended: Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) allows you to install and update apps directly from their GitHub releases.

1. Install [Obtainium](https://github.com/ImranR98/Obtainium)
2. Add app with URL: `https://github.com/officialdad/t9-app-dialer`
3. Obtainium will notify you of updates automatically

### Manual Download

[<img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Get it on GitHub" height="60">](https://github.com/officialdad/t9-app-dialer/releases/latest)

Download the latest APK from [Releases](https://github.com/officialdad/t9-app-dialer/releases/latest) and install on your Android device.

## Requirements

- **Android 6.0+** (API 23 or higher)
- **~15MB** storage
- **Permissions:** None required

## Building

### Termux (Android)

```bash
gradle assembleDebug
```

### Linux/macOS

```bash
./gradlew assembleDebug
```

### Windows

```bash
gradlew.bat assembleDebug
```

**Output:** `app/build/outputs/apk/debug/app-debug.apk`

## Contributing

Contributions are welcome! Feel free to:

- Report bugs
- Suggest features
- Submit pull requests

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
