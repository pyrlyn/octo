---
status: in progress
priority: P2
complexity: 5
readiness: 5%
agent: Claude Code / claude-opus-5-5
---

# T66. Keep Octo's data in SQLite

Octo keeps its data in one JSON file per kind (`state.json`, `usage.json`, `devices.json`,
`shares.json`, `presets.json`, `triggers.json`, `acknowledgements.json`). Every change rewrites a
whole file; `usage.json` alone is rewritten with up to 10 000 records after every turn. The
creator asked to keep this data in SQLite instead.

Decisions taken with the creator:

- Everything moves to one database, `octo.db` in the Octo home, except the user-edited
  configuration: `config.json` and the CLI's `cli.json` (its pairing URL and token) stay JSON.
- Room (`androidx.room` 2.8.5, the stable line) with the bundled SQLite driver
  (`androidx.sqlite:sqlite-bundled`), through KSP.
- Room publishes no `mingwX64` build (checked on Google Maven: jvm, macosArm64, linuxX64 and
  linuxArm64 only), so it lives in a new `:storage` module for jvm, macosArm64 and linuxX64.
  `:core` keeps every target and sees storage only through its own interfaces.

Done means: the app reads and writes all of the above through `octo.db`; a user's existing JSON
files are imported once on the first start; a database that cannot be opened never blocks
start-up (fail open, as `state.json` does today); docs and `toolchain.md` describe the new
storage.

## Execution plan

1. **Interfaces in core.** `StateStore` becomes an interface (`load`, `save`); the current file
   store is renamed `JsonStateStore`. A `ListStore<T>` interface (`items`, `warning`, `change`)
   is extracted from `JsonListFile`, and `Devices`, `Shares`, `FilePresetLibrary`, `Triggers`,
   `UsageLog` and `TermsGate` take it instead of a file. Behaviour and core tests stay as they
   are; the JSON classes remain as the importer's readers and the test doubles.
2. **`:storage` module.** KMP with jvm, macosArm64 and linuxX64 (the KMP convention plugin gets
   a switch to leave `mingwX64` out). `OctoDatabase` with Room entities and DAOs:
   - flat records get typed columns: `devices`, `shares`, `usage_records`, `acknowledgements`,
     `trigger_records` (deliveries as a JSON column);
   - nested, often-growing domain objects keep their kotlinx-serialization form in a JSON
     column next to typed keys, so a new field in `Task` needs no migration: `tasks` (id,
     position, body), `transcript_entries` (task id, seq, body), `presets` (name, position,
     members), `layout` (one row).
   Room `ListStore`/`StateStore` implementations write each change in one transaction; usage
   appends rows and trims the oldest past `UsageLog.MAX_RECORDS` instead of rewriting them all.
   Schema JSON is exported to `storage/schemas/` for future migrations.
3. **Import.** On a fresh database, `Storage.open` reads each existing JSON file with the core's
   JSON readers inside one transaction, then renames each imported file to `<name>.imported`.
   A file that cannot be read is left in place and reported as a warning.
4. **Fail open.** A database that cannot be opened is moved aside as `octo.db.corrupt` and a
   fresh one is created, with a warning shown in the UI.
5. **Wiring.** `app` opens `Storage` from `OctoPaths` and passes its stores to the core.
6. **Docs.** `docs/architecture.md` §2 and §8, `plan/README.md` decision, `toolchain.md` rows
   (Room runtime, sqlite-bundled, KSP, Room Gradle plugin).

## Verification

- `:storage` tests (jvm and the host native target) with a temp-dir database: round trip of
  every store, usage trimming, import of fixture JSON files, `.imported` renames, a corrupt
  database moved aside.
- `./gradlew check` (tests, ktlint, detekt) passes; `-Pocto.allNativeTargets=true` still builds
  core for `mingwX64`.
- The app starts against a copy of a real Octo home and shows the same tasks.
