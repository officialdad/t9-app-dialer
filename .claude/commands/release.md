---
description: Prepare release by bumping version and creating git tag
---

Prepare a new release version (CI/CD handles the actual build):

1. Read current `versionCode` and `versionName` from `app/build.gradle.kts`
2. Ask user for new version (suggest incrementing patch, e.g., 0.0.5 -> 0.0.6)
3. Update `app/build.gradle.kts`:
   - Increment `versionCode` by 1
   - Set `versionName` to the new version
4. Commit the version bump: `git add app/build.gradle.kts && git commit -m "chore: bump version to vX.Y.Z"`
5. Create matching git tag: `git tag vX.Y.Z`
6. Push commit and tag: `git push && git push --tags`
7. Report that GitHub Actions will build and release the APK
