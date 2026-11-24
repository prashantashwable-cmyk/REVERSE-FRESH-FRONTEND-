# 02 — Android Architecture Decision Record

**Status:** accepted · **Date:** 2026-11-23 · **Source audited:** `prashantashwable-cmyk/UNLIT-GPT-HTML` @ `605575a48885a78652e389a6f90cb01929730568` · **Target repo:** `prashantashwable-cmyk/REVERSE-FRESH-FRONTEND-`, branch `ccr-e4348b8b-dtb9ml`

## Decision

**A fresh native Android app in Kotlin with Jetpack Compose (Material 3).** It keeps the original's core design: every action is an *event*, and the same rules engine checks each event (role → visibility → stage → rule check) on every phone. Events are stored in app-private SQLite, and the state is rebuilt from them.

The product owner asked for "a better platform than HTML" and a fresh frontend built from zero. Of the options in the brief, that rules out A, B and C (all three keep the HTML app as the UI). Option D was chosen, with its scope limited to what could be ported and tested in this release (see *Known limitations*).

## Alternatives considered

| Option | What it would be | Why not chosen |
|---|---|---|
| A. Capacitor | Bundle `AIEC-Elevator-OS.html` (2.3 MB, one inline script) in a WebView and add native plugins | Keeps the HTML UI the owner asked to replace. The original's rules, storage (IndexedDB/localStorage) and crypto stay in a WebView. Not a fresh frontend. |
| B. Trusted Web Activity | Chrome shows the GitHub Pages PWA | Needs hosting plus Digital Asset Links, still HTML, and its offline behaviour depends on Chrome's storage being kept. |
| C. Native WebView (existing `android/` in the source) | The current wrapper | The audit found it loads live code from GitHub Pages and exposes a JS bridge (`AIECAndroid`). It also grants camera/mic/location to any origin and is signed with a key committed to the repo (High findings, 01 §8). |
| **D. Native Kotlin + Compose** | Port the domain engine, build the UI from zero | **Chosen.** A real Android UI with system camera, GPS, text-to-speech and SQLite. No remote code and no JS bridge. Release key outside the repo. The engine is plain Kotlin and unit-tested on the JVM. |

Flutter and React Native were also considered. Both add a second runtime and toolchain, and neither fits Android better than Kotlin/Compose. React Native would also bring back a JavaScript bridge.

## How the original's architecture maps to the new app

| Original (HTML) | New app (Kotlin) | Notes |
|---|---|---|
| `const L` dictionary [en, hi, mr] (H:295) | `assets/i18n.json`, same layout, 270 entries reused word-for-word | `tools/i18n/build.mjs` builds it; the unit test checks all 3 languages and placeholders |
| `ROLES`, `STAGES`, `PHASES` (H:5842–5851) | `domain/Model.kt` | All 10 roles, 25 stages, 7 phases, same keys |
| `RULES` + `validate()` (H:6798, 8280) | `domain/Rules.kt` | 41 of the 212 event types — the lead-to-handover path, service tickets, SOS, people and business rules — with the same event names |
| `apply()` reducer (H:8347) | `domain/Reducer.kt` | Same stage transitions for those types |
| `needs()` (H:8936) | `domain/Tasks.kt` | Tasks come from the same rules, so a task button never appears for an action the rule refuses |
| `ownerCovers` (H:6787) | `Rules.covers` | Office (Admin, or HQ when there is no Admin) covers Sales/Finance/QC only when nobody holds the role. Physical work is never covered |
| `calcQuote`, `paySchedule` (H:6082, 6105) | `domain/Pricing.kt` | JS `Math.round` and round-to-100 reproduced. A unit test checks a full quote to the rupee |
| `pinHash` PBKDF2-SHA256 150k, `p2$` (H:5352) | `domain/Pin.kt` | Same algorithm, salt prefix and encoding |
| IndexedDB / localStorage event log | SQLite (`data/EventDb.kt`, WAL), one database per mode (live / practice) | Event id is UNIQUE, so it doubles as the idempotency key |
| File sync between phones | `Repository.exportJson/importJson` | Every incoming event passes the rules again. Duplicates are skipped by id |
| Per-device ECDSA record signing, `KEY_ADDED` / `KEY_APPROVED` | `domain/Signing.kt`, `device/DeviceKey.kt` (Android Keystore) | Same idea and event names (since 1.1.0); the signed bytes are this app's own format |
| Encrypted company server (`server/`) | **Not in this release** | See 12 |

## Plugin / library requirements

There are no third-party runtime SDKs, no Google Play services, no Firebase and no analytics. Everything comes from AndroidX + Kotlin:
Compose BOM 2025.12.00 (Compose 1.10), activity-compose 1.11.0, lifecycle 2.9.4, core-splashscreen 1.2.0, exifinterface 1.4.1, kotlinx-serialization-json 1.9.0, kotlinx-coroutines 1.10.2. Tests use JUnit 4, Robolectric 4.16, Compose UI test and Roborazzi 1.52.0.

## Security implications

* No WebView, no JavaScript bridge, no remote code.
* `allowBackup=false` and data-extraction rules exclude every domain. Cleartext is forbidden by the network security config.
* The only exported component is the launcher activity. The FileProvider is not exported and exposes only `files/capture/` (camera output) and `cache/export/` (the records file the user chooses to share).
* `FLAG_SECURE` is on for outsider roles (technician, supplier, partner): no screenshots, blurred in the app switcher.
* Release signing comes only from environment/CI secrets. No keystore is committed.

## Offline implications

The app works fully offline: there is no network code path at all. Everything is saved durably before the screen says "saved". Sharing between phones uses files the user sends (WhatsApp, Drive or cable), as in the original.

## Maintenance cost and build complexity

One Gradle module, Kotlin only, about 3.9k lines of main code. The domain package has no Android dependency and is tested on the JVM in seconds. A standard Android Studio project; CI is `.github/workflows/android.yml`.

## Known limitations of the decision

41 of the original's 212 event types are ported. Not ported: the marketplace, partner companies, career ladder, chat, AMC visits, the encrypted server, and the rest listed in `12_KNOWN_LIMITATIONS_AND_RISKS.md`. The web app is unchanged and stays usable alongside this one, but the two **do not share data** in this release.
