# 03 — Android Setup and Configuration

## Identity

| Item | Value | Why |
|---|---|---|
| App name | `AIEC Elevator OS` (hi: `AIEC एलिवेटर OS`, mr: `AIEC एलिव्हेटर OS`) | From the brief; the launcher label follows the phone language |
| Application ID | `com.aiec.elevatoros` (debug: `com.aiec.elevatoros.debug`) | From the brief. The existing WebView app uses `com.allindiaelevators.os`, so the two IDs do not clash and both can be installed. I could not check Play Console ownership from here; the owner must confirm the ID is free before the first Play upload, because it is permanent |
| Version | `versionName 1.0.0`, `versionCode` from `AIEC_VERSION_CODE` (CI run number), default 1 | Each CI release is newer than the last |
| Orientation | Portrait, `adjustResize` for forms | As in the original |
| Languages | Marathi (default), Hindi, English, chosen in the app; `locales_config.xml` for Android 13+ per-app language | Original front door |

## SDK levels

| Setting | Value | Reason |
|---|---|---|
| `compileSdk` | 36 | Required by Compose 1.10 / AndroidX 2025.12 |
| `targetSdk` | 36 (Android 16) | Google Play has required API 36 for new apps and updates since 31 Aug 2026. Android 17 (API 37) is out, but Play only requires 37 from Aug 2027 |
| `minSdk` | 26 (Android 8.0) | `java.time` without desugaring, adaptive icons, and `PBKDF2WithHmacSHA256` (used for PIN compatibility with the web app) on every device. The original WebView wrapper used 24; 24–25 devices are a very small share in 2026 |

## Toolchain (verified in this session)

| Tool | Version |
|---|---|
| JDK | OpenJDK 21.0.13 (AGP 8.13 needs 17+; bytecode target 17) |
| Gradle | 8.14.3 (wrapper committed) |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.2.21 (+ Compose compiler plugin and serialization plugin 2.2.21) |
| Android SDK | platform `android-36`, build-tools `36.1.0` |

AGP 8.13.2, Kotlin 2.2.21 and Gradle 8.14.3 are a supported combination (AGP 8.13 needs Gradle ≥ 8.13). AGP 9.x was available, but it changes how Kotlin is applied; it can be adopted later without changing the code.

## Build configuration

* `release`: R8 minify + resource shrinking (`proguard-rules.pro`: keep line numbers only; the app uses no reflection), locale filter `en, hi, mr`, signing from the environment.
* `debug`: `.debug` suffix, so it installs next to the release build.
* Splash screen: `core-splashscreen` with navy background and the gold lift mark.
* Launcher icon: adaptive vector icon (`drawable/ic_launcher_foreground.xml`), monochrome layer for themed icons.
* Status/navigation bars: edge-to-edge (`enableEdgeToEdge`); Compose `Scaffold` handles insets.
* Network security config: cleartext off, system CAs only. The app makes no network calls in this release.
* Deep links: none, because no verified workflow needs one yet (QR resident page and join links are not ported, see 12).
* Repositories: `google()`, Google's Maven Central mirror, `mavenCentral()`. The mirror is listed first because Maven Central returned HTTP 429 to this build machine.

## Signing (summary; full guide in 10)

`app/build.gradle.kts` reads `AIEC_KEYSTORE`, `AIEC_KEYSTORE_PASSWORD`, `AIEC_KEY_ALIAS`, `AIEC_KEY_PASSWORD` from the environment, or from an untracked `keystore.properties`. Without them, `assembleRelease` makes an unsigned APK. `.gitignore` blocks `*.jks`, `*.keystore`, `keystore.properties` and `signing/`.
