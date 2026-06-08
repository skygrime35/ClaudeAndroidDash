# Module: adapter/

> Driven adapters — concrete implementations of `domain/port/` interfaces. Free to import Android, talk to the filesystem, fire intents.

## What lives here

| Path | Implements | What it does |
|---|---|---|
| `adapter/repository/JsonFileUsageRepository.kt` | `UsageRepository` | Reads `/sdcard/Download/claude_usage.json`, parses `source: "statusline"` / `"api"` (same shape) and `"legacy"`, returns `UsageSnapshot?` |
| `adapter/refresh/TermuxRefreshTrigger.kt` | `RefreshTrigger` | Sends a `RUN_COMMAND` intent to `com.termux.app.RunCommandService` which runs `proot-distro login ubuntu -- bash parser/claude_usage_api.sh` (the off-session API refresh, inside the PRoot distro) |
| `adapter/clock/RealClock.kt` | `Clock` | `System.currentTimeMillis() / 1000L` |

## Gotchas

- **Hardcoded paths/distro** in `TermuxRefreshTrigger` (`PROOT_DISTRO_BIN`, `DISTRO = "ubuntu"`, `API_SCRIPT`) target this device. If the PRoot distro is renamed or the project moves, update these.
- **Why proot-distro**: Claude Code (and `~/.claude/.credentials.json`) live inside the PRoot distro; the widget can only fire host-Termux intents, so the trigger logs into the distro to reach the token.
- **JSON schemas** in `JsonFileUsageRepository`: `statusline` (live session) and `api` (off-session, `parser/claude_usage_api.sh`) share one shape and both go through `parseStatusline` — both are 1:1 with claude.ai. `legacy` exists for the Python fallback parser only.
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
