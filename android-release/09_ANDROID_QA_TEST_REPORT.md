# 09 — Android QA Test Report

**Date:** 2026-11-23 · **Environment:** Linux container, OpenJDK 21.0.13, Gradle 8.14.3, AGP 8.13.2, Robolectric 4.16 (Android SDK 35 image, native graphics), Roborazzi 1.52.0 · **No device or emulator** (no KVM).

## 1. Original web app baseline (before any work)

Run by the audit agent on a copy of UNLIT-GPT-HTML @ `605575a` (01 §7):

| Command | Result |
|---|---|
| `node tests/static.test.js` (= `npm run check`) | 8 / 8 passed |
| `node tests/run-all.js --shard=1/4` | 28 of 112 test files, 618 `ok`, 0 `not ok`, 0 skipped, 9 min 10 s (Playwright 1.63 installed globally; the repo pins 1.56.1) |
| Shards 2–4 | **not run** (time) |

The original repository was not modified.

## 2. New app — commands and results

```bash
node tools/i18n/build.mjs --check      # keys used: 589 · written: 589 · missing: 0
./gradlew testDebugUnitTest            # 24 tests, 24 passed, 0 failed, 0 skipped
./gradlew recordRoborazziDebug         # same tests + 32 screenshots in android-release/screenshots/
./gradlew lintDebug                    # 0 errors, 29 warnings (library-update and KTX-style hints)
./gradlew assembleDebug assembleRelease bundleRelease   # BUILD SUCCESSFUL
apksigner verify --print-certs app-release.apk          # Verifies (v2, v3)
```

| Suite | Tests | Passed | What it proves |
|---|---|---|---|
| `EngineTest` | 13 | 13 | PIN hash matches the original scheme; quote maths to the rupee and payment schedule; **a full lead → handover → service-ticket run** with separation of duties (Field can't accept own lead, Sales can't approve own discount or confirm a payment, payment gates, prep gate, shaft minimum, step order, pass-with-fail refused, signatures needed, ticket cycle); replay gives the same state and is idempotent; small team covered by HQ but never for physical work; forged and newer-version records refused; escalation ladder in working days; practice company builds through the rules; review regressions (#4 recorder left out of cover, #5 author may reject, #6 Sales take-over, #7 bad times, #8 exact amounts, #19 job-number suffix) |
| `I18nTest` | 3 | 3 | Every entry has en/hi/mr, same placeholders, real Devanagari; every key the code asks for exists (static calls, stage/role/event families); placeholders filled |
| `UiFlowScreenshotTest` | 6 | 6 | The real Compose UI + ViewModel + SQLite: front door in 3 languages; Field login and Add-site form; Sales accepts a lead through **Start My Next Task** and the stage changes; a form refuses to save with a missing field; Admin approves a quote (stage → quote ready), escalations, people; Finance opens payment verification; technician's next step; **a technician sees no customer name and no ₹ amount**; HQ screens; dark and strong-contrast themes; business rules; share screen |
| `SmallScreenScreenshotTest` | 1 | 1 | 320×640 dp mdpi: Marathi welcome, practice login, PIN pad |
| `PerformanceTest` | 1 | 1 | 20,007-record replay in 191 ms; no records refused |
| **Total** | **24** | **24** | |

## 3. Brief §11 checklist

| Required test | Status |
|---|---|
| Install and first launch | **Blocked** (no device). APK builds, verifies and `aapt2` shows the right package/SDKs |
| Login / logout | Pass (UI test: PIN pad login of 6 roles, logout) |
| Role-based access | Pass (engine + UI: refusals, tasks per role, technician data masking) |
| Navigation and back | Pass for navigation (UI tests); back button reviewed in code, not run on a device |
| Core project lifecycle | Pass (engine, full lifecycle; UI covers its main steps) |
| Camera capture | **Blocked** — needs a camera app; code path reviewed |
| Location permissions | **Blocked** — needs a device; denial/disabled/timeout handled in code |
| Offline record creation | Pass (no network in any test) |
| Offline evidence capture | **Blocked** (camera) |
| Queue persistence / app restart | Pass on the JVM (rebuild from SQLite gives the same state); not run on a device |
| Sync and retry / duplicate prevention | Pass for files: duplicate records applied once (engine); import code reviewed. No server transport exists |
| Conflict handling | Pass (out-of-order and forged records refused; one canonical order) |
| Upgrade from previous version | N/A (first version; schema v1) |
| Permission denial and revocation | Code paths only; **blocked** on device |
| Network interruption | N/A (no network use) |
| Low storage | **Blocked** (device) |
| Session expiry | Auto-lock after 5 min in background: code reviewed, not run |
| API authorization | N/A (no API). Rules re-checked on every phone for every record |

## 4. Run on a phone (for the owner, when a device is available)

```bash
adb install -r android-release/dist/AIEC-Elevator-OS-1.0.0-eval.apk
adb shell am start -W -n com.aiec.elevatoros/.MainActivity      # cold launch time (TotalTime)
adb shell dumpsys meminfo com.aiec.elevatoros | head -30
```

Manual script: Practice → Ramesh (field) → Add site with photo + GPS → log out → Priya (sales) → accept → … → Raju (technician) → step 6 with camera → deny location once → turn on flight mode → reopen the app (data still there) → Share data → load the file on a second phone.
