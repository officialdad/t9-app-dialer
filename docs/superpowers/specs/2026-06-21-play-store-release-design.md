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
| `QUERY_ALL_PACKAGES` | **Drop → replace with `<queries>`** | A launcher only needs MAIN/LAUNCHER (+ icon-pack theme) intent visibility; `<queries>` covers it with NO sensitive permission and NO declaration form (revised after Bucket 1 audit) |
| R8 minify / resource shrink | **Skip for v1** | Not required by Play; icon-pack reflection (~`T9Activity.kt:913`) would need keep rules. Add later if size matters |
| PerfTrace.kt | **Delete** | Debug-only profiling tool; deletion removes the file + its thread-safety smells |
| App signing | **Play App Signing** | Google holds the app key; we upload with an upload key |
| Distribution | **Play primary, keep GitHub** | Future-proof channel + existing power users; two signing keys coexist. CI keeps building APK for GitHub; AAB built separately for Play |
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
2. **Code & manifest hardening** *(code)* — (a) remove uninstall + `REQUEST_DELETE_PACKAGES`;
   (b) drop `QUERY_ALL_PACKAGES`, add `<queries>` for MAIN/LAUNCHER + icon-pack theme intents;
   (c) delete `PerfTrace.kt` + its calls; (d) 5 stability fixes from audit
   (`iconPackMappings`→ConcurrentHashMap, icon-pack parse off main thread, `getDrawable`
   off IO thread, span `matchEnd` clamp, About `getPackageInfo` guard); (e) dead-code
   deletion (`debugLog`, `loadIconForApp`); (f) manifest cleanup (strip stale version
   attrs + `<uses-sdk>`, `label`→`@string/app_name`, `allowBackup="false"`).
   Verify: app discovery + icon packs still work after the `<queries>` switch.
3. **Release build ready** *(build)* — upload key + signing config; bump
   `targetSdk`/`compileSdk` to Play's current minimum (verify exact number at build time);
   bump version for launch; produce a signed **AAB** and confirm it builds + installs.
   (R8 skipped for v1.)
4. **Compliance content** *(docs)* — minimal privacy policy hosted on GitHub Pages;
   Data Safety answers (all "no"). (No `QUERY_ALL_PACKAGES` declaration needed — dropped.)
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
