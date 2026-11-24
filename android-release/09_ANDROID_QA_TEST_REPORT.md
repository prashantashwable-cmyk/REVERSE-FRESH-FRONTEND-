# 09 — Android QA Test Report

**Date:** 2026-11-24 (v1.1.2) · **Environment:** Linux container, OpenJDK 21.0.13, Gradle 8.14.3, AGP 8.13.2, Robolectric 4.16 (Android SDK 35 image, native graphics), Roborazzi 1.52.0 · **No device or emulator** (no KVM).

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
node tools/i18n/build.mjs --check      # keys used: 605 · written: 605 · missing: 0
./gradlew testDebugUnitTest            # 32 tests, 32 passed, 0 failed, 0 skipped
./gradlew recordRoborazziDebug         # same tests + 32 screenshots in android-release/screenshots/
./gradlew lintDebug                    # 0 errors, 31 warnings (library-update and KTX-style hints)
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
| `SigningTest` | 4 | 4 | Two simulated phones + an attacker: an approved phone is trusted, also for records made before approval; edited, unsigned, forged-as-HQ and spoofed-device records are refused; a key an attacker registers for HQ stays untrusted; cutting PIN fields keeps signatures valid; own-phone records are trusted |
| `CompanySetupFlowTest` | 1 | 1 | Real company through the UI: setup with a PIN typed twice, add a person with a PIN, log out, log in as that person with their PIN, restart and re-verify every signed record (0 refused) |
| `MainActivityLaunchTest` | 1 | 1 | The real `MainActivity` (splash, edge-to-edge, lifecycle observer, FLAG_SECURE) with PIN logins of an outsider and the HQ |
| `I18nTest` (added) | +2 | +2 | Placeholder filling without regex; **every `Regex(...)` in the app is valid on Android's ICU engine** (guards the 1.1.2 crash) |
| **Total** | **32** | **32** | |

## 3. Brief §11 checklist

| Required test | Status |
|---|---|
| Install and first launch | ✅ Android 11 emulator (software CPU): installs, launches, cold start 3–11 s in software emulation (not representative of a phone) |
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
adb install -r android-release/dist/AIEC-Elevator-OS-1.1.2-eval.apk
adb shell am start -W -n com.aiec.elevatoros/.MainActivity      # cold launch time (TotalTime)
adb shell dumpsys meminfo com.aiec.elevatoros | head -30
```

Manual script: Practice → Ramesh (field) → Add site with photo + GPS → log out → Priya (sales) → accept → … → Raju (technician) → step 6 with camera → deny location once → turn on flight mode → reopen the app (data still there) → Share data → load the file on a second phone.

## 5. Field report: "App crash after adding PIN" (v1.1.0, 2026-11-24)

* **Reproduced on the JVM?** No. `CompanySetupFlowTest` drives the exact flows (setup PIN, add person with PIN, PIN login, restart) and passes.
* **Most likely cause:** v1.1.0 creates the phone's signing key in the Android Keystore right after the PIN is accepted. On a real phone this uses the hardware Keystore, which the JVM tests cannot reach (they use the software fallback). A Keystore error there was not caught, so it would close the app straight after the PIN.
* **Fixed in v1.1.1:**
  * The Keystore key is tested (sign + verify) before use; on any failure the app uses an app-private software key instead.
  * Registering the key can never stop a login.
  * Any unexpected error while saving becomes a refusal on screen ("could not be saved, nothing changed") instead of a closed app.
* **If it still happens:** v1.1.1 records the error. The next start shows "The app closed by mistake" with **Share report**, which holds the error and the phone model only, no company data. Sending that report pinpoints the cause.
* R8 (release minification) was checked: no missing-class warnings; the serializer, signing and key classes are present in the release mapping.
* **Correction (v1.1.2): the Keystore guess above was wrong.** The crash was still there in 1.1.1.

## 6. Root cause found on a real Android device (v1.1.2)

* **Device:** Android 11 (API 30) x86_64 emulator. It runs in software CPU mode (`ANDROID_I_WANT_MY_TCG=yes`, no KVM) and boots in 4 min 20 s.
* **The exact APK sent to the owner (1.1.1)** crashed after PIN `1111`. `adb logcat -b crash`:
  ```
  FATAL EXCEPTION: main  java.util.regex.PatternSyntaxException: Syntax error in regexp pattern near index 8
  \{(\w+)}   at com.android.icu.util.regex.PatternNative.compileImpl ...
  ```
  `I18n.t` filled `{name}` placeholders with `Regex("\\{(\\w+)}")`. Desktop Java (and therefore every JVM/Robolectric test) accepts the bare `}`; Android's ICU regex engine does not. The first screen with a placeholder after the PIN is Home ("Namaste, {n}"), so every login crashed. Earlier screens (welcome, people list, PIN pad) have no placeholders, so they worked.
* **Fix:** placeholder filling by plain text scanning (`I18n.fill`); unit tests for it and a scan that refuses any `Regex(...)` with a bare `}`.
* **Verified on the device with the shipped 1.1.2 release APK:**

| Step | Result |
|---|---|
| Practice → Anil → PIN 1111 | ✅ Home (`screenshots-device/01`) |
| Next task, Projects, project detail, People, Me | ✅ no crash (`02`) |
| New company setup, PIN typed twice | ✅ Home "नमस्कार, Prashant · Shree Lifts" (`03`) |
| Add person (Sales, PIN 5927) → save → log out → Priya → PIN 5927 | ✅ Home "नमस्कार, Priya" (`04`) |
| Restart the app → Prashant → PIN 4831 | ✅ Home; `logcat -b crash` empty |

* The crash reporter added in 1.1.1 worked on the device: the next start showed the earlier crash with *Share report*.
* Also found and fixed on the device: the practice banner was drawn under the phone's status bar.
* Still not tested on a device: camera capture and GPS. The emulator has no camera app or location set up, so these were not exercised.
