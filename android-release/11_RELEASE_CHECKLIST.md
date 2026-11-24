# 11 — Release Checklist (v1.1.2, evaluation build)

| Check | Result | Evidence |
|---|---|---|
| Debug APK builds | ✅ | `dist/AIEC-Elevator-OS-1.1.2-debug.apk` (19.00 MB), `logs/build.log` |
| Release APK builds | ✅ | `dist/AIEC-Elevator-OS-1.1.2-eval.apk` (1,414,937 bytes) |
| AAB builds | ✅ | `dist/AIEC-Elevator-OS-1.1.2-eval.aab` (3,701,619 bytes); `jarsigner -verify` → jar verified |
| Signature verifies | ✅ v2 + v3 | `apksigner verify --print-certs` → Verifies; cert SHA-256 `5fceaee0a3d36085697fc15081728b3e55b12ea1c081ef8a007455185e89ad5c` (**evaluation key**, not production) |
| Package ID and version | ✅ | `aapt2 dump badging`: `com.aiec.elevatoros`, versionCode 4, versionName 1.1.2, targetSdk 36, minSdk 26 |
| Not debuggable | ✅ | release badging has no `application-debuggable` (debug build has it) |
| No debug endpoints / no network | ✅ | no INTERNET permission, no URLs in code |
| No privileged secrets in package | ✅ | `unzip -l`: no keystore/properties/keys; no API keys in code |
| Permissions justified | ✅ | CAMERA (evidence), LOCATION (lead/arrival/SOS/stamp), VIBRATE; see 04 |
| Tests | ✅ 32/32 | `logs/test-results/*.xml` |
| Lint | ✅ 0 errors (31 warnings) | `logs/lint-results-debug.txt` |
| Words in 3 languages | ✅ 605/605 | `node tools/i18n/build.mjs --check` |
| SHA-256 checksums | ✅ | `dist/SHA256SUMS.txt` |
| Installs on a device | ✅ Android 11 emulator | 09 §6 |
| Release build tested on a device | ✅ login, setup, add person, PIN flows; ⛔ camera/GPS not tested | 09 §6 |
| Upgrade path | ✅ by design, ⛔ not run on a device | 1.0.0 → 1.1.0: database v1 → v2 adds signature columns, old local records are signed at first start (code-reviewed). Same evaluation key, so 1.1.0 installs over 1.0.0 |
| Production signing | ⛔ owner action | create the company key, add CI secrets, tag `v1.0.0` (10). Record the production certificate SHA-256 here: `________` |
| Play Store | ⛔ owner action | confirm `com.aiec.elevatoros` is free in Play Console; privacy policy (12 #9); data-safety form from 06 |

**Release decision:** OK for **evaluation on test phones**. **Not ready for company production** until the ⛔ rows are closed (06 C1 record signing is fixed in 1.1.0).
