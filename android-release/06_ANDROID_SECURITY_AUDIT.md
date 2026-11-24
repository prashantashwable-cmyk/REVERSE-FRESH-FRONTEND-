# 06 — Android Security Audit

Scope: the new native app (this repository) and, for comparison, the existing web/WebView app (findings from 01 §8). Method: code review by the author, then an independent model review of commit `4610c9a` (24 findings). Each finding below says whether it was **fixed** in this release (with the regression test) or **accepted / open** with the reason.

## A. Findings in the existing app (UNLIT-GPT-HTML), carried from 01

| Sev | Finding | Status in the new app |
|---|---|---|
| High | Business rules are checked only on phones; the server can't read them | Same design (rules on every phone, no server). See C1 |
| High | `android/aiec-test.jks` and its password are committed; the release falls back to it | **Not repeated.** No keystore in git, `.gitignore` blocks keys, release signing only from env/CI secrets (10) |
| High | WebView loads live remote code from GitHub Pages and exposes the JS bridge `AIECAndroid`; camera/mic/GPS granted to any origin | **Not repeated.** No WebView, no remote code, no bridge |
| Medium | Leaflet from unpkg without SRI | Not repeated (no web content) |
| Medium | 4-digit PINs; data and company key unencrypted on the phone | PINs 4–6 digits, trivial PINs refused (live mode), same PBKDF2-150k. Data is app-private but **not encrypted at rest** (C4) |

## B. Configuration review of the new app (verified in the release APK with `aapt2 dump badging`)

| Item | Result |
|---|---|
| Exported components | Only `MainActivity` (launcher). FileProvider `exported=false` |
| FileProvider paths | `files/capture/` (camera output) and `cache/export/` (shared file; cleared before each export) |
| Backup | `allowBackup=false`, `fullBackupContent=false`, data-extraction rules exclude every domain for cloud and device transfer |
| Network | No `INTERNET` permission; cleartext off; system CAs only |
| Permissions | CAMERA, FINE/COARSE LOCATION, VIBRATE — each asked when first needed |
| Screenshots | `FLAG_SECURE` while a technician/supplier/partner is logged in |
| Debug | Release is not debuggable; R8 minified; no logging of personal data (no `Log.*` calls in the app) |
| Secrets in APK | None: no API keys, no server URL, no keystore (checked with `unzip -l` and code review) |
| Session | PIN on every login; 5 wrong PINs lock that person for 15 min on that phone; auto-lock after 5 min in the background |
| Record signing | Each phone has an ECDSA P-256 key in the Android Keystore (hardware-backed where the phone supports it); every record carries `kid` + `sig` |

## C. Findings in the new app

| # | Sev | Finding | Status |
|---|---|---|---|
| C1 | Critical → **fixed in 1.1.0** | Records files were not signed: anyone who could edit a file could write records "as" another person. | Every record is signed by the phone that made it: ECDSA P-256 / SHA-256, key in the Android Keystore, can't be read out (`Signing.kt`, `DeviceKey.kt`). Other phones accept a record only if it verifies with that person's phone key and the office approved that key (`KEY_ADDED` → `KEY_APPROVED` on the People screen; the office checks the phone code by phone). The founding phone's key is in the company record. PIN fields are signed by fingerprint, so cutting them keeps the signature valid. Edited, unsigned, forged and spoofed records are refused (tests in `SigningTest`). Defence in depth: imported people/rule changes still need the HQ. **Remaining:** on a brand-new phone the first file defines the company (as in the original), so set up new phones only with files from your own office. |
| C2 | High → **fixed** | The export carried every PIN hash, so a copied file allowed offline PIN cracking. | Office export puts in only the PIN hash of the person the file is for; everyone else's is removed (`stripped`). Staff outside the office export only their own records, with no PIN hashes (`exportJson(forUid, onlyBy)`) |
| C3 | Medium (open) | An office file for another staff phone still holds all customer contacts and amounts | Outsiders (technician/supplier/partner) cannot be chosen as a file target. Per-person reduced files, as in the original, are next release |
| C4 | Medium (open) | Database and evidence are app-private but not encrypted at rest; a rooted phone or a forensic copy can read them | Android file-based encryption protects them while the phone is locked. SQLCipher / Keystore-wrapped keys are next release |
| C5 | High → **fixed** | A malformed time in an imported record could crash every screen | `validate()` refuses times that aren't ISO instants or are more than 6 h in the future; `Time.day` never throws. Test `badTimesAreRefused` |
| C6 | High → **fixed** | The order after a restart differed from the order after an import (different job numbers, states flipping, silent drops) | One canonical order `(time, id)` in `EventDb.all()` and in import; local records that stop passing are logged in `rejected` and counted |
| C7 | High → **fixed** | Dead end: Admin records a payment, nobody may confirm it | The cover check leaves out the person who recorded the payment (original `coverExcl`); the record task is not given to the only possible confirmer. Test `recorderIsLeftOutOfCoverSoSomeoneElseConfirms` |
| C8 | High → **fixed** | Dead end: the author of a below-floor quote could not withdraw it | The author may reject (never approve). Test `authorCanRejectButNotApproveOwnQuote` |
| C9 | High → **fixed** | Dead end: a job's Sales person left, or was the covering office | Other Sales users can see and take over such a job; `salesUid` validated. Test `salesCanTakeOverAJobWhoseSalesPersonIsGone` |
| C10 | Medium → **fixed** | Payment amount not tied to the schedule (₹1 could unlock the next stage; NaN accepted) | The amount must equal the scheduled amount; customers can't report cash. Test `paymentAmountMustMatchSchedule` |
| C11 | Medium → **fixed** | Site QC could approve with failed checks and one photo | All checks required; approval refused with any fail; 2 photos |
| C12 | Medium → **fixed** | Office could mark installation fixes done | Install fixes: only the job's technician; site fixes: never a technician |
| C13 | Medium → **fixed** | Customer names shown to outsiders on task cards | Hidden (locality only), as on the other screens |
| C14 | Medium → **fixed** | Opening the company (full replay) on the main thread at start | Moved to a background thread with the spinner; cheap `hasOrg` query |
| C15 | Low → **fixed** | Double-tapping Save could create duplicates | In-flight flag on every form |
| C16 | Low → **fixed** | User-update guards missing (self role change, customer role swaps) | Refused |
| C17 | Low → **fixed** | Job-number suffix ran out after 23 offline duplicates; a half-applied record could stay in state after a reducer error | Numeric suffixes after Z; a record that breaks the reducer is left out and the history is replayed without it |
| C18 | Low → **fixed** | SOS reported "sent" when saving failed; waited up to 10 s for GPS | "Sent" only after it is saved; GPS wait 4 s |
| C19 | Medium (open) | Form contents and the screen stack are lost if Android kills the app while the camera is open | The capture file path survives; typed fields don't. Next release: SavedStateHandle |
| C20 | Low (open) | The PIN lock-out uses the wall clock; changing the phone time ends it early | Accepted; the original behaves the same |
| C21 | Low (open) | A technician can sign the customer's handover slot with a reason but no photo of a paper signature | The original also wanted a photo of the paper; next release |
| C22 | Low (open) | Payments due as ₹0 (token 0, or arrival 100%) still wait for a payment record | Rare configuration; next release |

## Data collected, purpose, permissions, retention

| Data | Why | Where | Kept |
|---|---|---|---|
| People: name, role, phone, PIN hash | Login, who did what | App database | Until the company data is removed |
| Customer contact, site address, GPS | Lead to handover | App database | As above |
| Photos and signatures (stamped with time, GPS, person, job) | Evidence for each step | `files/evidence/` | As above; captures not used are deleted after 24 h |
| Money: quotes, schedule, payment references | Payments and checks | App database | As above |

Nothing leaves the phone except a records file the person chooses to share. There is no analytics, crash reporting or advertising SDK. A full privacy notice for India's DPDP Act 2023 (as in the original) still has to be reviewed by a lawyer and added (12).
