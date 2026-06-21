# T9 Dialer — Google Play Production Release

**Date:** 2026-06-21
**Status:** Approved design — execution in buckets

## Goal

Make T9 Dialer pass Google Play review and stay compliant, so Play becomes the
**future-proof primary distribution channel** before Google's developer-verification
rules gate sideloading (Obtainium/GitHub APK). Reach, credibility, and future
monetization ride along but are not the driver.

## Core decision

**Compliance-first, minimum viable listing.** Every bucket optimizes for *getting
accepted and staying accepted*, not new features. When a feature conflicts with
approval risk, approval wins.

## Locked decisions

| Decision | Choice | Why |
|---|---|---|
| In-app uninstall (`REQUEST_DELETE_PACKAGES`) | **Drop** | Riskiest permission; App Info + Play Store actions still let users uninstall via system UI |
| `QUERY_ALL_PACKAGES` | **Keep** | Core launcher function; an approved Play justification — needs declaration form |
| App signing | **Play App Signing** | Google holds the app key; we upload with an upload key |
| Distribution | **Play primary, keep GitHub** | Future-proof channel + existing power users; two signing keys coexist |
| Monetization | **Deferred** | Not the driver; don't preclude, don't build now |
| Scope | **Ship + light polish** | Fix obvious rough edges in-pass; no speculative work |

### Known consequence

Play build (Play App Signing key) ≠ GitHub APK (own key). Different signature →
Play build **cannot update over** an existing sideloaded install; those users must
uninstall + reinstall (losing `allowBackup` prefs). Accepted.

## Verified facts (2026-06-21)

- **No data egress.** No `INTERNET` permission, no analytics/crash/network SDKs. Only
  outbound = `ACTION_VIEW` intents to GitHub/Play URLs. → Data Safety = all "no";
  privacy policy trivial.
- **Uninstall removal scope:** `T9Activity.kt:70-71, 316-318, 1945-1957` + manifest L14.
- **CI** signs a release **APK** (keystore in GitHub secrets); Play needs an **AAB**.
- **Stale versions:** manifest declares `versionCode=1 / 1.0`; gradle declares
  `versionCode=105 / 1.5.0` (gradle wins). Manifest attrs to be removed.
- `T9Activity.kt` = 2027 lines (god-file). **Out of scope** beyond uninstall removal.
- Repo owner mismatch: README/code use `officialdad`; git user is `opariffazman`.

## Buckets (plan → checkpoint → build → verify, one at a time)

1. **Production-readiness audit** *(triage)* — Android/Kotlin review via
   `android-developer` + `code-reviewer` agents → prioritized punch-list
   (Blocker / Polish / Deferred). Shapes all later buckets.
2. **Permission surgery** *(code)* — remove uninstall + `REQUEST_DELETE_PACKAGES`;
   remove stale manifest version attrs; apply quick Blocker fixes from Bucket 1.
3. **Release build ready** *(build)* — upload key + signing config; enable R8 minify
   + resource shrink for release; bump `targetSdk`/`compileSdk` to Play's current
   minimum (verify exact number at build time); bump version for launch; produce a
   signed **AAB** and confirm it builds + installs.
4. **Compliance content** *(docs)* — minimal privacy policy hosted on GitHub Pages;
   Data Safety answers (all "no"); `QUERY_ALL_PACKAGES` permission declaration text.
5. **Store listing + light polish** *(content)* — title, short + full description,
   category, content-rating answers, and exact asset spec (icon 512², phone
   screenshots, feature graphic 1024×500); plus light-polish fixes (repo owner,
   About text).
6. **Final verification + submit** *(verify)* — smoke-test signed AAB on a real
   device, run pre-submission checklist, then user creates the Play app and uploads.

## Deferred (YAGNI)

CI auto-AAB / Play-API publishing · in-app billing/monetization · app rename ·
god-file refactor · targetSdk beyond Play minimum.

## Success criteria

Signed AAB accepted into Play production (or closed testing), with the permission
declaration approved and Data Safety/privacy policy in place. GitHub release path
still functional.
