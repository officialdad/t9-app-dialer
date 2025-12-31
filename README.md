# T9 App Dialer

A minimalist Android launcher that uses T9 keypad input to quickly find and launch apps.

## Features

- **T9 Search** - Type app names using a classic phone keypad (2=ABC, 3=DEF, etc.)
- **Material Design 3** - Clean OLED black interface with borderless keys
- **Icon Pack Support** - Apply custom icon packs (including Icon Pack Studio format)
- **Instant Results** - Real-time app filtering as you type

## Usage

1. Press number keys to spell app names (e.g., 43556 for "GMAIL")
2. Tap an app icon to launch it
3. Press **1 (CLEAR)** to reset search
4. Long-press **1** to access settings and change icon packs

## Requirements

- Android 5.0 (API 21) or higher
- ~10MB storage

## Building

**Termux:**
```bash
gradle assembleDebug
```

**Linux/macOS:**
```bash
./gradlew assembleDebug
```

**Windows:**
```bash
gradlew.bat assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`
