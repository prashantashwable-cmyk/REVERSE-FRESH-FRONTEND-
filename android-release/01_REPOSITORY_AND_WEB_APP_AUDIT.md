# 01 — Repository and Web App Audit (AIEC Elevator OS)

| Item | Value |
|---|---|
| Source repository | https://github.com/prashantashwable-cmyk/UNLIT-GPT-HTML (local clone remote: `https://github.com/prashantashwable-cmyk/unlit-gpt-html`) |
| Starting commit | `605575a48885a78652e389a6f90cb01929730568` (commit date 2026-11-13 05:21 UTC) |
| Audit date | 2026-11-23 |
| Local clone | `/home/user/prashantashwable-cmyk/unlit-gpt-html` (shallow clone: `git rev-parse --is-shallow-repository` = `true`, 1 commit visible) |
| Audit mode | **Read-only.** No file in the source repository was modified. The script was copied to a scratch directory for parsing; tests were run from a scratch copy of the repository so that `tests/out/` was not created inside the source clone. |

Classification vocabulary used below: **VERIFIED** (code path read and/or exercised by a passing test run in this audit), **PARTIALLY IMPLEMENTED**, **DOCUMENTED ONLY**, **SIMULATED**, **MISSING**, **DEFECTIVE**, **UNVERIFIED** (not checked or not checkable in this audit).

Line numbers refer to `AIEC-Elevator-OS.html` (abbreviated **H:**) unless another file is named.

---

## 1. Repository inventory

### 1.1 Top level

| Path | Purpose | Runtime/build use | Dependencies | Risk / notes |
|---|---|---|---|---|
| `AIEC-Elevator-OS.html` | The entire application: 2,347,691 bytes, 16,992 lines. CSS H:11–286; one inline `<script>` H:293–16990. | Runtime (opened from file or served from GitHub Pages; loaded by the Android WebView from `https://prashantashwable-cmyk.github.io/UNLIT-GPT-HTML/AIEC-Elevator-OS.html`) | None bundled. Optional runtime load of Leaflet 1.9.4 from unpkg.com (H:13756–13757) and OSM tiles/Nominatim | Single 2.3 MB file; i18n dictionary `L` alone is H:295–~5277 (4,860 keys, 3 languages). Static budget check limits it to 2.4 MB (`tests/static.test.js`). |
| `index.html` | Meta-refresh redirect to `AIEC-Elevator-OS.html` | Runtime (hosting) | — | None |
| `404.html` | GitHub Pages 404 → `location.replace('/UNLIT-GPT-HTML/AIEC-Elevator-OS.html' + hash)` | Runtime (hosting) | — | Hard-coded path prefix |
| `sw.js` | Service worker, cache `aiec-os-v105`; network-first GET for same origin; push wake; notification click; periodic sync reminders | Runtime (https only) | — | See §3.5 |
| `manifest.webmanifest` | PWA manifest (`lang: mr`, portrait, standalone, icons) | Runtime | icons | — |
| `icon.svg`, `icon-192.png`, `icon-512.png` | App icons | Runtime | — | — |
| `server.json` | `{"url": "https://ycyvtdrtkdrnnkiszrxf.supabase.co/functions/v1/aiec"}` — default sync server address fetched at boot (H:16984) | Runtime | — | Contains only a public function URL; no key/JWT (§9) |
| `package.json` | Scripts `check` (`node tests/static.test.js`), `test` (static + `tests/run-all.js`), `shots`; devDependency `playwright@1.56.1`; Node >= 20 | Dev/test | playwright | No runtime deps |
| `package-lock.json` | Lock for playwright 1.56.1 | Dev/test | — | — |
| `.gitignore` | Ignores `node_modules/`, `tests/out/`, `dist/`, `server/.wrangler/`, `server/node_modules/` | — | — | — |
| `README.md` | 547 lines; product description, workflow, limits | Docs | — | Partly outdated: "Honest limits" says "There is no company server" (README:492) and "No build step and no external libraries" (README:539) although `server/` and Leaflet-from-CDN exist |
| `AIEC_ELEVATOR_OS_MASTER_SPECIFICATION_FINAL-4.md` | 3,901-line master specification (409 KB) | Docs | — | Not cross-checked line by line in this audit (UNVERIFIED vs. implementation) |
| `docs/AIEC_Elevator_OS_Master_Spec_English.txt` | 318-line English spec summary | Docs | — | — |
| `docs/screens.jpg` | 265 KB screenshot contact sheet | Docs | — | — |
| `.github/workflows/test.yml` | CI: `static` job (`npm run check`), `browser` job (4 shards, `npx playwright install --with-deps chromium`, `node tests/run-all.js --shard=N/4`; weekly run with `AIEC_SHIFT_DAYS=45`), `screens` job, and a job polling the public Pages URL for the `sw.js` cache version | CI | Node 22, Playwright | — |
| `.github/workflows/android.yml` | CI: builds `assembleRelease` with Gradle 8.9 / JDK 17; uploads APK artifact; optional Play build if `AIEC_KEYSTORE_B64` secret exists | CI | Gradle, AGP 8.7.3 | Release APK is signed with the public test key when no secret is set (§9) |
| `android/` | Existing WebView wrapper (see 1.3) | Build | AGP 8.7.3, Kotlin 2.0.21 | — |
| `server/` | Encrypted-mailbox sync server (see 1.2) | Deployed separately | wrangler ^4.20.0 (dev), `npm:postgres@3.4.5` (Supabase) | — |
| `tests/` | 112 `*.test.js` files + `lib.js`, `run-all.js`, `shots-signin.js`, `fixtures/golden-v1.json` (1.2 MB total) | Test | playwright | See 1.4 and §8 |

### 1.2 `server/`

| Path | Lines | Purpose | Runtime use | Notes |
|---|---|---|---|---|
| `server/core.mjs` | 319 | Request handler `handle(req, env, store)` + `memoryStore()` + `d1Store()`. Endpoints: `GET /health`, `GET /vapid`, `POST /setup`, `POST /invite/:id/open`, `GET /backups` (admin secret or restore proof), `POST /report` (anonymous lift-QR report), `GET/PUT /pubkey`, `POST /request` (join request), `PUT/DELETE /box/:id`, `PUT /invite/:id`, `POST/DELETE /push-sub`, `PUT/GET /backups/...`, `GET /boxes`, `POST /send/:box`, `GET /recv/:box` | All server variants | Stores only opaque blobs; tokens stored as SHA-256 (core.mjs:6, 67–76); per-minute rate limits (core.mjs:9, 285, 312–317); CORS `*` (core.mjs:63) |
| `server/supabase.ts` | 17 | Supabase Edge Function entry (Deno), Postgres via `pgstore.mjs`; strips `/functions/v1/aiec` prefix | Live deployment target per `server.json` | Requires "Verify JWT" off (comment line 3) |
| `server/supabase-test.ts` | 16 | Throw-away test function: creates a fresh schema `t_<run>` per URL path | Test deployment | Comment says "Replace it with a stub after testing, so nobody else can make tables" — if left deployed, any caller can create schemas (deployment state UNVERIFIED) |
| `server/pgstore.mjs` | 54 | Postgres store + `pgTables(schema)` DDL | Supabase | — |
| `server/sqlstore.mjs` | 48 | SQLite store (Durable Object / node:sqlite) | Cloudflare DO, local | — |
| `server/do.mjs` | 18 | Cloudflare Worker + Durable Object `Company` (one object for the whole company) | Cloudflare (default `wrangler.toml`) | — |
| `server/worker.mjs` | 13 | Cloudflare Worker on D1 + R2, daily cleanup cron | Cloudflare (`wrangler-d1.toml`) | — |
| `server/local.mjs` | 25 | Node HTTP server for tests/laptop (memory or node:sqlite) | Tests | — |
| `server/schema.sql` | 9 | D1 tables `meta, boxes, msgs, invites, subs` | D1 | — |
| `server/vapid.mjs` | 8 | Generates VAPID keypair and prints wrangler commands | One-off | Keys are not written to disk |
| `server/bundle.mjs` | 19 | Builds `dist/aiec-server-worker.js` and `dist/aiec-app-website.zip` | One-off | Uses `python3` zip |
| `server/wrangler.toml`, `server/wrangler-d1.toml` | 17 / 20 | Cloudflare configs | Deploy | `database_id = "PASTE-YOUR-DATABASE-ID-HERE"`; no secrets committed |
| `server/package.json` | 7 | `wrangler deploy` | Deploy | — |
| `server/README.md` | 197 | Server setup/behaviour | Docs | States "Rules are checked on the phones… it does not check the business rules" (server/README.md:146) |

### 1.3 `android/`

| Path | Purpose | Notes |
|---|---|---|
| `android/build.gradle.kts` | AGP 8.7.3, Kotlin 2.0.21 plugins | — |
| `android/settings.gradle.kts` | Single `:app` module | — |
| `android/gradle.properties` | `-Xmx2g`, AndroidX | — |
| `android/app/build.gradle.kts` | `applicationId com.allindiaelevators.os`, minSdk 24, target/compile 35, `versionCode = GITHUB_RUN_NUMBER`; signing config `aiecTest` with `storePassword = "aiectest"`, `keyPassword = "aiectest"` (lines 22–31); release uses `store` config only if `AIEC_KEYSTORE` env exists, else the test key (line 47); `isMinifyEnabled = false` | Hard-coded keystore password |
| `android/aiec-test.jks` | PKCS12 keystore, alias `aiec-test`, `CN=AIEC Elevator OS Test, O=All India Elevators Company, C=IN`, valid 2026-11-02 → 2056-12-14, cert SHA-256 `9D:81:56:B1:…:9C:36` (verified with `keytool -list -v -storepass aiectest`) | Public signing key (§9) |
| `android/app/src/main/AndroidManifest.xml` | Permissions INTERNET, ACCESS_NETWORK_STATE, CAMERA, RECORD_AUDIO, MODIFY_AUDIO_SETTINGS, FINE/COARSE LOCATION, VIBRATE, POST_NOTIFICATIONS, WRITE_EXTERNAL_STORAGE (maxSdk 28); `allowBackup="false"`, `usesCleartextTraffic="false"`; single exported `MainActivity` (singleTask, portrait) with VIEW intent-filter for `https://prashantashwable-cmyk.github.io/UNLIT-GPT-HTML*` (`autoVerify="false"`) | — |
| `android/app/src/main/java/com/allindiaelevators/os/MainActivity.kt` | 335 lines: full-screen WebView loading `START_URL` (line 55); JS bridge `AIECAndroid` (`saveFile`, `print`, `version`, `exit`, lines 278–305); permission bridging; external links; offline retry view | See §9 |
| `android/app/src/main/res/values*/strings.xml` (en/hi/mr), `themes.xml` (+v31), `colors.xml`, `mipmap-xx(x)hdpi/ic_launcher.png` | Resources | — |
| `android/README.md` | 52 lines; APK install/Play signing steps; states the test key password is public on purpose | — |

No Gradle wrapper is committed under `android/` (CI uses `gradle/actions/setup-gradle` with Gradle 8.9).

### 1.4 `tests/` (summary)

* 112 `*.test.js` files, every one listed in `tests/run-all.js` (verified programmatically: 0 files missing from the runner list). The runner executes each file in a child Node process sequentially; `--shard=k/n` takes every n-th file.
* `tests/lib.js`: launches Playwright Chromium against `file://…/AIEC-Elevator-OS.html` with fake camera/mic, Pune geolocation, `Asia/Kolkata`, aborts unpkg/OSM requests (offline), supports `AIEC_SHIFT_DAYS` time shift, collects `pageerror`/console errors, sets `App.set.devNoInstallGate = true`.
* `tests/static.test.js`: Node-only checks (size budget, sw.js cache version == `APP_BUILD`, script parses, no duplicate top-level names, i18n completeness).
* Coverage areas by filename: practice roles, full lead-to-handover e2e, sync/server (`server-*.test.js` start `server/local.mjs`), permissions-denied, security/security2, partner, safety, privacy, statutory, quality, sales, material, tickets, finance (`bp4finance`, `money`, `moneyline`, `payplans`, `recon`, `supplierbills`), marketplace (`market`, `bp5market`), escalation, notifications, golden replay (`golden.test.js` + `fixtures/golden-v1.json`), snapshot, time-travel, journeys A–H, sign-in L1–L5, layout/UI.

---

## 2. Runtime architecture

### 2.1 Entry points

| Entry | Evidence |
|---|---|
| `index.html` → meta refresh → `AIEC-Elevator-OS.html` | `index.html:6` |
| App boot: `boot()` at end of script | H:16989; boot body registers SW (H:16974), `navigator.storage` persistence (H:16979), `Notify.start()`, `Sync.start()`, join-request polling, fetch of `server.json` (H:16984) |
| Android: `MainActivity.onCreate` → `web.loadUrl(START_URL)` (remote GitHub Pages URL, not a bundled asset) | `MainActivity.kt:53–55, 117` |
| Mode split: `App.mode` = `practice` (namespace `p:`) or `real` (namespace `r:`) | H:9333 |
| Practice data seeding | `seedPractice()` H:16784 (15 sample users per README:547; test output shows practice projects AIEC-P001…P013) |
| Install gate: real company use only in installed PWA/Android app | `installGate` H:9509, applied in `render()` H:9537 |

### 2.2 Storage (`KV`, H:5797–5839)

* `KV.init()` tries **IndexedDB** database `aiec-elevator-os` v1, object store `kv` (3 s timeout) → mode `idb`; on failure probes **localStorage** (keys prefixed `aiec:`) → mode `ls`; otherwise **memory** `Map` → mode `mem` (data lost on close).
* API: `get/set/del/keys(prefix)`; `ls` mode JSON-serialises; quota error toasts `err_storage_full` (H:5822).
* Keys used (namespace `App.ns` = `p:`/`r:`): `ev:<i>` event chunks + `evn` count (`saveAll` H:9310–9323), `snap` snapshot (H:8931), `sk:<uid>` signing key (H:5414, 5441), `ck` company sync key (H:5493, 5510), `bk`, `rk` (backup/recovery keys, H:5529), `reports`, `settings`.
* Data is stored **unencrypted at rest** in IndexedDB/localStorage (events, PIN hashes, company key `ck` as base64). The ECDSA private signing key is a non-extractable `CryptoKey` in IDB mode, but exported as JWK when in `ls` mode (H:5439–5441).

### 2.3 Event-sourcing model

* Single mutation path: `perform(type, pid, data, opt)` H:9400–9428 ("the only way to change data", H:9360).
* Event shape (H:9407): `{ id: <dev>-<rid6>, n: <per-device seq>, t: ISO time, by: uid, type, pid, data, dev, v: EV_V }`, plus `sig` when signed. `EV_V = 2` (H:9364). Events newer than the app's `EV_V` are rejected with `err_newer_app` (H:8281).
* Monotonic time: a new event's `t` is bumped past the last event's `t` (H:9405).
* Validation: `validate(S, ev)` H:8280–8308 — unknown type; org bootstrap; first-user bootstrap must be the Owner; user must exist and be active; **role check** against `RULES[type].roles` with `ownerCovers()` small-team exception (H:6787–6794); project existence; `canSee()` visibility (H:6169) unless `open`; **stage check** against `RULES[type].stages`; marketplace block; `gate()`; `check()`; `needsSecond()` two-person approval (H:6719–6733).
* If `err_needs_second`, `perform` converts the action into an `APPROVAL_REQUESTED` event wrapping the original (H:9411–9416); the approved inner event is replayed via `innerEvent()` (H:6795).
* PIN re-check (2-minute grace) for `REFUND_DECIDED`, `REFUND_PAID`, `CONFIG_SET`, `DATA_EXPORTED`, waived `CHARGE_SETTLED`, role changes (H:9369–9376).
* Undo: 14 simple event types, own record, within 5 minutes, nothing later on the job (H:6761–6772); implemented as an `UNDONE` event and full rebuild skipping the target (H:8867–8882).
* Reducer: `apply(S, ev)` H:8347–8866, a single `switch (ev.type)` that mutates the in-memory state `S` (project record template `newProject()` H:8321–8333).
* Signing (`Sign`, H:5408–5480): per-person ECDSA P-256 key created at first real-mode login (`Sign.ensure`), published as `KEY_ADDED`; second phones need `KEY_APPROVED` by Owner/Admin. Signature v2 payload = `[2, id, n, t, by, type, pid, dev, publicFields, [[privateField, salted sha256]...], v]` (H:5390–5404) so private fields (PINs, phone numbers, prices; list `PRIVATE_V2` H:5367–5380) can be cut from shared files without breaking signatures. Imported packs are checked by `Sign.check()` (H:5459 ff.): records from a person with a known approved key must carry a valid signature; unsigned records are accepted only if the person has no active key or the pack is signed by an Owner/Admin. Signing happens only in `real` mode (`Sign.ok`, H:5410).
* Sync (`Sync`, H:5489–5790): company key `ck` (32 random bytes) on office phones; per-box token = HMAC(ck, `tok:`+box) truncated to 48 hex chars, per-box AES-GCM key = HMAC(ck, `key:`+box) (H:5494–5496); messages AES-GCM encrypted (`lock/unlock` H:5497–5498); join requests use ECDH P-256 + SHA-256 → AES-GCM to the office public key (H:5708–5718); invites use a PBKDF2 key from a one-time 6-digit code (H:5625, server core.mjs:113–125); backups PBKDF2 from a recovery code, chunked 6 MB (H:5662, 5679). Poll every 60 s when visible, 10 min when hidden (H:5784).

### 2.4 Rebuild and snapshot

* `rebuild(events, skip)` H:8867–8882: sorts events, replays from `emptyState()` through `validate`+`apply`; rejected events collected in `S.rejected`; second pass drops undone events.
* Snapshot (H:8884–8933): every `SNAP_EVERY = 300` events; trusted only if app fingerprint (FNV of the whole script text + length + `EV_V`), event-ID checksum, last ID match and no later `UNDONE` (H:8908–8916). Any code change invalidates all snapshots (full replay).
* Consequence for a rewrite: business rules are time-versioned with date constants (e.g. `LIMITS_FROM`, `CONTRACT_FROM`, `PAYDEF_FROM`, `PLAN_FROM`, `ISC_FROM`, `ACT_FROM`, `SURVEY_FROM`, H:5878–5905, 6123) so that old events stay valid on replay. A native rewrite that replays the same event log must reproduce these date-dependent branches exactly.

### 2.5 Service worker (`sw.js`, 61 lines)

* Cache `aiec-os-v105`; shell pre-cache of `./`, `index.html`, `AIEC-Elevator-OS.html`, manifest, icons (sw.js:3–5).
* `fetch`: only same-origin GET; **network first**, cache copy on success, fallback to cache then to the app HTML (sw.js:15–21).
* `push`: no payload; posts `{aiec:'sync'}` to open clients, or shows a generic notification (sw.js:24–31).
* `notificationclick` focuses/open app with `{aiec:'openTasks'}` (sw.js:34–41).
* `periodicsync` tag `aiec-check`: reads `aiec-notify.json` from cache `aiec-notify` and shows due reminders (sw.js:45–61).
* `APP_BUILD = 105` (H:9366) must equal the cache number (checked by `tests/static.test.js`, passed).

### 2.6 External URLs contacted (grep of `https?://` in H and sw.js)

| URL | Where | Purpose | Notes |
|---|---|---|---|
| `https://unpkg.com/leaflet@1.9.4/dist/leaflet.css` / `.js` | H:13756–13757 | Street map | Injected `<script>` **without Subresource Integrity**; CSP (H:6) does not restrict `script-src` |
| `https://tile.openstreetmap.org/{z}/{x}/{y}.png` | H:13816 | Map tiles | Leaks viewed coordinates to OSM |
| `https://nominatim.openstreetmap.org/reverse?...` | H:16041 | GPS → address | Sends lat/lng to OSM |
| `https://wa.me/91<phone>?text=…` / `https://wa.me/<n>` | H:10483, 12263 and others (11 occurrences) | Open WhatsApp with prefilled text | — |
| `https://www.google.com/maps/search/` and `/dir/` | H:9657–9658 | Map / directions links | — |
| `https://prashantashwable-cmyk.github.io/UNLIT-GPT-HTML/` | H:9498 (`APP_URL`) | QR stickers, share links | — |
| Company server (configurable; default from `server.json` = Supabase function) | H:5501, 5513, 5652, 5681, 5691, 12114, 12128, 12156, 12206, 16531, 16984 | Sync, setup, invites, backups, join requests, resident reports | All payloads encrypted client-side except `/setup` (office token hash, org name), push subscriptions and anonymous metadata |
| Push services (`fcm.googleapis.com`, Mozilla, Apple, Windows) | server core.mjs:14 | VAPID push wake | Server-side only |
| `http://www.w3.org/2000/svg` | inline SVG namespace | — | not a network call |

`sw.js` contacts no external URL.

### 2.7 Browser APIs used

| API | Evidence (H:) | Use |
|---|---|---|
| `navigator.mediaDevices.getUserMedia` (video) + `MediaRecorder` + canvas JPEG | 9689–9720 | Photo capture (960 px, JPEG 0.7) and 10 s video evidence |
| `getUserMedia({audio})` + `MediaRecorder` | 9770–9790 | Voice notes (hashed with SHA-256) |
| `navigator.geolocation.getCurrentPosition` (high accuracy, 15 s) | 9646, 15788, 16648 | GPS on lead capture, check-in, SOS |
| `speechSynthesis` | 9590–9592 | Read screens aloud (3 languages) |
| `SpeechRecognition` / `webkitSpeechRecognition` | 9302, 9621–9623 | Voice dictation into fields |
| `Notification` / `ServiceWorkerRegistration.showNotification` / `Notification.requestPermission` | 15389–15396 | Task alerts |
| `pushManager` (Web Push) | 5562 | Server wake-ups |
| `periodicSync` | 15466 | Background reminders |
| `AudioContext` oscillator tones; `navigator.vibrate` | 15369, 15387, 11887 | Alarm/task sounds |
| `navigator.wakeLock` | 9457–9462 | Keep screen on during wizards/checklists |
| `crypto.subtle` (SHA-256, PBKDF2, ECDSA P-256, ECDH P-256, AES-GCM, HMAC) + `crypto.getRandomValues` | 32 uses, e.g. 5343, 5354, 5408–5480, 5497 | Hashing, PINs, signatures, encryption |
| WebAuthn `navigator.credentials.create/get` (platform authenticator) | 9382–9392 | Optional fingerprint/face unlock (local only, no server verification) |
| `indexedDB`, `localStorage` | 5801, 5794–5819 | Storage |
| `navigator.share`, `navigator.clipboard` | 12268, 12324, 14611; 16322–16419 | Sharing files/links |
| `window.print` / Android `AIECAndroid.print` | 16597, 16411 | PDF printing |
| `FileReader`, file inputs | 9502, 9716, 9786 | Imports, media |
| `navigator.storage` (persist) | 16979 | Persistent storage request |
| `navigator.onLine`, `online/offline` events | 5583, 5788, 10292 | Sync state |
| `requestIdleCallback` | 13766 | Map loading |

Not used: `BarcodeDetector` (no in-app QR scanning was found; QR is produced, residents scan with their own camera), `DeviceMotion`, `BroadcastChannel`, `sessionStorage`.

---

## 3. Domain model

### 3.1 Roles (H:5842)

`ROLES = ['owner', 'admin', 'field', 'sales', 'finance', 'qc', 'supplier', 'partner', 'tech', 'customer']` (+ `system` icon only, H:5843). The Owner is displayed as "HEAD QUARTER" everywhere (H:8341 `HQ_NAME`). `OUTSIDERS = ['partner','tech','supplier']` (H:14528) get watermark/guard UI and access logging (H:14472). Technician grades: trainee → helper → technician → senior → qc (career ladder; `PROMOTED/DEMOTED` events, README:166).

### 3.2 Phases and the 25 stages (H:5844–5851)

Phases: 0 lead 📍, 1 sales 🤝, 2 agree ✍️, 3 site 🏗️, 4 material 🚚, 5 install 🔧, 6 handover 🔑.

| # | Stage key | Phase (index) |
|---|---|---|
| 1 | `new` | lead (0) |
| 2 | `returned` | lead (0) |
| 3 | `accepted` | sales (1) |
| 4 | `contacted` | sales (1) |
| 5 | `requirements` | sales (1) |
| 6 | `quote_approval` | sales (1) |
| 7 | `quote_ready` | sales (1) |
| 8 | `quoted` | sales (1) |
| 9 | `won` | agree (2) |
| 10 | `agreement` | agree (2) |
| 11 | `signed` | agree (2) |
| 12 | `site_prep` | site (3) |
| 13 | `qc_site` | site (3) |
| 14 | `site_fix` | site (3) |
| 15 | `site_ok` | site (3) |
| 16 | `material_order` | material (4) |
| 17 | `dispatched` | material (4) |
| 18 | `arrived` | material (4) |
| 19 | `installing` | install (5) |
| 20 | `qc_final` | install (5) |
| 21 | `install_fix` | install (5) |
| 22 | `final_pay` | handover (6) |
| 23 | `handover` | handover (6) |
| 24 | `amc` | handover (6) — terminal "live lift" state |
| 25 | `lost` | −1 (off-path terminal, reopenable) |

Helper groups: `PRE_WON` (8 stages up to `quoted`, H:5853), `SALES_TALK` (H:5854), `OPEN_STAGES` = all but `lost`/`amc` (H:5855). `STAGE_BACK` map (H:6007): `site_ok→qc_site`, `material_order→site_ok`, `dispatched→material_order`, `qc_final→installing`.

### 3.3 RULES table (H:6798–8277)

Extraction method: the script was copied to the scratch directory and lines H:5842–8277 (domain constants + `RULES`) were evaluated in Node inside a `with(Proxy)` sandbox that stubs every outside identifier; `roles`, `stages`, `project`, `open`, and presence of `gate`/`check` were read from the resulting objects. No identifier in `roles` or `stages` resolved to a stub (verified), so the lists below are exact. No later code mutates `RULES` (grep for `RULES.X =` / `Object.assign(RULES` returned nothing).

Totals: **212 event types**; 139 project-scoped; 64 with an explicit stage list; 34 with a `gate()`; `ORG_CREATED` is bootstrap-only. Event types that every role may emit: `KEY_ADDED, PIN_ALERT, DEVICE_USED, PIN_UPGRADED, ACCESS_LOGGED, CHAT_POSTED, CHAT_STATUS, UNDONE, PRIVACY_ACCEPTED`.

Number of event types listing each role: owner 145, admin 142, sales 78, tech 51, qc 45, customer 38, finance 37, partner 35, supplier 27, field 25.

Important qualifications to the matrix:
* `ownerCovers()` (H:6787) lets Admin (or Owner when there is no Admin) perform 30 `COVER_EV` sales/finance/QC events (H:6776–6778) when no other active person holds the needed `COVER_ROLES` (`sales, finance, qc`). Physical work roles are never covered.
* Most entries have additional `check()`/`gate()` conditions (ownership of the job, separation of duties such as "QC cannot inspect own job/company" H:7718–7720, "payment confirmer ≠ recorder" via `coverExcl` H:6780–6786, evidence photo counts, consent flags).
* `canSee()` (H:6169–6185) further restricts project events per role (field: own leads; sales: unassigned or own; supplier/tech/partner: assigned jobs; customer: own project).

### 3.4 Role–permission matrix (event type × role)

Legend: X = role listed in `RULES[type].roles`. Proj = project-scoped (`open` = visibility check skipped). G = has `gate()`, C = has `check()`. "Allowed stages" is `RULES[type].stages` ("any" = project-scoped without stage restriction). HTML line = definition line in H.


| # | Event type | HTML line | Own | Adm | Fld | Sal | Fin | QC | Sup | Ptr | Tch | Cus | Proj | Gate/Check | Allowed stages |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | ORG_CREATED | 6799 |  |  |  |  |  |  |  |  |  |  | N | boot | — (not project-scoped) |
| 2 | KEY_ADDED | 6800 | X | X | X | X | X | X | X | X | X | X | N | C | — (not project-scoped) |
| 3 | KEY_APPROVED | 6801 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 4 | PIN_ALERT | 6803 | X | X | X | X | X | X | X | X | X | X | N | C | — (not project-scoped) |
| 5 | DEVICE_USED | 6804 | X | X | X | X | X | X | X | X | X | X | N | C | — (not project-scoped) |
| 6 | DEVICE_ALERT_SEEN | 6805 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 7 | PIN_ALERT_SEEN | 6806 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 8 | PIN_UPGRADED | 6807 | X | X | X | X | X | X | X | X | X | X | N | C | — (not project-scoped) |
| 9 | USER_ADDED | 6808 | X | X |  | X |  |  |  | X |  |  | N | C | — (not project-scoped) |
| 10 | USER_UPDATED | 6829 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 11 | PARTNER_PROFILE | 6844 | X | X |  |  |  |  |  | X |  |  | N | C | — (not project-scoped) |
| 12 | MEMBER_APPROVED | 6853 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 13 | MEMBER_REMOVED | 6854 | X | X |  |  |  |  |  | X |  |  | N | C | — (not project-scoped) |
| 14 | TEAM_SET | 6863 | X | X |  |  |  |  |  | X |  |  | Y | GC | any |
| 15 | CONFIG_SET | 6873 | X |  |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 16 | SHIFT_STARTED | 6891 |  |  | X |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 17 | SHIFT_ENDED | 6892 |  |  | X |  |  |  |  |  |  |  | N |  | — (not project-scoped) |
| 18 | ENQUIRY_ADDED | 6894 | X | X |  | X |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 19 | FIN_INTEREST | 6895 | X | X |  | X |  |  |  |  |  | X | N | C | — (not project-scoped) |
| 20 | FIN_WITHDRAWN | 6905 | X | X |  | X | X |  |  |  |  | X | N | C | — (not project-scoped) |
| 21 | FIN_REFERRED | 6908 | X | X |  |  | X |  |  |  |  |  | N | C | — (not project-scoped) |
| 22 | MODEL_SET | 6912 | X | X |  |  |  |  |  |  |  |  | Y | C | accepted, contacted, requirements |
| 23 | RFQ_SENT | 6918 | X | X |  |  |  |  |  |  |  |  | Y | GC | any |
| 24 | PQUOTE_SENT | 6924 |  |  |  |  |  |  |  | X |  |  | Y | GC | any |
| 25 | MK_PICKED | 6932 | X | X |  | X |  |  |  |  |  | X | Y | GC | any |
| 26 | MK_PICK_HELD | 6936 | X | X |  |  |  |  |  |  |  |  | Y | GC | any |
| 27 | MK_ANSWER | 6937 |  |  |  |  |  |  |  | X |  |  | Y | GC | any |
| 28 | MK_INTRODUCED | 6939 | X | X |  | X |  |  |  |  |  | X | Y | GC | any |
| 29 | MK_PAY_RECORDED | 6942 |  |  |  |  |  |  |  | X |  | X | Y | GC | any |
| 30 | MK_PAY_VERIFIED | 6951 | X | X |  |  | X |  |  | X |  | X | Y | GC | any |
| 31 | MK_READY | 6956 |  |  |  |  |  |  |  | X |  |  | Y | GC | any |
| 32 | MK_INSPECTED | 6957 |  |  |  |  |  | X |  |  |  |  | Y | GC | any |
| 33 | MK_DONE | 6962 |  |  |  |  |  |  |  | X |  |  | Y | GC | any |
| 34 | COMM_STATUS | 6964 | X | X |  |  | X |  |  |  |  |  | N | C | — (not project-scoped) |
| 35 | COMM_CREDIT | 6969 | X | X |  |  | X |  |  |  |  |  | N | C | — (not project-scoped) |
| 36 | ENQUIRY_CLOSED | 6975 | X | X | X | X |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 37 | JOIN_DECIDED | 6976 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 38 | CUSTOMER_LINKED | 6977 | X | X |  | X |  |  |  |  |  |  | Y | C | any |
| 39 | LEAD_CREATED | 6986 | X | X | X | X |  |  |  |  |  | X | N | C | — (not project-scoped) |
| 40 | LEAD_EDITED | 7002 |  |  |  |  |  |  |  |  |  | X | Y | GC | any |
| 41 | LEAD_INFO_ADDED | 7004 |  | X | X | X |  |  |  |  |  |  | Y(open) | C | 23 stages: all except amc, lost |
| 42 | REFERRAL_SET | 7006 | X | X |  | X |  |  |  |  |  |  | Y | C | 23 stages: all except amc, lost |
| 43 | REFERRAL_REWARDED | 7013 | X | X |  |  |  |  |  |  |  |  | Y | C | any |
| 44 | SOC_SET | 7021 | X | X |  | X |  |  |  |  |  |  | Y | GC | any |
| 45 | SOC_APPROVED | 7030 | X | X |  | X |  |  |  |  |  | X | Y | C | any |
| 46 | SOC_VOTED | 7040 | X | X |  | X |  |  |  |  |  | X | Y | C | any |
| 47 | SITE_GROUP_SET | 7049 | X | X |  | X |  |  |  |  |  |  | Y | C | 23 stages: all except amc, lost |
| 48 | FINISH_APPROVED | 7058 | X | X |  | X |  |  |  |  |  | X | Y | C | won, agreement, signed, site_prep, qc_site, site_fix, site_ok |
| 49 | STOCK_ITEM_SET | 7066 | X | X |  |  | X |  |  |  |  |  | N | C | — (not project-scoped) |
| 50 | STOCK_MOVED | 7071 | X | X |  |  | X |  |  |  | X |  | N | C | — (not project-scoped) |
| 51 | EXPENSE_ADDED | 7084 | X | X |  | X | X | X |  |  | X |  | Y | C | any |
| 52 | EXPENSE_DECIDED | 7095 | X | X |  |  | X |  |  |  |  |  | Y | C | any |
| 53 | SUP_COMPARED | 7105 | X | X |  |  |  |  |  |  |  |  | Y | C | site_ok |
| 54 | LEAD_ACCEPTED | 7114 |  | X |  | X |  |  |  |  |  |  | Y | C | new |
| 55 | LEAD_RETURNED | 7120 |  | X |  | X |  |  |  |  |  |  | Y | C | new |
| 56 | LEAD_RESUBMITTED | 7121 | X | X | X | X |  |  |  |  |  |  | Y |  | returned |
| 57 | CONTACT_CORRECTED | 7122 | X | X | X | X |  |  |  |  |  |  | Y | C | 23 stages: all except amc, lost |
| 58 | CONTACT_LOGGED | 7131 |  |  |  | X |  |  |  |  |  |  | Y | C | accepted, contacted, requirements, quote_ready, quoted |
| 59 | FOLLOWUP_SET | 7139 |  |  |  | X |  |  |  |  |  |  | Y | C | accepted, contacted, requirements, quote_ready, quoted |
| 60 | REQ_SAVED | 7140 |  |  |  | X |  |  |  |  |  |  | Y | C | contacted, requirements, quoted |
| 61 | SURVEY_DONE | 7148 | X | X | X | X |  |  |  |  |  |  | Y | C | accepted, contacted, requirements, quote_ready, quoted, new |
| 62 | QUOTE_CREATED | 7159 |  |  |  | X |  |  |  |  |  |  | Y | C | requirements, quoted |
| 63 | QUOTE_DECISION | 7171 | X | X |  |  |  |  |  |  |  |  | Y | C | quote_approval |
| 64 | QUOTE_SENT | 7182 |  |  |  | X |  |  |  |  |  |  | Y |  | quote_ready |
| 65 | OPTION_CHOSEN | 7184 | X | X |  | X |  |  |  |  |  | X | Y | C | quoted |
| 66 | DEAL_WON | 7192 |  |  |  | X |  |  |  |  |  |  | Y | C | quoted |
| 67 | REFUND_PROPOSED | 7201 |  |  |  |  | X |  |  |  |  |  | Y | C | lost |
| 68 | REFUND_DECIDED | 7202 | X | X |  |  |  |  |  |  |  |  | Y | C | any |
| 69 | REFUND_PAID | 7203 |  |  |  |  | X |  |  |  |  |  | Y | C | any |
| 70 | OPT_OUT | 7205 | X | X |  | X |  |  |  |  |  | X | Y | C | any |
| 71 | COUNTER_OFFER | 7207 |  |  |  | X |  |  |  |  |  | X | Y | C | quoted |
| 72 | DEAL_LOST | 7210 | X | X |  | X |  |  |  |  |  |  | Y | C | new, returned, accepted, contacted, requirements, quote_approval, quote_ready, quoted |
| 73 | PROJECT_CANCELLED | 7211 | X |  |  |  |  |  |  |  |  |  | Y | GC | any |
| 74 | DEAL_REOPENED | 7215 | X | X |  |  |  |  |  |  |  |  | Y | C | lost |
| 75 | AGREEMENT_PREPARED | 7216 |  |  |  | X |  |  |  |  |  |  | Y |  | won |
| 76 | AGREEMENT_SIGNED | 7217 |  |  |  | X |  |  |  |  |  | X | Y | C | agreement |
| 77 | PAYMENT_RECORDED | 7226 |  |  |  | X | X |  |  |  |  | X | Y | GC | any |
| 78 | PAYMENT_STATUS | 7244 |  |  |  |  | X |  |  |  |  |  | Y | C | any |
| 79 | PREP_ITEM_DONE | 7256 |  |  |  | X |  |  |  |  |  | X | Y | C | site_prep |
| 80 | QC_REQUESTED | 7263 |  |  |  | X |  |  |  |  |  | X | Y | C | site_prep, site_fix |
| 81 | QC_SITE_RESULT | 7267 |  |  |  |  |  | X |  |  |  |  | Y | C | qc_site |
| 82 | QC_SITE_CORRECTED | 7291 | X | X |  |  |  | X |  |  |  |  | Y | GC | site_ok |
| 83 | INFEASIBLE_DECIDED | 7304 | X | X |  |  |  |  |  |  |  |  | Y | GC | qc_site |
| 84 | DRAWING_APPROVED | 7309 | X | X |  |  |  |  |  |  |  |  | Y | GC | any |
| 85 | CORRECTION_DONE | 7317 |  |  |  | X |  |  |  |  | X | X | Y | C | site_fix, install_fix, installing |
| 86 | GAD_SIGNED | 7328 | X | X |  | X |  |  |  |  |  | X | Y | C | site_ok |
| 87 | DAMAGE_REPORTED | 7341 | X | X |  | X |  | X |  |  | X |  | Y | C | arrived, installing, qc_final, install_fix |
| 88 | DAMAGE_DECIDED | 7350 | X | X |  |  |  |  |  |  |  |  | Y | C | any |
| 89 | CHARGE_SETTLED | 7360 | X | X |  |  | X |  |  |  |  |  | Y | C | any |
| 90 | LIFT_MODE_SET | 7369 | X | X |  |  |  | X |  |  | X |  | Y | C | qc_final, final_pay, handover, amc |
| 91 | RELEASED | 7377 | X | X |  |  |  |  |  |  |  |  | Y | C | site_ok |
| 92 | CAPACITY_CHECKED | 7389 | X | X |  |  |  |  |  |  |  |  | Y | C | site_ok |
| 93 | ASSIGNED | 7392 | X | X |  |  |  |  |  |  |  |  | Y | C | 24 stages: all except lost |
| 94 | PO_ACCEPTED | 7402 |  |  |  |  |  |  | X |  |  |  | Y | C | material_order |
| 95 | PO_DECLINED | 7407 |  |  |  |  |  |  | X |  |  |  | Y | C | material_order |
| 96 | DISPATCHED | 7411 |  |  |  |  |  |  | X |  |  |  | Y | C | material_order |
| 97 | LOT_DISPATCHED | 7424 |  |  |  |  |  |  | X |  |  |  | Y | C | arrived, installing |
| 98 | LOT_ARRIVED | 7438 |  |  |  |  |  |  | X |  | X |  | Y | C | arrived, installing |
| 99 | ARRIVED | 7449 |  |  |  |  |  |  | X |  | X |  | Y | C | dispatched |
| 100 | INCIDENT_RAISED | 7457 | X | X |  | X | X | X | X | X | X |  | Y | GC | any |
| 101 | INCIDENT_RESOLVED | 7466 | X | X |  |  |  |  |  |  |  |  | Y | C | any |
| 102 | INCIDENT_REPORTED | 7476 | X | X |  |  |  |  |  |  |  |  | Y | C | any |
| 103 | REP_SET | 7485 | X | X |  | X |  |  |  |  |  | X | Y | C | won, agreement, signed, site_prep, qc_site, site_ok, material_order, dispatched, arrived |
| 104 | MATERIAL_HANDED | 7495 |  |  |  |  |  |  |  |  | X |  | Y | C | arrived |
| 105 | SERIAL_RESOLVED | 7512 | X | X |  |  |  |  |  |  |  |  | Y | C | any |
| 106 | MATERIAL_RECEIPT_SIGNED | 7515 |  |  |  |  |  |  |  |  |  | X | Y | C | arrived |
| 107 | SOP_STEP_DONE | 7524 |  |  |  |  |  |  |  |  | X |  | Y | C | installing |
| 108 | CLAIM_RAISED | 7549 |  |  |  |  |  |  |  |  | X |  | Y | C | arrived, installing, install_fix |
| 109 | CLAIM_DECIDED | 7561 | X | X |  |  |  |  |  |  |  |  | Y | C | any |
| 110 | CLAIM_SENT | 7568 |  |  |  |  |  |  | X |  |  |  | Y | C | any |
| 111 | CLAIM_RECEIVED | 7576 |  |  |  |  |  |  |  |  | X |  | Y | C | any |
| 112 | PEER_CHECKED | 7585 |  |  |  |  |  | X |  |  | X |  | Y | C | installing |
| 113 | SURPRISE_SET | 7594 | X | X |  |  |  |  |  |  |  |  | Y | C | arrived, installing, install_fix |
| 114 | SURPRISE_DONE | 7595 |  |  |  |  |  | X |  |  |  |  | Y | C | any |
| 115 | VARIATION_PROPOSED | 7604 | X | X |  | X |  |  |  |  |  |  | Y | C | any |
| 116 | VARIATION_SIGNED | 7606 |  |  |  | X |  |  |  |  |  | X | Y | C | any |
| 117 | VARIATION_DECLINED | 7610 |  |  |  | X |  |  |  |  |  | X | Y | C | any |
| 118 | SUPPLIER_INVOICE | 7613 |  |  |  |  |  |  | X |  |  |  | Y | GC | any |
| 119 | SUP_INVOICE_DECIDED | 7624 | X | X |  |  |  |  |  |  |  |  | Y | C | any |
| 120 | SUP_INVOICE_PAID | 7634 |  |  |  |  | X |  |  |  |  |  | Y | C | any |
| 121 | SITE_CHECKED_IN | 7642 |  |  |  |  |  |  |  |  | X |  | Y | C | arrived, installing, install_fix |
| 122 | CHECKIN_REVIEWED | 7652 | X | X |  |  |  |  |  |  |  |  | Y | C | any |
| 123 | DAY_ENDED_TECH | 7659 |  |  |  |  |  |  |  |  | X |  | Y | C | any |
| 124 | SAFETY_CHECKED | 7668 |  |  |  |  |  |  |  |  | X |  | Y | C | installing, install_fix |
| 125 | SAFETY_REVIEWED | 7679 | X | X |  |  |  | X |  | X |  |  | Y | C | any |
| 126 | SOS_RAISED | 7689 | X | X | X | X | X | X | X | X | X |  | N | C | — (not project-scoped) |
| 127 | ACCESS_LOGGED | 7691 | X | X | X | X | X | X | X | X | X | X | N | C | — (not project-scoped) |
| 128 | ACCESS_REVIEWED | 7692 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 129 | SOS_CLOSED | 7693 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 130 | BLOCKER_RAISED | 7694 | X | X |  | X |  | X | X |  | X |  | Y | C | 23 stages: all except amc, lost |
| 131 | BLOCKER_RESOLVED | 7698 | X | X |  | X |  | X | X |  | X |  | Y | C | any |
| 132 | INSPECTION_REQUESTED | 7706 |  |  |  |  |  |  |  |  | X |  | Y | C | installing, install_fix |
| 133 | QC_FINAL_RESULT | 7715 |  |  |  |  |  | X |  |  |  |  | Y | C | qc_final |
| 134 | SAFETY_COUNTERSIGNED | 7738 |  |  |  |  |  |  |  |  | X | X | Y | C | any |
| 135 | QC_RECHECK_DONE | 7745 |  |  |  |  |  | X |  |  |  |  | Y | C | any |
| 136 | QC_RECHECK_REVIEWED | 7747 | X | X |  |  |  |  |  |  |  |  | Y | C | any |
| 137 | HANDOVER_SIGNED | 7748 |  |  |  |  |  | X |  |  | X | X | Y | C | handover |
| 138 | HANDOVER_COMPLETED | 7764 | X | X |  |  |  | X |  |  |  |  | Y | C | handover |
| 139 | DRAWING_ADDED | 7767 | X | X |  |  |  |  |  |  |  |  | Y | GC | any |
| 140 | STATUTORY_SET | 7777 | X | X |  | X | X | X |  |  |  |  | Y | GC | any |
| 141 | HOLD_REQUESTED | 7806 | X | X |  | X |  |  | X | X | X |  | Y | GC | any |
| 142 | HOLD_DECIDED | 7817 | X | X |  |  |  |  |  |  |  |  | Y | C | any |
| 143 | HOLD_ENDED | 7824 | X | X |  |  |  |  |  |  |  |  | Y | C | any |
| 144 | STAGE_BACK | 7826 | X | X |  |  |  |  |  |  |  |  | Y | GC | any |
| 145 | DISPUTE_SET | 7831 | X | X |  | X |  |  |  |  |  |  | Y | C | any |
| 146 | DISPUTE_CLEARED | 7832 | X | X |  |  |  |  |  |  |  |  | Y | C | any |
| 147 | CHAT_POSTED | 7833 | X | X | X | X | X | X | X | X | X | X | Y | C | any |
| 148 | CHAT_STATUS | 7850 | X | X | X | X | X | X | X | X | X | X | Y | C | any |
| 149 | CHAT_DECISION | 7865 | X | X |  |  |  |  |  |  |  |  | Y | C | any |
| 150 | TEMPLATE_SET | 7875 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 151 | TEMPLATE_DELETED | 7876 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 152 | MSG_LOGGED | 7877 | X | X |  | X | X |  |  |  |  |  | Y | C | any |
| 153 | MSG_STATUS | 7881 | X | X |  | X | X |  |  |  |  |  | Y | C | any |
| 154 | SERVICE_DONE | 7883 |  |  |  |  |  |  |  |  | X |  | Y | C | amc |
| 155 | TICKET_OPENED | 7896 | X | X |  | X |  |  |  |  | X | X | Y | GC | any |
| 156 | TICKET_SEVERITY | 7909 | X | X |  | X |  | X |  |  | X |  | Y | GC | any |
| 157 | LIFT_QR_SET | 7919 | X | X |  |  |  |  |  |  |  |  | Y | C | amc |
| 158 | TICKET_ASSIGNED | 7920 | X | X |  | X |  | X |  |  | X |  | Y | GC | any |
| 159 | TICKET_ACCEPTED | 7933 | X | X |  | X |  | X |  |  | X |  | Y(open) | C | any |
| 160 | TICKET_UPDATED | 7940 | X | X |  | X |  | X |  |  | X |  | Y(open) | C | any |
| 161 | TICKET_CLOSED | 7950 | X | X |  |  |  |  |  |  |  | X | Y | C | any |
| 162 | WARRANTY_CALLED | 7959 | X | X |  | X |  |  |  |  |  |  | Y | C | amc |
| 163 | LIFT_OWNER_CHANGED | 7961 | X | X |  |  |  |  |  |  |  |  | Y | C | amc |
| 164 | AMC_ENDED | 7964 | X | X |  | X |  |  |  |  |  |  | Y | C | amc |
| 165 | AMC_RENEWED | 7965 | X | X |  | X |  |  |  |  |  |  | Y | C | amc |
| 166 | ESC_ACTED | 7970 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 167 | JOB_POSTED | 7979 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 168 | JOB_APPLIED | 7998 |  |  |  |  |  | X |  | X |  |  | N | C | — (not project-scoped) |
| 169 | JOB_WITHDRAWN | 8009 |  |  |  |  |  | X |  | X |  |  | N | C | — (not project-scoped) |
| 170 | JOB_CONFIRMED | 8010 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 171 | JOB_CLOSED | 8020 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 172 | CREW_ADDED | 8022 | X | X |  |  |  |  |  | X | X |  | Y | GC | any |
| 173 | CREW_REMOVED | 8035 | X | X |  |  |  |  |  | X | X |  | Y | GC | any |
| 174 | CUSTOMER_RATED | 8037 |  |  |  | X |  |  |  |  |  | X | Y | GC | any |
| 175 | CREW_RATED | 8049 |  |  |  |  |  |  |  |  | X |  | Y | GC | any |
| 176 | PROMOTED | 8058 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 177 | DEMOTED | 8069 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 178 | SKILL_CHECKED | 8077 | X |  |  |  |  | X |  |  | X |  | N | C | — (not project-scoped) |
| 179 | STRIKE_APPEALED | 8087 | X | X | X | X | X | X | X | X | X |  | N | C | — (not project-scoped) |
| 180 | STRIKE_DECIDED | 8094 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 181 | SPOT_CHECKED | 8102 | X | X |  |  |  | X |  |  |  |  | Y | C | any |
| 182 | REVIEW_HELD | 8111 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 183 | APPROVAL_REQUESTED | 8113 | X | X | X | X | X | X | X | X | X |  | N | C | — (not project-scoped) |
| 184 | APPROVAL_DECIDED | 8120 | X | X |  | X | X | X |  |  |  |  | N | GC | — (not project-scoped) |
| 185 | BACKUP_SET | 8133 | X |  |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 186 | BACKUP_ENDED | 8141 | X |  |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 187 | DAILY_REPORT | 8143 |  |  |  |  |  |  |  |  | X |  | Y | C | arrived, installing, install_fix |
| 188 | DATA_EXPORTED | 8150 | X |  |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 189 | UNDONE | 8151 | X | X | X | X | X | X | X | X | X | X | N | C | — (not project-scoped) |
| 190 | DAY_CLOSED | 8153 | X | X |  | X | X | X |  |  |  |  | N | GC | — (not project-scoped) |
| 191 | CLOCK_REVIEWED | 8164 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 192 | TRIAL_RESULT | 8166 | X | X |  |  |  | X |  |  | X |  | Y | C | any |
| 193 | PRIVACY_ACCEPTED | 8176 | X | X | X | X | X | X | X | X | X | X | N | C | — (not project-scoped) |
| 194 | STAT_LEAD_SET | 8178 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 195 | SERVICE_LEAD_SET | 8179 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 196 | BUDDY_SET | 8180 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 197 | COMPANY_SHARE_SET | 8182 | X | X |  |  |  |  |  | X |  |  | N | C | — (not project-scoped) |
| 198 | PARTNER_STATUS_SET | 8186 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 199 | PARTNER_DD_SET | 8191 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 200 | WORKER_PROFILE | 8196 | X | X | X | X | X | X | X | X | X |  | N | C | — (not project-scoped) |
| 201 | DOC_VERIFIED | 8205 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 202 | LEGAL_SET | 8211 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 203 | TRAINING_PASSED | 8219 | X | X | X | X | X | X | X | X | X |  | N | C | — (not project-scoped) |
| 204 | PAY_APPROVED | 8228 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 205 | RESERVE_SETTLED | 8239 |  |  |  |  | X |  |  |  |  |  | N | C | — (not project-scoped) |
| 206 | CONTRACT_ACCEPTED | 8243 |  |  | X |  |  | X | X | X | X |  | N | C | — (not project-scoped) |
| 207 | TRAINING_VIDEO_SET | 8244 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 208 | RATING_APPEALED | 8245 |  |  | X | X |  | X |  | X | X |  | N | C | — (not project-scoped) |
| 209 | RATING_APPEAL_DECIDED | 8250 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |
| 210 | PAY_PAID | 8254 |  |  |  |  | X |  |  |  |  |  | N | C | — (not project-scoped) |
| 211 | PAY_DISPUTED | 8262 |  |  | X |  |  | X |  | X | X |  | N | C | — (not project-scoped) |
| 212 | PAY_DISPUTE_ANSWERED | 8270 | X | X |  |  |  |  |  |  |  |  | N | C | — (not project-scoped) |

---

## 4. Lead-to-handover stage transitions (from `apply()`, H:8347–8866)

Every stage change goes through `setStage(P, s, ev)` (H:8336–8339), which appends `{s, t, by}` to `P.hist`. The table lists every `setStage` call site in `apply()`. "Rule" gives allowed roles and stages from `RULES` (§3.4).

| From stage(s) | Event | Condition in `apply()` | To stage | apply() line | Rule (roles · stages) |
|---|---|---|---|---|---|
| (none) | `LEAD_CREATED` | `newProject()` sets `stage:'new'` | `new` | 8321–8323 | field, sales, admin, owner, customer · project-scoped, no stage list |
| `new` | `LEAD_ACCEPTED` | sets `P.sales` | `accepted` | 8444 | sales, admin · `new` |
| `new` | `LEAD_RETURNED` | records reason | `returned` | 8445 | sales, admin · `new` |
| `returned` | `LEAD_RESUBMITTED` | appends note | `new` | 8446 | field, sales, admin, owner · `returned` |
| `accepted` | `CONTACT_LOGGED` | `outcome === 'interested'` and stage `accepted` | `contacted` | 8458 | sales · `accepted, contacted, requirements, quote_ready, quoted` |
| any SALES_TALK stage | `CONTACT_LOGGED` | `outcome === 'not_interested'` | `lost` | 8461 | (as above) |
| `contacted` | `REQ_SAVED` | only if stage is `contacted` (also rebuilds `P.materials`) | `requirements` | 8464 | sales · `contacted, requirements, quoted` |
| `requirements`, `quoted` | `QUOTE_CREATED` | `q.needsApproval` (discount over sales limit / admin limit / margin below floor, `calcQuote` H:6082–6103) | `quote_approval` else `quote_ready` | 8472 | sales · `requirements, quoted` |
| `quote_approval` | `QUOTE_DECISION` | approve / reject | `quote_ready` / `requirements` | 8479 | owner, admin · `quote_approval` (below-margin approval always needs a second approver, H:6723) |
| `quote_ready` | `QUOTE_SENT` | — | `quoted` | 8487 | sales · `quote_ready` |
| `quoted` | `DEAL_WON` | builds payment schedule (`paySchedule` H:6105) | `won` | 8488 | sales · `quoted` |
| marketplace job | `MK_ANSWER` | partner accepts (`d.yes`) | `won` | 8407 | partner · gate |
| marketplace job | `MK_DONE` | partner completes; AMC dates, lift QR code | `amc` | 8417–8419 | partner · gate |
| PRE_WON stages | `DEAL_LOST` | — | `lost` | 8489 | sales, admin, owner · 8 PRE_WON stages |
| any | `PROJECT_CANCELLED` | closes tickets and job posts | `lost` | 8490–8494 | owner · gate |
| `lost` | `DEAL_REOPENED` | `P.sales ? 'accepted' : 'new'` | `accepted` / `new` | 8496 | admin, owner · `lost` (always two-person when dual control on, H:6729) |
| `won` | `AGREEMENT_PREPARED` | — | `agreement` | 8497 | sales · `won` |
| `agreement` | `AGREEMENT_SIGNED` | signature evidence | `signed` | 8498 | customer, sales · `agreement` |
| `signed` | `PAYMENT_STATUS` | `status==='confirmed'` and purpose `token` or `rails` | `site_prep` | 8502–8507 | finance · project-scoped, any stage |
| `site_prep`, `site_fix` | `QC_REQUESTED` | — | `qc_site` | 8514 | customer, sales · `site_prep, site_fix` |
| `qc_site` | `QC_SITE_RESULT` | `approved` / `corrections` (`not_feasible` stays in `qc_site` and sets `P.infeasible`) | `site_ok` / `site_fix` | 8515–8526 | qc · `qc_site` |
| `qc_site` | `INFEASIBLE_DECIDED` | `decision==='redesign'` (variation needed) | `site_ok` | 8533–8536 | admin, owner · `qc_site` + gate |
| `site_ok` | `RELEASED` | sets supplier, tech, partner (`companyOf(tech)`), drawings, PO | `material_order` | 8539–8541 | admin, owner · `site_ok` |
| `material_order` | `DISPATCHED` | seal, vehicle, serials, challan; auto-accepts PO | `dispatched` | 8548–8550 | supplier · `material_order` |
| `dispatched` | `ARRIVED` | seal match check; damage creates incident | `arrived` | 8565–8571 | tech, supplier · `dispatched` |
| `arrived` | `MATERIAL_HANDED` | if not `custApp` (customer signs on tech phone) | `installing` | 8627–8634 | tech · `arrived` |
| `arrived` | `MATERIAL_RECEIPT_SIGNED` | customer signs in own app | `installing` | 8638 | customer · `arrived` |
| `installing`, `install_fix` | `INSPECTION_REQUESTED` | — | `qc_final` | 8649 | tech · `installing, install_fix` |
| `qc_final` | `QC_FINAL_RESULT` | `pass` / `fail` (random re-check sampling `qcRecheckPct`, H:8654) | `final_pay` / `install_fix` | 8653–8661 | qc · `qc_final` |
| `final_pay` | `PAYMENT_STATUS` | `confirmed` and purpose `final` | `handover` | 8508 | finance |
| `handover` | `HANDOVER_COMPLETED` | all signature slots + final payment confirmed (rule H:7764); creates AMC visit plan, warranty months, lift QR code | `amc` | 8665–8671 | admin, owner, qc · `handover` |
| `site_ok`, `material_order`, `dispatched`, `qc_final` | `STAGE_BACK` | `d.to` must equal `STAGE_BACK[P.stage]` (H:6007, rule H:7826–7829); clears dispatch when leaving `dispatched` | `qc_site`, `site_ok`, `material_order`, `installing` | 8610–8613 | owner, admin · gate |

Observations:
* Stages `site_prep → qc_site` require a confirmed token payment first (8507); the flow cannot reach site checks without Finance confirmation. Payment plan 1 ("by material lot") uses purposes `rails/doors/drive` and lot events `LOT_DISPATCHED/LOT_ARRIVED` that do not change the main stage (`payOpen` H:6136–6142).
* The stage machine is linear with four explicit "back" edges and two terminal states (`amc`, `lost`); `lost` is re-openable.
* `HOLD_REQUESTED/HOLD_DECIDED/HOLD_ENDED` (8605–8609) and `DISPUTE_SET` add overlays without changing stage.

---

## 5. Functional audit of business-logic modules

| Module | Status | Evidence |
|---|---|---|
| Lead capture | **VERIFIED** | `WIZ.lead` (H:10276 ff.) steps: safety check, location (auto GPS via `Geo.get`), address (GPS reverse lookup via Nominatim, H:16041), contact, consent (`call`/`whatsapp`), shaft/floors; `LEAD_CREATED` rule H:6986; job number `AIEC-<city>-<year>-<5 digits>` (`newJobNo` H:8314); `practice-roles` and lead-to-handover tests exercise it (§8). Customer self-lead (`WIZ.custLead`) and enquiry intake (`ENQUIRY_ADDED` H:6894) exist. |
| Duplicate detection | **PARTIALLY IMPLEMENTED** | `findDuplicates(ctx)` H:10270–10275: match on same 10-digit phone (main or extra contacts) **or** GPS within 80 m (`haversine`), excluding different tower names and `lost` jobs. User chooses "attach" (→ `LEAD_INFO_ADDED`) or "new" (stored in `lead.dupOf`, warning shown H:10430). No name/address fuzzy matching; duplicates are advisory only; runs only against data on the capturing phone (a field phone sees only its own leads per `canSee`, H:6178), so cross-phone duplicates are detected only after sync at the office (UNVERIFIED whether the office gets a duplicate task). |
| Survey & site readiness | **VERIFIED (code) / PARTIALLY (tests)** | `SURVEY_DONE` rule H:7148 (survey details from `SURVEY_FROM` H:5904); `PREP_ITEMS` shaft/pit/power/clean/light/barricade (H:5895), `PREP_ITEM_DONE` H:7256; QC site visit `QC_SITE_RESULT` with measurements, checks, corrections, not-feasible branch (H:8515–8536); tolerances `tolWidth/Depth/Pit/Head` in `CFG_LIMITS` (H:5872). `survey.test.js`, `sitequality.test.js` exist (not all run in this audit). |
| Customer / builder records | **PARTIALLY IMPLEMENTED** | Project-embedded contact (`P.contact`, `P.contacts`, `P.extra`, `CONTACT_ROLES` owner/builder/manager/watchman H:5893); `REP_SET` representative H:7485; `CUSTOMER_LINKED` H:6977; housing society/builder groups `SOC_SET/SOC_APPROVED/SOC_VOTED`, `SITE_GROUP_SET`; customer user accounts per project (`USER_ADDED` role customer, H:6820–6823). There is no standalone customer master/CRM entity: customers exist only inside projects. |
| Quotation | **VERIFIED** | `calcQuote(cfg, req, discountPct)` H:6082–6103: base (auto/manual) + per-stop + capacity + finish + install + transport + dismantle; cost-plus with `targetMargin`; discount; GST; margin. `QUOTE_CREATED` H:7159, `QUOTE_SENT`, `COUNTER_OFFER` H:7207, `OPTION_CHOSEN` H:7184; quotation validity `validityDays`. |
| Discount approvals | **VERIFIED** | `why`: `over_limit` (> `salesDiscountLimit`), `over_admin` (> `adminDiscountLimit`), `below_floor` (margin < `minMargin`) → `needsApproval` → stage `quote_approval`; `QUOTE_DECISION` owner/admin; below-floor approval always requires a second Owner/Admin (`needsSecond` H:6723); `discount.test.js` exists. |
| Agreements | **PARTIALLY IMPLEMENTED** | `AGREEMENT_PREPARED`/`AGREEMENT_SIGNED` (H:8497–8498, rule H:7217) with on-screen signature capture and in-person flag; agreement text is a fixed template (README:522 "must be approved by a qualified lift engineer… and a lawyer"); no e-sign provider, no PDF hash anchoring beyond the event signature. GAD (layout drawing) signature `GAD_SIGNED` H:7328. Worker/partner contract `CONTRACT_ACCEPTED` versioned by `agreeVersion`. |
| Payment milestones | **PARTIALLY IMPLEMENTED** | Schedules (`paySchedule` H:6105–6120): plan 2 token + arrival % + cabin % + retention + final; plan 1 rails 30/doors 40/drive 20/final (H:6107–6109); `PAY_STAGE` (H:5886) gates when each part can be paid; `PAYMENT_RECORDED` (sales/finance/customer, H:7226) → `PAYMENT_STATUS` confirmed/rejected/reversed by Finance (H:7244; reversal needs second approver under dual control). Money moves outside the app: no payment gateway or bank API ("Not connected: payment gateway…", README:520); UPI/bank details are config text (`TEXT_CFG` H:5856). Bank statement CSV matching is suggestion-only (`SCREENS.recon` H:11430, README:498). Refunds `REFUND_PROPOSED/DECIDED/PAID`. |
| Material procurement | **VERIFIED (code)** | `materialList(req)` H:6147–6153 (fixed BOM by stops); `RELEASED` creates PO (`poNo`, `needBy` from `poDays`, H:8539–8541); `PO_ACCEPTED/PO_DECLINED` (supplier, H:7402); `DISPATCHED` with seal/serials/challan (H:8548); serial-tracked items `SERIAL_ITEMS` (H:5888); supplier comparison `SUP_COMPARED`; supplier invoices `SUPPLIER_INVOICE/SUP_INVOICE_DECIDED/SUP_INVOICE_PAID` (H:7613). |
| Material receipt | **VERIFIED (code)** | `ARRIVED` seal match + condition/damage incident (H:8565–8571); `MATERIAL_HANDED` with customer + tech signatures and serial check → `serialHold` (H:8627–8634); `MATERIAL_RECEIPT_SIGNED` (customer app) (H:8638); part claims `CLAIM_RAISED/DECIDED/SENT/RECEIVED` (`CLAIM_KINDS` H:5889); stock ledger `STOCK_ITEM_SET`, `STOCK_MOVED` (H:7071, 8696–8697). |
| Scheduling | **PARTIALLY IMPLEMENTED** | No calendar or crew-capacity planner was found. What exists: PO need-by date and supplier dispatch date (H:8540, 8546); sales follow-ups `FOLLOWUP_SET`; chat appointments (`kind 'appt'` with date/time/place, H:7844, 8677) and their tasks (H:9051); installation target days `installDays` with late flags (H:9966, 12907); `maxActiveJobs` limit per technician; AMC visit dates generated at handover (H:8666–8667); `CAPACITY_CHECKED`. |
| Licensed partner company assignment | **VERIFIED (code)** | Role `partner`; `PARTNER_PROFILE` licence fields (H:6844); `PARTNER_STATUS_SET`, `PARTNER_DD_SET` (owner/admin, H:8186); technicians belong to a company (`companyOf`, `USER_ADDED` partner adds own trainee/helper techs, H:6813); `P.partner = companyOf(d.tech)` at release (H:8539); job marketplace `JOB_POSTED/JOB_APPLIED/JOB_CONFIRMED` (H:7979, 8010) and partner marketplace `RFQ_SENT/PQUOTE_SENT/MK_PICKED/MK_ANSWER/MK_DONE` (H:8400–8419); `licenceGate` config. Licence verification is manual (`DOC_VERIFIED`); no external registry check. |
| Technician assignment | **VERIFIED (code)** | `ASSIGNED` (admin/owner, H:7392–7400): active check, `techWhyNot` (training/contract/strikes), `canLead` grade check, QC cannot be on own job; crew `CREW_ADDED/CREW_REMOVED`; `TEAM_SET` H:6863; career ladder `PROMOTED/DEMOTED/SKILL_CHECKED`. |
| Installation evidence | **VERIFIED (code)** | 12-step SOP `SOP` (H:5986) with readings `SOP_READ` (H:5932); `SOP_STEP_DONE` tech, `installing` (H:7524); photos/videos/voice captured in-app with SHA-256 hash, GPS, device info (H:9716, 9786); daily safety `SAFETY_CHECKED` (H:7668) + countersign; site check-in with geofence `SITE_CHECKED_IN` (H:7642, `geoRadius`); peer check, surprise QC; SOS `SOS_RAISED` (H:7689). Photos are checked by people, not automatically (README:500). |
| QC & rework | **VERIFIED** | `QC_FINAL_RESULT` (H:7715–7735): not own job/own company, ≥2 photos, all checks and critical items answered with critical photos, numeric test values (from `LIFE_FROM`), fail requires items; fail → `install_fix` with correction items (H:8655–8661); `CORRECTION_DONE`; random second-QC re-check `QC_RECHECK_DONE/REVIEWED`; 42-point inspector checklist `ISC` (H:5897–5899). |
| Inspection & handover | **VERIFIED (code)** | `STATUTORY_SET` (erection permit, Lift Inspector visit, licence; H:7777) with `licenceGate`/`permitGate`; `HANDOVER_SIGNED` slots customer/tech/qc with consent and signature (H:7748–7762); `HANDOVER_COMPLETED` requires all slots + confirmed final payment (H:7764); generates lift QR (`liftCode`, H:8670). The app does not issue statutory licences (README:522). |
| Warranty & AMC | **PARTIALLY IMPLEMENTED** | Warranty end = `warrantyMonths*30` days from handover (`warrantyEnd` H:6012); AMC visit schedule `amcVisits` per year (H:8666–8667); `SERVICE_DONE` (H:7883), `AMC_RENEWED` (H:7965), `AMC_ENDED` (H:7964), `WARRANTY_CALLED` (H:7959); AMC pricing config `amcPrice/amcCompPrice`. No AMC invoicing/collection automation beyond payment records. |
| Service tickets | **VERIFIED (code)** | `TICKET_OPENED` (H:7896), `TICKET_SEVERITY`, `TICKET_ASSIGNED`, `TICKET_ACCEPTED`, `TICKET_UPDATED`, `TICKET_CLOSED` (H:7950); SLA hours by severity `slaUnsafe/slaBreakdown/slaRepeat/slaRoutine` (H:5873, 6065); service lead routing (`svcRoles` H:6159); resident QR report endpoint `POST /report` (server core.mjs:144–153, client H:16531). `tickets.test.js`, `service.test.js` exist. |
| Contractor ledger & payouts | **PARTIALLY IMPLEMENTED** | Earnings by week `weeksFor/payLines` (H:6681–6716); `ledger(uid)` pending/verified/due/paid/disputed/reserve (H:6690–6696); `PAY_APPROVED` (owner/admin, H:8228; non-zero adjustment needs second approval), `PAY_PAID` (finance, H:8254; payer ≠ approver), `PAY_DISPUTED/PAY_DISPUTE_ANSWERED`, reserve `RESERVE_SETTLED` (H:8239). Pay rates are templates (README:525); actual bank transfer is outside the app. |
| Commission | **PARTIALLY IMPLEMENTED** | Marketplace commission entries `S.comm` with `COMM_STATUS` (H:6964) and `COMM_CREDIT` credit notes (H:6969, 8421); `SCREENS.commLedger` (H:13220); config `commRate`, `dupCommission`, `leadWonPct`, `salesWonPct`, `refReward` (`CFG_LIMITS` H:5863–5870). Sales/field incentive computation beyond these config percentages was not traced (UNVERIFIED). |
| Notifications & escalation | **PARTIALLY IMPLEMENTED** | Task engine `needs(P)`/`needs0` H:8936–9111 (132 distinct task keys `add('…')`); `Notify` H:15361 ff. (in-app tones via `AudioContext`, vibration, OS notifications, quiet hours); escalation ladder `escLevel` H:6597–6600 using `remindDays/escalateDays/escOwnerDays` (defaults 1/3/7, H:6038); `ESC_ACTED` (H:7970); Owner/Admin task split `splitOA` (H:8920). Delivery when the app is closed depends on Web Push via the company server or Chrome periodic sync (README:519, sw.js:24–61); there is no SMS/WhatsApp/email sending (wa.me links only). |
| Audit history & reporting | **VERIFIED (code)** | Every change is a signed (real mode) immutable event; per-project `hist`, `log`; `SCREENS.audit` (H:14417), `problems`, `access` (outsider access log `ACCESS_LOGGED`), `clock` (phone clock gaps, H:6735–6753); KPIs `SCREENS.kpi` (H:13066), `weekly` (H:15194), `DAILY_REPORT` (H:8143), CSV export `DATA_EXPORTED` (owner only, PIN re-check; kinds projects/payments/tickets/tally, H:8150). Audit history is local to each phone and complete only on office phones after sync. |

Cross-cutting notes:
* Authorisation is enforced only on the client (`validate()` on every phone and on every imported file); see §9 F-01.
* "SIMULATED" items: practice mode (`seedPractice` H:16784, banner `practice_banner`) uses fake data; signatures are not applied in practice mode (H:5410). WebAuthn unlock is a local gate only (H:9382–9393).
* MISSING (stated by README:503, 520–524): payment gateway, EMI/bank partner, WhatsApp Business API delivery receipts, GPS trackers/sensors/CCTV/digital locks, masked calling, screenshot blocking (`FLAG_SECURE` is not set in `MainActivity.kt`; verified by reading the file).

---

## 6. Screens inventory

Routing: `SCREENS` object (H:9299); navigation via `go(name, p)` (H:9438), `back()` (H:9446), `data-a="nav" data-v="<screen>"` buttons; `render()` (H:9533) draws `SCREENS[route.name] || SCREENS.home`. Bottom navigation per role in `NAV` (H:9890–9898). Step-by-step task forms are 170 `WIZ.<name>` wizards rendered by `SCREENS.wiz` (H:10185); button actions are dispatched through `ACT` (H:16170–16726, 272 handlers).

**99 screens** are defined (`grep -c '^SCREENS\.'`). Role guards below are the first `includes(u.role)` / `role ===` checks found in each screen body; screens without a guard show role-dependent content.

| Screen (H: line) | Who (guard) | Purpose |
|---|---|---|
| `boot` 12038, `splash` 12043, `gate` 12050, `door` 12065 | all / public | Start-up, front door (choose person, customer vs staff) |
| `welcome` 12375, `lang` 12382, `intro` 12390, `install` 12025 | public | First run, language (mr/hi/en), intro, install-app gate |
| `setup` 12400, `newOrg` 12415, `join` 12423 | public | Create company or join one |
| `login` 12429, `pin` 12441, `pinRecover` 12455 | all; pinRecover Owner only | Choose user, PIN pad, Owner recovery code |
| `codeLogin` 12078, `joinReq` 12092, `myReq` 12178, `reqSent` 12261 | public (supplier/partner join) | One-time login code; join request to office |
| `enquiry` 12250, `priceGuess` 12233 | public | Public enquiry, price estimate |
| `privacy` 12468, `perms` 12470 | all | Privacy notice, permissions explanation |
| `home` 12578 | all (customer → `customerHome`) | Role task list ("my tasks"), critical bar |
| `projects` 13325 | staff | Job list (filtered by `canSee`) |
| `project` 13343 | all (customer variant) | One job record: phase bar, needs, history |
| `wiz` 10185 | per wizard rule | Step-by-step task forms |
| `cl` 13596 | tech/QC | Checklists (site prep, corrections, SOP, handover) |
| `done` 13639 | all | Success screen after a task |
| `ev` 13850 | all | Evidence (photo/video/voice) detail |
| `chat` 13837, `group` 14009 | all | Per-job chat, groups |
| `map` 13730 | owner, admin | Map of sites (Leaflet online, SVG offline) |
| `me` 14158 | all | Profile, language, logout, biometrics, server status |
| `users` 14238, `workers` 14979, `worker` 15012 | owner, admin (partner for own techs) | People management |
| `joins` 14320, `joinCheck` 14330, `joinlink` 12343 | owner, admin | Join requests, invite links |
| `server` 14397, `restore` 14289, `data` 14428 | owner, admin | Company server setup, restore, file sharing |
| `rules` 14406, `perm` 14224 | owner / owner, admin | Business rule config, permission table |
| `audit` 14417, `problems` 14425, `access` 14511, `clock` 15338 | owner, admin | Audit log, rejected records, outsider access log, clock gaps |
| `kpi` 13066, `weekly` 15194, `reports` 14389, `drill` 13319 | owner/admin (weekly: owner; reports: +sales) | Key numbers, weekly summary, resident reports |
| `finList` 13102, `recon` 11430, `ageing` 11117, `pay` 12965, `earn` 15853, `commLedger` 13220 | finance, owner, admin | Payments, bank match, receivables ageing, worker pay, commission |
| `supbills` 11388 | supplier | Supplier invoices |
| `stock` 11074 | owner, admin, finance, tech | Store/van/site stock |
| `drawings` 11658, `docs` 13589, `doc` 14143, `receipt` 11596 | various | Drawings (watermarked), documents, printable docs, receipts |
| `stattracker` 11684 | owner, admin, qc, statutory lead | Permits/inspector/licence tracker |
| `lifts` 11065, `liftqr` 14791, `qr` 14809 | owner/admin/sales/qc; `qr` public | Handed-over lifts, QR stickers, resident lift help page |
| `logbook` 11748, `safetylog` 11891, `faults` 11127, `risk` 10854 | various; risk owner/admin | Lift logbook, daily safety log, offline fault guide, risk view |
| `escalations` 15134, `auto` 15246, `exceptions` 15289, `waits` 12525 | owner, admin | Escalation ladder, automatic actions, exceptions, waiting items |
| `ladder` 15520, `reviews` 15548, `training` 15038, `leaders` 15941 | owner/admin/qc; all | Career ladder, review meetings, training, leaderboard |
| `jobs` 15765, `posts` 15838, `mktCompare` 13201 | partner, qc; owner/admin; partner | Job marketplace |
| `contract` 15880 | owner, admin (+ self) | Worker/partner contract |
| `templates` 14707 | owner, admin | WhatsApp/message templates |
| `notify` 15479 | all | Notification settings |
| `steps` 15986, `damage` 15991, `mywork` 15996 | tech | Technician tabs: install steps, damage reports, pay |
| `mypay` 16008 | customer | Customer payments |
| `callnext` 16014 | sales | Next call list |
| `enquiries` 12366, `paste` 12351 | owner, admin, sales (+field) | Enquiry inbox, paste lead |
| `shareApp` 12311, `sharePoster` 12337, `closed` 12336, `helper` 16019, `about` 14234, `report` 14207 | various | Sharing, posters, closed company, helper, about, report |

Bottom navigation (`NAV`, H:9890–9898): field: home/projects/chat/me; sales: home/projects/callnext/chat/me; tech: home/stock/steps/damage/mywork/me; finance & default: home/projects/chat/me; supplier: home/projects(orders)/chat/me; customer: home(my lift)/mypay/docs/chat(help)/me.


---

## 7. Tests run in this audit

Environment: Node v22.23.5; Playwright **1.63.0** resolved globally from `/opt/node-tools/node_modules/playwright` (the repository pins 1.56.1 in `package.json`; `npm ci` was **not** run and `playwright install` was **not** run). Chromium from `/opt/pw-browsers` (revision 1243) launched without needing `executablePath`. To keep the source clone untouched (the test helper creates `tests/out/`, `tests/lib.js:7`), the repository working tree (without `.git`) was copied to the scratch directory `/tmp/claude-0/-home-user-REVERSE-FRESH-FRONTEND-/5911fe7d-91be-5c37-8e7d-d08bf6639427/scratchpad/repo-copy` and the commands were run there.

| Command | Scope | Result | Duration |
|---|---|---|---|
| `node tests/static.test.js` (= `npm run check`) | 8 static checks | **8 ok, 0 failed** — "STATIC PASSED" (size 2.24 MB ≤ 2.4 MB budget; APP_BUILD 105 = sw.js cache 105; script parses; 984 unique top-level names; 4,860 unique word keys; all words in en/hi/mr; placeholders match; 1,900 requested keys exist) | 0.12 s |
| `node tests/run-all.js --shard=1/4` | 28 of 112 test files: practice-roles, lead-to-handover.e2e, escalation, market, security2, statutory, material, extras, branches, recon, hq, server-push, server-request, waits, statlead, moneyline, golden, homeui, easeuse, growth, money, journeyB, journeyF, signinL3, hqname, mapzoom, bp1compliance, bp5market | **28 files passed, 0 failed, 0 skipped** ("ALL TESTS PASSED", exit 0); 618 `ok -` assertion lines printed, 0 `not ok` lines (some files report only a final PASSED line, so 618 is a lower bound of assertions) | 9 min 10 s |

Not run (UNVERIFIED in this audit): shards 2/4, 3/4, 4/4 (84 files), the `screens` CI job, `AIEC_SHIFT_DAYS=45` time-shifted run, `npm run shots`. By extrapolation from shard 1 the full suite needs roughly 35–40 minutes sequentially. CI configuration runs all four shards in parallel (`.github/workflows/test.yml`); CI results on GitHub were not inspected.

Notable checks that passed in shard 1: full real-mode lead-to-handover through the UI with 9 roles ("E2E PASSED"); forged/out-of-role import refusals (`security2`); golden event-log replay (`golden.test.js` against `tests/fixtures/golden-v1.json`); server push and join-request flows against `server/local.mjs`; payment plans/money lines; marketplace.

---

## 8. Security findings (original web app, server and Android wrapper)

Severity reflects risk for a production deployment holding customer personal data and money records.

| ID | Severity | Finding | Evidence |
|---|---|---|---|
| F-01 | **High** | **Authorisation is client-side only.** Every business rule (roles, stages, separation of duties, two-person approval) is evaluated by `validate()` on each phone. The server stores opaque AES-GCM blobs and checks only box-level access; it cannot evaluate rules. Any registered phone may post to the shared `inbox` box (core.mjs:234). A modified client can create any event; protection depends on office phones re-validating and checking ECDSA signatures on import (`Sign.check` H:5459 ff.), and on the first file a brand-new phone loads being trusted (README:513). Practice mode records are unsigned (H:5410). | H:8280–8308; core.mjs:1–6, 231–243; README:97, server/README.md:146 |
| F-02 | **High** | **Public APK signing key with password in source.** `android/aiec-test.jks` (PKCS12, alias `aiec-test`) and its password `aiectest` (store and key) are committed (`android/app/build.gradle.kts:22–31`; `android/README.md`). CI `assembleRelease` signs the distributed APK with this key whenever the `AIEC_KEYSTORE*` secrets are absent (`build.gradle.kts:47`, `android.yml:27–30`). Anyone can build a malicious APK with the same signature that Android will accept as an **update over the installed app** (inheriting its permissions; `allowBackup=false` limits but does not remove impact since the WebView data dir is reused). | files cited; keytool output §1.3 |
| F-03 | **High** | **Android WebView runs remote code from GitHub Pages with a native bridge.** `START_URL` is `https://prashantashwable-cmyk.github.io/UNLIT-GPT-HTML/AIEC-Elevator-OS.html` (MainActivity.kt:53–55); the app is a thin shell, so whoever can push to the repository/Pages controls the code that holds all company data. `addJavascriptInterface(Bridge(), "AIECAndroid")` (line 96) is exposed to every frame/script in the page, including the third-party Leaflet script (F-04). Bridge methods: `saveFile` writes arbitrary base64 bytes into public Downloads (filename sanitised, lines 280–288), `print`, `version`, `exit`. No certificate pinning. | MainActivity.kt:82–98, 278–305 |
| F-04 | **Medium** | **Third-party script without SRI.** Leaflet is injected from `https://unpkg.com/leaflet@1.9.4/dist/leaflet.js` with no `integrity` attribute (H:13756–13757). The page CSP is only `object-src 'none'; base-uri 'none'; form-action 'none'` (H:6), so there is no `script-src`/`connect-src` restriction. A compromised CDN response would run with full access to IndexedDB (events, company key `ck`), `crypto.subtle` keys and the Android bridge. | H:6, H:13750–13760 |
| F-05 | **Medium** | **Low-entropy PINs; offline brute force feasible.** PIN = 4 digits by default (`pinDigits: 4`, H:6037; configurable 4–6, H:5874). Hash = PBKDF2-SHA256, 150,000 iterations, salt `'aiec-pin2:' + salt` (`pinHash` H:5352–5360); legacy hash is single SHA-256 of `'aiec-pin:' + salt + ':' + pin` (H:5350), still accepted and upgraded at next login (`pinMatches` H:5792). The hash and salt travel in `USER_ADDED`/`USER_UPDATED`/`PIN_UPGRADED` events (private fields that may be cut from shared files, H:5363–5380). With 10⁴–10⁶ candidates, a copied data file yields PINs in seconds to minutes. Online lockout and `PIN_ALERT` after 3 lock-outs exist (rule H:6803). | H:5350–5360, 5791, 6037 |
| F-06 | **Medium** | **Data at rest not encrypted.** Events (names, phone numbers, prices, PIN hashes), media data URLs, the company sync key `ck` (base64, H:5510) and backup/recovery keys (`bk`, `rk`, H:5529) are stored in plain IndexedDB/localStorage. In `ls` fallback mode the ECDSA private key is stored as an exportable JWK (H:5439–5441). Anyone with the device (or WebView data directory) unlocked obtains the company key and can read every mailbox. | H:5797–5839, 9310–9323 |
| F-07 | **Medium** | **Server claim-on-first-use.** Without `ADMIN_SECRET`, the first caller of `POST /setup` becomes the office (`officeHash`) for the server (core.mjs:97–110; wrangler.toml comment lines 16–17). A newly deployed but unclaimed server can be claimed by anyone who knows the URL. | core.mjs:99–110 |
| F-08 | **Medium** | **Unverified deployment hygiene of `supabase-test.ts`.** If the test function is left deployed, any caller can create a new Postgres schema per request path `t_<6–20 chars>` (supabase-test.ts:9–14), i.e. unbounded DDL/resource consumption. Whether it is deployed is **UNVERIFIED** (no live endpoint was probed). | server/supabase-test.ts |
| F-09 | **Medium** | **Permission grants without origin check.** `onPermissionRequest` grants camera/microphone to whichever origin requests them once the Android runtime permission is held (MainActivity.kt:228–237); geolocation is granted to any origin (line 241). Combined with F-03/F-04 this widens what injected or framed content can do. Navigation is restricted to the app host (lines 193–197), but `shouldOverrideUrlLoading` does not cover sub-frames. | MainActivity.kt:192–250 |
| F-10 | **Low** | `intent:` URLs are parsed with `Intent.parseUri(..., URI_INTENT_SCHEME)` and started directly (MainActivity.kt:211–216) without clearing component/selector; a crafted link in page content could start arbitrary exported activities on the device. | MainActivity.kt:209–224 |
| F-11 | **Low** | Anonymous endpoints `POST /report` and `POST /request` are rate-limited per client IP read from `CF-Connecting-IP` or `X-Forwarded-For` (core.mjs:145, 158), which is client-controlled on non-Cloudflare deployments (e.g. Supabase); global caps (300/min, 60/min) still apply. CORS is `*` (core.mjs:63) — acceptable because auth uses bearer tokens, not cookies. | core.mjs:143–166 |
| F-12 | **Low** | Privacy: GPS coordinates are sent to OpenStreetMap Nominatim and tiles (H:13816, 16041); WhatsApp messages are composed via `wa.me` with customer phone numbers in the URL (H:10483). Screenshot blocking (`FLAG_SECURE`) is not set in the Android wrapper (README:503 notes the limitation). | cited lines |
| F-13 | **Info** | `server.json` contains only the public Edge Function URL `https://ycyvtdrtkdrnnkiszrxf.supabase.co/functions/v1/aiec`. A repository-wide grep for `eyJ…` (JWT), `service_role`, `sb_secret`, `sb_publishable`, `anon key`, `PRIVATE KEY` found **no committed keys or service-role secrets** in the working tree. `ADMIN_SECRET`, VAPID keys and the Play keystore are referenced only as environment/GitHub secrets. Git history could not be searched (shallow clone, 1 commit) — history status **UNVERIFIED**. The Supabase function must run with "Verify JWT" off (supabase.ts:3), i.e. it is publicly callable and relies solely on `core.mjs` auth. | grep results; server.json |
| F-14 | **Info** | Android manifest: `android:allowBackup="false"`, `android:usesCleartextTraffic="false"`, WebView `allowFileAccess = false`, `mixedContentMode = MIXED_CONTENT_NEVER_ALLOW`; but `allowContentAccess = true`, `databaseEnabled = true`, `mediaPlaybackRequiresUserGesture = false`, `isMinifyEnabled = false`; App Links `autoVerify="false"` (links can be claimed by other apps). VAPID private key is auto-generated and stored in the server `meta` table when not provided as a secret (core.mjs:30–38). | AndroidManifest.xml; MainActivity.kt:82–95 |
| F-15 | **Info** | Positive controls observed: per-device ECDSA signatures with second-device approval (H:5408–5480); field-level salted fingerprints allow redaction without breaking signatures; AES-GCM end-to-end mailbox with HMAC-derived per-box keys/tokens; tokens stored as SHA-256 on server; constant-time compare (core.mjs:57–62); invite codes 6 digits with 5-try deletion and 3-day max lifetime (core.mjs:113–125, 190–192); backups encrypted with a paper recovery code; two-person approval for high-risk actions (H:6719–6733); PIN re-check for money/config/export (H:9369); outsider watermark and access log (H:9544–9546, 14472). | cited lines |

---

## 9. Recommendations for a native Android rewrite (what must be preserved)

1. **Event log compatibility.** Preserve the event envelope `{id, n, t, by, type, pid, data, dev, v, sig}` (H:9407), `EV_V` handling (reject newer versions, H:8281) and the deterministic replay order (`sortEvents` + `rebuild`, H:8867). Treat `tests/fixtures/golden-v1.json` + `tests/golden.test.js` as the conformance oracle: the native reducer must produce the same state from the same log.
2. **All 212 RULES and 25 stages exactly**, including the time-gated branches (`*_FROM` constants, H:5878–5905, 6123) so that historical events re-validate identically, `ownerCovers` small-team exceptions (H:6776–6794), `needsSecond` two-person approval (H:6719–6733), `STAGE_BACK` edges (H:6007) and `canSee` visibility (H:6169–6185). The matrix in §3.4 is the minimum test inventory.
3. **Signature format v1/v2** byte-for-byte (`sigPayload` H:5390–5404, `PRIVATE_KEYS`/`PRIVATE_V2` H:5363–5380), ECDSA P-256/SHA-256 with WebCrypto-compatible raw (IEEE P1363) signature encoding, and `Sign.check` import semantics, otherwise existing records will be rejected.
4. **Sync protocol compatibility** with `server/core.mjs`: HMAC-derived box tokens/keys (H:5494–5496), AES-GCM `iv.ct` base64 blobs, ECDH join sealing (H:5708–5718), PBKDF2 invite/backup keys (H:5625, 5662), backup chunking; or a planned migration if the server changes. Move rule enforcement (F-01) server-side only with a deliberate change to the privacy model (README:97).
5. **Fix the trust anchors:** a private release keystore outside the repo (F-02); bundle the app logic in the APK or pin/verify downloaded code (F-03); remove or restrict the JS bridge; no CDN scripts (F-04); Android Keystore-backed encryption for the event DB and keys (F-06); stronger PIN policy or hardware-backed key wrapping + BiometricPrompt (F-05); `FLAG_SECURE` for outsider roles; origin-checked permission grants.
6. **Business calculations** to port verbatim with unit tests: `calcQuote` (H:6082), `paySchedule`/`payOpen` (H:6105–6142), `materialList` (H:6147), `ledger`/`payLines`/`weeksFor` (H:6681–6716), `escLevel` (H:6597), `findDuplicates` (H:10270), job numbering (H:8314), warranty/AMC dates (H:6012, 8666–8670), `CFG_LIMITS`/`CFG_ORDER` config validation (H:5858–5877).
7. **Evidence capture semantics:** photo downscale to 960 px JPEG 0.7, 10 s video, voice notes, SHA-256 per media item with GPS/device stamp (H:9689–9790); required photo counts and labels in rules (e.g. QC critical photos H:7728–7729).
8. **Offline-first behaviour:** all actions work offline and sync later; SOS, safety, wake-lock behaviour, quiet hours, escalation tones (H:15361 ff.). A native app can add reliable background work (WorkManager/FCM) that the PWA lacks (README:496, 519).
9. **Three languages** (Marathi default, Hindi, English; 4,860 keys in `L`, all complete per static test) and the accessibility features (voice read-out, dictation, large tiles).
10. **Role UX**: the per-role bottom navigation (`NAV`, H:9890) and task engine `needs()` (132 task keys) define what each role sees first; preserve task keys so notifications and escalations map 1:1.
11. **Documentation drift to resolve before relying on README:** "There is no company server" (README:492) and "no external libraries" (README:539) are no longer accurate.
