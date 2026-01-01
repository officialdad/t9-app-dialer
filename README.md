<div align="center">

# T9 App Dialer

**A minimalist Android launcher with T9 keypad search**

[![Latest Release](https://img.shields.io/github/v/release/officialdad/t9-app-dialer)](https://github.com/officialdad/t9-app-dialer/releases/latest)
[![License](https://img.shields.io/github/license/officialdad/t9-app-dialer)](LICENSE)

</div>

---

## Features

- **T9 Search** - Type app names using a classic phone keypad (2=ABC, 3=DEF, etc.)
  - Smart matching: beginning, word start, and substring matching
  - Real-time filtering as you type

- **Theme Support** - Switch between light and dark themes

- **Icon Pack Support** - Use your installed app icons

- **Landscape Mode** - Optimized for one-handed use

- **Performance** - Lightning fast

## Usage

### Basic Controls

- Press **2-9** to search app names using T9 input (e.g., `43556` for "GMAIL")
- Tap an **app icon** to launch it
- Tap **outside the container** to close the dialer

### Shortcuts

- Press **1** - Clear current search
- Long-press **1** - Open icon pack selector
- Long-press **2** - Toggle light/dark theme
- Long-press **app** - Show context menu (App Info, Play Store)

## Requirements

- **Android 5.0+** (API 21 or higher)
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

## Installation

Download the latest APK from [Releases](https://github.com/officialdad/t9-app-dialer/releases/latest) and install on your Android device.

## Contributing

Contributions are welcome! Feel free to:

- Report bugs
- Suggest features
- Submit pull requests

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
