# 08 — Performance and Compatibility Report

**Environment:** cloud Linux container, 4 vCPU, 15 GB RAM, OpenJDK 21. **There is no Android device or emulator:** the container has no `/dev/kvm`, so an x86_64 emulator can't start. Every figure below is from the JVM (Robolectric/unit tests) or the build output. Device measurements are listed as **not measured**.

## Measured

| Metric | Result | How |
|---|---|---|
| Release APK size | **1.41 MB** (1,414,457 bytes), R8 + resource shrinking, locales en/hi/mr | `ls -l app-release.apk` |
| Release AAB size | 3.70 MB (Play generates smaller per-device APKs) | `ls -l app-release.aab` |
| Debug APK size | 19.0 MB (not minified, includes test manifest) | `ls -l app-debug.apk` |
| Replay of the full history at start (engine only) | **20,007 records / 1,818 jobs in 191 ms** | `PerformanceTest`, after JIT warm-up |
| Task list for Sales over 1,818 jobs | 30 ms | same test |
| PIN check (PBKDF2-SHA256, 150,000 rounds) | 149 ms on the JVM | timed during development |
| Practice company build (101 records through the rules + SQLite insert) | ~210 ms on the JVM | timed during development |
| Small screen 320×640 dp, mdpi | Welcome and PIN screens fit and scroll | screenshots `30`, `31` |

## Estimated, not measured

Low-end phones (e.g. 4-core Cortex-A53) run this kind of Kotlin code roughly 5–10× slower than this machine. Expect about 1–2 s to replay 20,000 records, and 0.7–1.5 s per PIN check. Both run off the main thread with a spinner (the start-up replay was moved off the main thread after the review, C14). The original saves a snapshot every 300 records to start faster; that is not ported (12).

## Not measured (needs a phone or emulator)

Cold/warm launch time, memory, frame timing of screen changes and long lists, camera capture and image processing time, battery, ANR/crash behaviour. They need a device: see the commands in 09 §"Run on a phone".

## Compatibility by design

| Area | Decision |
|---|---|
| Android versions | minSdk 26 (8.0) → targetSdk 36 (16) |
| Location | `LocationManager` only (no Play services), so it works on phones without Google services |
| Camera | system camera app through `ACTION_IMAGE_CAPTURE`; no camera library |
| Fonts | system Noto fonts; Devanagari conjuncts render correctly (screenshots) |
| Emoji | decorative only; two Unicode-13 emoji replaced with older ones. A few Unicode-11 emoji (🧱) may show as boxes on Android 8; the text label is always next to them |
| ABIs | the app has no native code of its own; the release lists `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64` because of `libandroidx.graphics.path.so` (≈10 KB each), which comes with Compose |
| Large lists | Projects use `LazyColumn`; Home task lists are plain columns (fine for tens of tasks; a person with hundreds of tasks will scroll a long page) |
