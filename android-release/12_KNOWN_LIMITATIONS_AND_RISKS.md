# 12 — Known Limitations and Risks

This release is a **fresh native app with the core lead-to-handover workflow**. It is **not** feature-equal to the 2.3 MB web app, which has 212 event types, 99 screens and 170 step-by-step forms. Nothing below is claimed as done.

## Functional parity (what is ported)

| Area | In this app | Not yet |
|---|---|---|
| Company & people | Company setup, practice company, add person (all roles except partner), stop access, PIN login + lock-out | Join requests, customer self-enquiry, login codes, recovery code, onboarding checklist, training, career ladder, contractor agreement |
| Lead & sales | Add site (photos, GPS, duplicate warning), accept/return/resubmit, contact log, lift needs, quotation with discount approval, send, won/lost | Price talks/counter offers, rate-card versions, tiers, WhatsApp templates, consent records, field-worker pay, referrals |
| Agreement & payments | Agreement + signature, token / arrival / final payments, Finance confirmation with separation of duties | Payment plan 1, cabin/retention parts, UPI pay screen, receipts, refunds, bank-statement match, variations |
| Site | Prep checklist with photos, site QC with measurements & checks, corrections | Survey wizard, infeasible decisions, housing-society approvals |
| Material | Release (supplier, technician, drawing no.), dispatch with seal, arrival with incidents, material handover with two signatures | Purchase orders, lots, serial-number checks, part claims, supplier bills, representative, safe-keeping flow on the customer's phone |
| Installation | 12 SOP steps in order with photos, inspection request, final QC with checks, fix list, re-inspection | Inspector checklist (42 points), daily safety check, check-in at site, co-worker check, surprise QC, recheck, safety log |
| Handover & after | 3 signatures, handover, warranty date, AMC visit dates, lift code, fault tickets (open/assign/fix/close), SOS | AMC visit execution, renewals, QR stickers & resident page, warranty card/claims, statutory papers (permit, inspection, licence), holds, stage back, disputes |
| Office | Escalation ladder (working days), escalations list, business rules (13 numbers, 3 texts), people | Office queue, close the day, backup approver, weekly summary, key numbers, spreadsheets, access log, clock checks, two-person approval |
| Other | Text-to-speech, 3 languages, 4 colour themes | Chat, notifications/alarms when the app is closed, marketplace, partner companies, commission, help search, printing/PDF, voice notes |

## Risks

1. **A brand-new phone trusts the first company file it loads** (as in the original). Records are signed and checked since 1.1.0 (06 C1). Set up new phones only with files that come straight from your own office.
2. **No company server.** The original's encrypted mailbox (Supabase/Cloudflare) is not used: no automatic sync, no backups, no push alarms.
3. **The web app and this app don't share data.** Running both for real work would split the records. Choose one per company, or wait for server compatibility.
4. **Not run on a device.** Camera, GPS, permission prompts, back gestures, process death, launch time and memory are reviewed in code but **not executed** (no emulator in the build environment). Test on at least one low-end and one current phone before company use (09 §4).
5. **Evaluation signing key.** The APK in `dist/` is signed with a throw-away key. The production build must be signed with the owner's key; the evaluation install must be removed first (10).
6. **Evidence stays on the phone that took it.** Records files carry photo hashes, not photos.
7. **Data at rest is not encrypted** beyond Android's own device encryption (06 C4).
8. **Form contents lost** if Android kills the app while the camera is open (06 C19).
9. **Legal texts.** The DPDP privacy notice, agreement wording and contractor terms of the original are not included and need a lawyer before real use.
10. **Business-rule numbers** (prices, margins, shaft minimums) are the original's sample values and must be set by the owner and a lift engineer.

## Next release requirements (in order)

1. ~~Per-device signing of records~~ — **done in 1.1.0** (own format; compatibility with the web app's signature format is part of item 2).
2. Company-server transport (the existing encrypted mailbox), with retry/backoff, acknowledgement, evidence upload with idempotency keys, and nightly locked backups.
3. Per-person reduced files for technicians, suppliers and partner companies.
4. Device test pass on real phones (low-end Android 8–10, mid-range, Android 16) and fixes.
5. Port the remaining workflows above, highest first: daily safety check / check-in, statutory papers, purchase orders and supplier bills, AMC visits, chat, notifications.
6. SQLCipher (or Keystore-wrapped key) encryption; SavedStateHandle for forms; replay snapshots for big companies.
