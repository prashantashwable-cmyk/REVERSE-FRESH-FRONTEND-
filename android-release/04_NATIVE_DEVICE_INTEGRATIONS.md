# 04 — Native Device Integrations

Only capabilities that a ported workflow needs are included. Manifest permissions: `CAMERA`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `VIBRATE`. Compared with the source WebView app, these are not requested: `INTERNET`, `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `WRITE_EXTERNAL_STORAGE`.

> `INTERNET` is not declared because this release makes no network calls. Sharing works through the Android share sheet: the receiving app (WhatsApp, Drive) does the upload.

## Camera and evidence (`ui/screens/ActionScreen.kt` → `PhotoField`, `data/EvidenceStore.kt`)

| Requirement from the brief | Implementation | Status |
|---|---|---|
| Native camera | `ActivityResultContracts.TakePicture` with a FileProvider URI in `files/capture/` | Implemented. Runs on a real phone only; not run here (no device or emulator, see 09) |
| Capture a new photo, no silent gallery | The only path is the camera; no gallery picker exists anywhere | Implemented |
| CAMERA permission | Asked at first use; denial shows "camera permission is off" and the form refuses to save without the required photo | Implemented |
| Linked to project, task and user | The photo reference goes into the event's `ev` list (job id, event type and person are on the event) | Unit-tested (EngineTest) |
| Timestamp, GPS and accuracy | `EvidenceRef.t`, `gps{lat,lon,acc,t}`; stamp band on the image: date/time IST, GPS ±m or "GPS —", person (role), job number, company, phone id | Implemented |
| Integrity | SHA-256 of the stored (stamped) bytes kept in the event | Implemented |
| Safe storage offline | `files/evidence/` (app-private, not in the gallery, excluded from backup); images reduced to ≤1600 px JPEG 80 | Implemented |
| Upload queue and idempotency | No server in this release. Photos stay on the phone, and the records file carries only their hashes | **Not implemented** (see 05/12) |
| Clean-up | Unused captures older than 24 h are deleted at start | Implemented |

Signatures: a finger-drawn `SignaturePad` saves a PNG to `files/evidence/` with its SHA-256 (agreement, material handover ×2, handover ×3).

## Location (`device/Device.kt` → `Gps`)

* No dependency on Google Play services. `LocationManager.getCurrentLocation` is used on Android 11+, `requestSingleUpdate` on 8–10, with a 20 s timeout.
* A cached fix is used only if it is under 2 minutes old.
* Results: `Fix(point with accuracy and time, approximate?)`, `Denied`, `Disabled`, `Timeout`. Each is shown to the person in their language.
* Accuracy worse than 50 m is shown in orange with "location is not accurate — recorded as it is, not as proof". Location is never shown as verified presence.
* Used for: new site (lead), material arrival, the photo stamp and SOS.

## Text-to-speech (`Speaker`)

The 🔊 button on every screen reads the screen title and main line in the chosen language (`hi-IN`, `mr-IN`, `en-IN`), using the phone's TTS engine. It works offline when the voice is installed.

## Phone calls

SOS offers the company rescue number (business rule `sosNumber`) and 112 through `ACTION_DIAL`. The dialer opens with the number filled in; the app never places a call itself, so `CALL_PHONE` is not needed.

## Files and sharing

* Export: `cache/export/AIEC-records-<date>.json`, shared with `ACTION_SEND` and a one-time read grant.
* Import: Storage Access Framework `OpenDocument`. No storage permission is needed.

## Not included (no ported workflow needs them yet)

Push notifications (FCM), biometric unlock, QR scanning, WhatsApp deep links, voice notes, wake lock, printing/PDF. Each is listed in 12 with the feature that would need it.
