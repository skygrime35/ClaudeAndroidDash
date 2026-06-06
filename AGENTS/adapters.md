# Module: adapter/

> Driven adapters — concrete implementations of `domain/port/` interfaces. Free to import Android, talk to the filesystem, fire intents.

## What lives here

| Path | Implements | What it does |
|---|---|---|
| `adapter/repository/JsonFileUsageRepository.kt` | `UsageRepository` | Reads `/sdcard/Download/claude_usage.json`, parses both `source: "statusline"` and `source: "legacy"` schemas, returns `UsageSnapshot?` |
| `adapter/refresh/TermuxRefreshTrigger.kt` | `RefreshTrigger` | Sends a `RUN_COMMAND` intent to `com.termux.app.RunCommandService` which executes `parser/claude_usage.py` |
| `adapter/clock/RealClock.kt` | `Clock` | `System.currentTimeMillis() / 1000L` |

## Gotchas

- **Hardcoded paths** in `TermuxRefreshTrigger` (`PYTHON_BIN`, `PARSER_SCRIPT`) target Termux ARM64 on this device. If the project moves elsewhere these break — make them configurable then.
- **Two JSON schemas** in `JsonFileUsageRepository`: prefer `statusline` (1:1 with claude.ai). `legacy` exists for the Python fallback parser only.
- **Silent failure** on read/parse errors returns `null` — the widget then renders the empty state. This is intentional: a corrupt JSON file should not crash the widget.
- **`MANAGE_EXTERNAL_STORAGE`** is required for the read on Android 11+. Without it, `jsonFile().exists()` returns `false`.

## When to extend

- **Swap storage backend** — replace `JsonFileUsageRepository` with another implementation of `UsageRepository`. The widget code does not need to change.
- **Swap refresh mechanism** — same pattern for `RefreshTrigger`.
- **Add an adapter** — needed when a new port is defined in `domain/port/`.

## Wiring

All adapters are instantiated in `di/ServiceLocator.kt`. If you add an adapter, also add a property/factory in `ServiceLocator`.

## Keep this doc in sync

If you add an adapter, change a port implementation, or move a hardcoded path, update the table above and the gotchas list.
