# 11 — Release Checklist (v1.0.0, evaluation build)

| Check | Result | Evidence |
|---|---|---|
| Debug APK builds | ✅ | `dist/AIEC-Elevator-OS-1.0.0-debug.apk` (18.96 MB), `logs/build.log` |
| Release APK builds | ✅ | `dist/AIEC-Elevator-OS-1.0.0-eval.apk` (1,397,053 bytes) |
| AAB builds | ✅ | `dist/AIEC-Elevator-OS-1.0.0-eval.aab` (3,675,443 bytes); `jarsigner -verify` → jar verified |
| Signature verifies | ✅ v2 + v3 | `apksigner verify --print-certs` → Verifies; cert SHA-256 `5fceaee0a3d36085697fc15081728b3e55b12ea1c081ef8a007455185e89ad5c` (**evaluation key**, not production) |
| Package ID and version | ✅ | `aapt2 dump badging`: `com.aiec.elevatoros`, versionCode 1, versionName 1.0.0, targetSdk 36, minSdk 26 |
| Not debuggable | ✅ | release badging has no `application-debuggable` (debug build has it) |
| No debug endpoints / no network | ✅ | no INTERNET permission, no URLs in code |
| No privileged secrets in package | ✅ | `unzip -l`: no keystore/properties/keys; no API keys in code |
| Permissions justified | ✅ | CAMERA (evidence), LOCATION (lead/arrival/SOS/stamp), VIBRATE; see 04 |
| Tests | ✅ 24/24 | `logs/test-results/*.xml` |
| Lint | ✅ 0 errors (29 warnings) | `logs/lint-results-debug.txt` |
| Words in 3 languages | ✅ 589/589 | `node tools/i18n/build.mjs --check` |
| SHA-256 checksums | ✅ | `dist/SHA256SUMS.txt` |
| Installs on a device | ⛔ not verified | no device/emulator in the build environment (09) |
| Release build tested on a device | ⛔ not verified | same |
| Upgrade path | N/A | first version of this package; schema v1 |
| Production signing | ⛔ owner action | create the company key, add CI secrets, tag `v1.0.0` (10). Record the production certificate SHA-256 here: `________` |
| Play Store | ⛔ owner action | confirm `com.aiec.elevatoros` is free in Play Console; privacy policy (12 #9); data-safety form from 06 |

**Release decision:** OK for **evaluation on test phones**. **Not ready for company production** until the ⛔ rows and the Critical risk in 06 C1 are closed.
