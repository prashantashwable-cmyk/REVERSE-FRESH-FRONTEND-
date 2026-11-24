# 10 — Build and Signing Guide

## Prerequisites

* JDK 17 or newer (verified with OpenJDK 21.0.13)
* Android SDK with `platforms;android-36` and `build-tools;36.1.0` (`ANDROID_HOME` set, or `sdk.dir` in an untracked `local.properties`)
* Node.js 18+ (only for the dictionary check)
* Internet for the first Gradle run (Google Maven + Maven Central)

## Commands (exactly as run for this release)

```bash
git clone https://github.com/prashantashwable-cmyk/REVERSE-FRESH-FRONTEND-.git
cd REVERSE-FRESH-FRONTEND-
node tools/i18n/build.mjs --check          # every word in Marathi, Hindi and English
./gradlew testDebugUnitTest                # engine + dictionary + UI flow tests (Robolectric)
./gradlew recordRoborazziDebug             # same tests, also writes android-release/screenshots/*.png
./gradlew lintDebug
./gradlew assembleDebug                    # → app/build/outputs/apk/debug/app-debug.apk
# signed release (see "Signing" below for the four variables)
./gradlew assembleRelease bundleRelease    # → app/build/outputs/apk/release/app-release.apk
                                           #   app/build/outputs/bundle/release/app-release.aab
```

Without signing variables, `assembleRelease` produces `app-release-unsigned.apk`, which Android cannot install.

## Signing

The build reads these four values from environment variables, or from an untracked `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`):

| Variable | Meaning |
|---|---|
| `AIEC_KEYSTORE` | path to the `.jks` file |
| `AIEC_KEYSTORE_PASSWORD` | keystore password |
| `AIEC_KEY_ALIAS` | key alias |
| `AIEC_KEY_PASSWORD` | key password |

Signature schemes: v2 + v3 (v1 is off, because minSdk 26 does not need it).

### Make the company's release key (owner, once)

```bash
keytool -genkeypair -v -keystore aiec-release.jks -alias aiec -keyalg RSA -keysize 3072 -validity 10000
```

Keep the file and passwords in two safe places. **If they are lost, the installed app can never be updated.** For Google Play, enrol in Play App Signing and use this key as the *upload* key.

### CI release (`.github/workflows/android.yml`, job `release`)

1. GitHub → Settings → Environments → create `release` (add required reviewers if wanted).
2. Add the secrets `AIEC_KEYSTORE_B64` (`base64 -w0 aiec-release.jks`), `AIEC_KEYSTORE_PASSWORD`, `AIEC_KEY_ALIAS` and `AIEC_KEY_PASSWORD`.
3. Push a tag `v1.0.0`, or run the workflow manually. The job decodes the key into the runner's temp folder, builds the APK and AAB, deletes the key, verifies with `apksigner`, and uploads `AIEC-Elevator-OS.apk`, `.aab` and `SHA256SUMS.txt`.

## The build delivered with this release

No production key was created or used: the owner has to approve that (brief §12). The release APK and AAB in `dist/` are signed with an **evaluation key** made on the build machine. That key stays outside the repository and is not kept after this session.

```
apksigner verify --verbose --print-certs app-release.apk
Verifies
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Number of signers: 1
Signer #1 certificate DN: CN=AIEC Elevator OS evaluation build, O=AIEC, C=IN
Signer #1 certificate SHA-256 digest: 5fceaee0a3d36085697fc15081728b3e55b12ea1c081ef8a007455185e89ad5c
```

Consequences:
* Fine for installing and trying on phones.
* **Do not upload it to Google Play**, and do not hand it out as the long-term company app. A build signed with the company key cannot install over it: uninstall it first, which deletes the data on that phone.
* The production build must be made with the owner's key (CI job above). Record that certificate's SHA-256 in `11_RELEASE_CHECKLIST.md`, and keep using the same key for every future update.

## Verify a downloaded APK

```bash
sha256sum -c SHA256SUMS.txt
$ANDROID_HOME/build-tools/36.1.0/apksigner verify --print-certs AIEC-Elevator-OS-1.1.1-eval.apk
$ANDROID_HOME/build-tools/36.1.0/aapt2 dump badging AIEC-Elevator-OS-1.1.1-eval.apk | head -3
```
