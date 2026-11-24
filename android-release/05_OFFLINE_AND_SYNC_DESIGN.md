# 05 — Offline and Sync Design

## What the original used

The audit (01 §2) found the web app keeps an append-only event log in IndexedDB (falling back to localStorage, then memory). The state is rebuilt by replaying events through `validate()` + `apply()`, and sync is either file exchange or an end-to-end-encrypted mailbox server.

## What this app uses, and why

The same event-sourcing model is kept, but stored in **SQLite** (`data/EventDb.kt`):
* Android SQLite is durable across process death and reboot (WAL journal, committed before the UI confirms). Browser storage can be evicted.
* There is one file per mode (`aiec_live.db`, `aiec_practice.db`), so practice data can never mix with company data.

### Schema v2

```
events(seq PK AUTOINCREMENT, id TEXT UNIQUE, type, by_uid, pid, t, dev, v, data JSON,
       sync_state TEXT DEFAULT 'local_saved', attempts INT, last_error TEXT,
       kid TEXT, sig TEXT)                -- added in schema v2 (1.1.0): signing key id and ECDSA signature
rejected(id PK, raw, reason, t)      -- records another phone sent that the rules refused
```

`onUpgrade` 1 → 2 adds the `kid`/`sig` columns. Records this phone saved under 1.0.0 are signed with this phone's key at the first start of 1.1.0. Company records are never dropped.

## Write path (durability)

`Repository.perform()`:
1. Build the event: id = `millis(base36)-device-random6`, sortable and unique, also used as the idempotency key.
2. `Rules.validate(state, event)`. A refusal returns an error and **nothing is saved**.
3. `INSERT … ON CONFLICT IGNORE` with `sync_state = pending_sync`.
4. Only after the insert succeeds is the event applied to the in-memory state and the screen shows **Done**.

At start the whole log is replayed through the rules (`Reducer.rebuild`). A record that no longer passes is logged in `rejected` and not applied.

## Sync states

| State | Meaning in this release |
|---|---|
| `local_saved` | (reserved) saved on the phone |
| `pending_sync` | made on this phone, not yet shared |
| `synced` | included in a records file this phone shared |
| `imported` | came from another phone's file and passed the rules |
| `failed` | (reserved for a future server transport) |

"Shared" means "put in a file". It does **not** mean another phone confirmed receipt. No server acknowledges anything in this release, so the app never claims a server confirmed an action.

## File sync between phones

* **Share records** (`Repository.exportJson`): `{format:"aiec-events", v:1, org, events:[…]}`, sent through the Android share sheet.
* **Load records** (`Repository.importJson`):
  * Files from another company are refused (`org` must match).
  * Events already on the phone are skipped by id: re-loading the same file twice is a no-op.
  * The merged history is ordered by `(time, id)`, so every phone gets the same order, and replayed through the rules.
  * New events the rules refuse go to `rejected`. The screen shows *added / duplicates / refused*.

### Conflict handling

There is no "last write wins" on fields. Two phones acting on the same job offline produce two events; both are replayed in time order and the rules decide. Example: two Sales phones both mark a deal won — the second `DEAL_WON` is refused because the stage is no longer `quoted`, and it is listed as refused. Job numbers made offline on two phones get `-B`, `-C` … in history order, as in the original.

### Known gaps (honest list)

* **Photos and signatures are not in the records file.** Only their SHA-256 references travel; the images stay on the phone that took them.
* An **office** file (Head Quarter/Admin → one chosen staff member's phone) carries every record so that phone can re-check everything, but only that person's PIN hash; everyone else's is removed. **Other staff** (including technicians and suppliers) share only the records they made themselves, with no PIN hashes, for the office to load. Office files for technicians and suppliers (with customer details removed, as in the original) are not ported. See 06 C2/C3.
* **Every record is signed** by the phone that made it (since 1.1.0). A loaded record must verify with the author's phone key, which the office approved once (People → Phones waiting for approval). If the office approves the phone later, loading the same file again accepts the earlier records, which are checked again with the approved key. Imported changes to people, PINs or business rules additionally need the Head Quarter.
* Phones and restarts all use one order (time, then id). If a wrong phone clock makes one of this phone's own records invalid after an import, it is not applied, and it is listed in *refused* on the Share screen, never dropped silently. Records dated more than 6 h in the future are refused.
* Retry with backoff, server acknowledgement and push are not implemented, because there is no server transport. The `sync_state`, `attempts` and `last_error` columns are ready for one.

## Tests

| # | Scenario from the brief | How it was verified | Result |
|---|---|---|---|
| 1 | Create a record offline | Every UI test runs with no network; SQLite insert, then state | Pass (Robolectric) |
| 3–4 | Close/reopen keeps data | `Reducer.rebuild` of the stored history gives the same state (EngineTest `fullLeadToHandover…`); practice DB re-opened by a new ViewModel in each UI test | Pass (JVM). Not run on a device |
| 8 | Retry without duplicates | Rebuild of `history + history` applies each event once (EngineTest) | Pass |
| 9–10 | Two devices / conflicts | Forged/out-of-order events refused on rebuild (EngineTest `forgedEventsAreRefusedOnRebuild`) | Pass (JVM). Two-device test not run |
| 2, 5–7, 11–12 | Offline evidence capture, network restore, interrupted upload, permission denial on device, low storage, process kill | Need a real phone or emulator | **Blocked** — no Android device or emulator here (no KVM, see 09) |
