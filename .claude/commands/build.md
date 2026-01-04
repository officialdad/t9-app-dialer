---
description: Build debug APK for Termux development
---

Build the debug APK using Gradle in Termux environment:

1. Run `gradle assembleDebug`
2. Report build success or failure with any errors
3. On success, show APK location: `app/build/outputs/apk/debug/app-debug.apk`
4. Install with `termux-open` if build succeeds
