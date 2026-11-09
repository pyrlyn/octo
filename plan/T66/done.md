# T66. Done

- Core: `StateStore`, `ListStore`, `UsageStore` and `AcknowledgementStore` interfaces; the JSON
  implementations stay to read the files of earlier versions.
- `:storage`: Room KMP 2.8.5 with the bundled SQLite driver, one database `octo.db`. It has no
  mingwX64 target (`octo.windowsNative=false`); core keeps it.
  - Flat records get typed columns; tasks, transcripts, layout, preset members and trigger
    deliveries stay JSON in a column, so new fields need no migration.
  - State saves write only what changed; usage appends rows and trims to the newest.
  - SQLite errors become `IOException`, so the core's fail-open handling still applies.
  - A database that cannot be opened is moved to `octo.db.corrupt` and a new one started,
    failing that Octo runs in memory with a warning. Unreadable list rows leave the list
    empty and read-only.
  - The JSON files are imported once, in one transaction marked by a `meta` row, and renamed
    to `.imported`; an unreadable file stays and is reported.
- App: opens `Storage` and wires every store; `config.json` and `cli.json` stay JSON.

Checked: `./gradlew check` green locally (7 storage tests on jvm and macosArm64);
`-Pocto.allNativeTargets=true` builds core for mingwX64 and storage for linuxX64; CI green.

PR: https://github.com/pyrlyn/octo/pull/85
