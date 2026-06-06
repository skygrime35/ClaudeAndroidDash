# Module: domain/

> The hexagonal core. Pure Kotlin, zero Android imports.

## What lives here

| Path | Role |
|---|---|
| `domain/model/UsageSnapshot.kt` | Immutable data class holding one snapshot of rate-limit state |
| `domain/port/UsageRepository.kt` | Outbound port — "give me the latest snapshot" |
| `domain/port/RefreshTrigger.kt` | Outbound port — "ask the upstream parser to refresh" |
| `domain/port/Clock.kt` | Outbound port — current epoch seconds (injectable for tests) |
| `domain/usecase/UsageFormatter.kt` | Pure formatting (percent, remaining-time countdown) using `Clock` |

## Hard rule

No `import android.*` anywhere under `domain/`. If you need an Android capability, declare a port and let an adapter implement it. The point of the layer is to be testable on the JVM without an emulator.

## When to extend

- **New port** — when the domain needs a capability that requires Android, the filesystem, the network, or any external dependency. Define the interface here; implement under `adapter/`.
- **New use case** — when there is non-trivial computation worth naming. Trivial getters do not earn a use case. Today there is only `UsageFormatter` because formatting is the only logic the widget needs.
- **New model field** — only when the upstream JSON contract grows. Update `JsonFileUsageRepository` parsing in lockstep.

## What NOT to do

- Don't reference `R.layout.*`, `Context`, `RemoteViews`, or anything from the Android SDK.
- Don't put I/O here (no file reads, no network). Ports describe I/O; adapters do it.
- Don't add framework annotations. Plain Kotlin only.

## Keep this doc in sync

If you add a model field, port, or use case, update the table above and the rules below.
