# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## MCP Usage

- **Always use Context7 MCP** when needing library/API documentation, code generation, setup, or configuration steps — without requiring the user to explicitly ask for it.

## Project Overview

**T9 Dialer** - A minimal Android T9 predictive text app launcher built with Kotlin.

### Tech Stack
- **Language**: Kotlin 2.1.0
- **Build**: Gradle 8.7.3 (Kotlin DSL)
- **Min SDK**: 23 (Android 6.0)
- **Target SDK**: 34 (Android 14)
- **Dependencies**: AndroidX Core KTX, AppCompat, Material Design, Coroutines

### Project Structure
```
app/
├── src/main/
│   ├── java/com/t9dialer/
│   │   └── T9Activity.kt      # Main (and only) activity
│   ├── res/
│   │   ├── layout/            # XML layouts (portrait)
│   │   ├── layout-land/       # Landscape layouts
│   │   ├── drawable/          # Icons and ripple effects
│   │   ├── values/            # Colors, strings, themes, styles
│   │   └── mipmap-*/          # App icons
│   └── AndroidManifest.xml
├── build.gradle.kts           # App-level build config
build.gradle.kts               # Project-level build config
settings.gradle.kts            # Project settings
```

### Build Commands (Termux)
- **Debug build**: `gradle assembleDebug`
- **Clean**: `gradle clean`
- **Install**: `termux-open app/build/outputs/apk/debug/app-debug.apk`

**Important**: The user builds/tests in Termux on-device. But sessions often run on a **headless Linux server with no Android SDK** (`ANDROID_HOME` unset, no `sdkmanager`) — `gradle assembleDebug` cannot build there. In that case: commit + push the branch and the user clones + builds on-device. Only run `termux-open` when actually building inside Termux.

### Architecture Notes
- Single-activity app with dialog-style window
- Coroutines (`MainScope`) for async app loading and icon caching
- `SharedPreferences` for theme and icon pack persistence
- View recycling pool for search results performance
- Pre-computed T9 sequences for fast matching
- **Package visibility**: manifest `<queries>` (MAIN/LAUNCHER + icon-pack theme intents `org.adw.launcher.THEMES`, `com.gau.go.launcherex.theme`). Do NOT re-add `QUERY_ALL_PACKAGES` — it was intentionally dropped (Play sensitive permission)
- **No in-app uninstall** (Play compliance): app long-press menu = App Info + Play Store only
- App list **re-indexes on `onResume`** (first resume skipped) to pick up external installs/uninstalls — don't remove
- Versions owned by Gradle (`app/build.gradle.kts`); manifest has no `versionCode`/`versionName`/`<uses-sdk>`. `allowBackup=false`

### CI/CD
- GitHub Actions builds release APK on version tags (`v*`)
- Use `/release` command to bump version and trigger
- CI keeps building an **APK** for the GitHub channel; the Play **AAB** is built separately (Play App Signing)

### Google Play release (in progress)
- Play = primary future-proof channel (GitHub kept). Work branch: `release/play-store-prep`
- Plan + locked decisions + bucket breakdown: `docs/superpowers/specs/2026-06-21-play-store-release-design.md`

## Universal Development Guidelines

### Code Quality Standards
- Write clean, readable, and maintainable code
- Follow consistent naming conventions across the project
- Use meaningful variable and function names
- Keep functions focused and single-purpose
- Add comments for complex logic and business rules

### Git Workflow
- Use descriptive commit messages following conventional commits format
- Create feature branches for new development
- Keep commits atomic and focused on single changes
- Use pull requests for code review before merging
- Maintain a clean commit history

### Documentation
- Keep README.md files up to date
- Document public APIs and interfaces
- Include usage examples for complex features
- Maintain inline code documentation
- Update documentation when making changes

### Testing Approach
- Write tests for new features and bug fixes
- Maintain good test coverage
- Use descriptive test names that explain the expected behavior
- Organize tests logically by feature or module
- Run tests before committing changes

### Security Best Practices
- Never commit sensitive information (API keys, passwords, tokens)
- Use environment variables for configuration
- Validate input data and sanitize outputs
- Follow principle of least privilege
- Keep dependencies updated

## Project Structure Guidelines

### File Organization
- Group related files in logical directories
- Use consistent file and folder naming conventions
- Separate source code from configuration files
- Keep build artifacts out of version control
- Organize assets and resources appropriately

### Configuration Management
- Use configuration files for environment-specific settings
- Centralize configuration in dedicated files
- Use environment variables for sensitive or environment-specific data
- Document configuration options and their purposes
- Provide example configuration files

## Development Workflow

### Before Starting Work
1. Pull latest changes from main branch
2. Create a new feature branch
3. Review existing code and architecture
4. Plan the implementation approach

### During Development
1. Make incremental commits with clear messages
2. Run tests frequently to catch issues early
3. Follow established coding standards
4. Update documentation as needed

### Before Submitting
1. Run full test suite
2. Check code quality and formatting
3. Update documentation if necessary
4. Create clear pull request description

## Common Patterns

### Error Handling
- Use appropriate error handling mechanisms for the language
- Provide meaningful error messages
- Log errors appropriately for debugging
- Handle edge cases gracefully
- Don't expose sensitive information in error messages

### Performance Considerations
- Profile code for performance bottlenecks
- Optimize database queries and API calls
- Use caching where appropriate
- Consider memory usage and resource management
- Monitor and measure performance metrics

### Code Reusability
- Extract common functionality into reusable modules
- Use dependency injection for better testability
- Create utility functions for repeated operations
- Design interfaces for extensibility
- Follow DRY (Don't Repeat Yourself) principle

## Review Checklist

Before marking any task as complete:
- [ ] Code follows established conventions
- [ ] Tests are written and passing
- [ ] Documentation is updated
- [ ] Security considerations are addressed
- [ ] Performance impact is considered
- [ ] Code is reviewed for maintainability