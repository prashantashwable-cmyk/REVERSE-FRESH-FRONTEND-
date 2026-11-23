# 07 — Mobile UI/UX and Accessibility

The frontend was built from zero in Jetpack Compose, keeping the original's identity and working style: deep navy + lift-door gold on warm cream, one big dark **Start My Next Task** button, pictures and icons on every choice, 🔊 on every screen, and Marathi / Hindi / English one tap away (the म / हि / EN button). Screenshots are in `screenshots/` (rendered from the real app by the UI tests).

## Screens built

| Screen | Who | Screenshot |
|---|---|---|
| Welcome: language, Staff login, Practice, New company | everyone | `01_welcome_{mr,hi,en}.png`, small phone `30_small_welcome_mr.png` |
| New company setup (HQ, non-trivial PIN twice) | first HQ | — |
| Who are you? (people list, "Continue as …") | staff | `02_login_list_en.png` |
| PIN pad (big keys, 5 tries → 15 min lock) | staff | `31_small_pin_mr.png` |
| Home: greeting, SOS alarms, Start My Next Task, tasks with two bars, office tiles | every role | `03` field, `05` sales, `09` admin, `15` technician, `18` HQ, `21` strong contrast |
| Projects: search by job no / name / mobile / address, filters | staff | — |
| Project: phase stepper, next line, your actions, details, history | by visibility | `10_project_detail_{en,hi}.png`, technician view `17` |
| Action forms (35 actions): photos, GPS, choices, checks, signatures | by rule | `04` add site, `06` check lead, `08` refusal, `11` approve quote, `14` verify payment, `16` install step |
| Done: what happened, what happens next | after every save | `07_saved_{en,mr}.png` |
| People: add person, stop access | HQ / Admin | `13_people_en.png` |
| Escalations | HQ / Admin | `12_escalations_en.png` |
| Me: language, colours, logout, about | everyone | `19_me_en.png`, dark `20` |
| Business rules (with allowed ranges) | HQ | `22_business_rules_en.png` |
| Share data (file sync, counters) | office staff | `23_share_data_en.png` |

## Checks

| Topic | What was done | Verified how |
|---|---|---|
| Small / low-resolution screens | All screens scroll; tested at 320×640 dp mdpi | Screenshot `30`, `31` |
| Touch targets | Buttons ≥ 56 dp (big button 64 dp), chips ≥ 48 dp, PIN keys 78 dp | Code review |
| Keyboard and forms | `adjustResize`, number/phone/PIN keyboards, required fields refuse with "Please fill: <field>" | UI test `salesWorkflowAndRefusals` |
| Safe areas / system bars | Edge-to-edge + Material 3 `Scaffold` insets | Code review. Gesture-navigation devices not tested |
| Loading / empty / error states | Spinner overlay while busy; "No work for you right now"; refusals in a red box using the rule's own message | Screenshots `03`, `08` |
| Offline / sync indicator | "Saved on this phone" under every save button; Share screen counts waiting / shared / loaded / refused | Screenshot `23` |
| Devanagari rendering | Hindi and Marathi render correctly, including conjuncts | Screenshots `01`, `03`, `04`, `07`, `10`, `15` |
| Font scaling | All text uses `sp`; layouts wrap and scroll | Code review. Large-font screenshots not taken |
| Screen readers | Titles marked as headings; icon buttons have localized content descriptions; chips announce "✓" when selected | Code review. TalkBack not run |
| Back navigation | Hardware/gesture back goes back one screen (`BackHandler`); at the first screen Android closes the app | Code review |
| Resume / pause | Auto-lock after 5 min in background (next person types their PIN) | Code review. Not run on a device |
| Colours | System / light / dark / strong contrast | Screenshots `20`, `21` |

## Changes from the original UI, and why

* **One app, native controls:** Material 3 components instead of HTML cards, so fonts, keyboard and back behave like Android.
* **Two bars on cards:** kept (progress + deadline, with on time / behind / late / paused).
* **Emoji:** two newer emoji (🛗, 🪢) were replaced with older ones because they show as empty boxes on older font sets. Text labels are always shown next to the icons.
* **Customer/Join doors:** not on the first screen, because the customer self-enquiry and join-request flows are not ported (see 12). Customers are added by the office and log in through Staff login with their own PIN.
