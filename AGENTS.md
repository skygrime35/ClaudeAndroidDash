# AGENTS.md — ClaudeAndroidDash

> Single source of truth for agents (Claude Code, Cursor, etc.) working on this project. `CLAUDE.md` redirects here.

## How to code here (meta)

Adapted from [multica-ai/andrej-karpathy-skills/CLAUDE.md](https://github.com/multica-ai/andrej-karpathy-skills/blob/main/CLAUDE.md). These rules bias toward caution over speed. Use judgment for trivial work.

### 1. Think before coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### 2. Simplicity first

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### 3. Surgical changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it — don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: every changed line should trace directly to the user's request.

### 4. Goal-driven execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

**These guidelines are working if**: fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

---

## What this is

**Claude Dash**: an Android widget that displays the user's real-time Claude Code consumption (5h block + 7-day window) using **exactly** the `rate_limits` Anthropic ships to the Claude Code status bar. The numbers on screen are 1:1 with what the user sees on claude.ai.

## Architecture (hexagonal / ports & adapters)

The Kotlin module follows a clean hexagonal split:

```
com.claudedash.widget/
├── domain/                # Pure Kotlin, zero Android imports
│   ├── model/             # UsageSnapshot
│   ├── port/              # UsageRepository, RefreshTrigger, Clock
│   └── usecase/           # UsageFormatter
├── adapter/               # Infrastructure (driven adapters)
│   ├── repository/        # JsonFileUsageRepository
│   ├── refresh/           # TermuxRefreshTrigger
│   └── clock/             # RealClock
├── ui/                    # Android entry points (driving adapters)
│   ├── UsageWidget        # AppWidgetProvider
│   ├── WidgetRenderer     # Snapshot -> RemoteViews
│   ├── OnboardingActivity
│   └── SettingsActivity
└── di/
    └── ServiceLocator     # Wires ports to adapters (no DI framework)
```

**Rule of thumb**: anything under `domain/` must not import any `android.*`. Anything under `adapter/` or `ui/` may. The `domain/port/` interfaces define what the domain needs; the `adapter/` classes implement them. `ServiceLocator` wires them together.

### Runtime data flow

```
┌──────────────────────────────────────────────┐
│ Claude Code (live Termux session)            │
│   └─ renders the status bar every ~2s        │
│      via ~/.claude/statusline-command.sh     │
│         (receives a JSON on stdin with       │
│          .rate_limits.five_hour, seven_day,  │
│          .model, .cost, .context_window)     │
│                                              │
│   hook injected at the top of the script:    │
│   → jq transforms + writes                   │
│     /sdcard/Download/claude_usage.json       │
└──────────────────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│ Android widget (Kotlin APK)                  │
│  ui.UsageWidget (AppWidgetProvider)          │
│   • on update: usageRepository.read()        │
│   • on tap: refreshTrigger.trigger() (RUN_   │
│     COMMAND to Termux), then re-render       │
│   • renderer overlays text on progress bars  │
└──────────────────────────────────────────────┘
```

**Single source of truth**: the JSON at `/sdcard/Download/claude_usage.json`. Three writers, in order of preference:

1. **statusline hook** (`source: "statusline"`) — live session on this device. Richest (model, context, cost).
2. **`parser/claude_usage_api.sh`** (`source: "api"`) — **off-session path**, what the refresh button now triggers. Reads the OAuth token, queries the API, maps the account-scoped `anthropic-ratelimit-unified-*` headers. Just as accurate as statusline for the 5h/7d bars, and needs **no live session** — this is the fix for "I use Claude on another device but only the widget here". Runs inside the PRoot distro. See `AGENTS/parser.md`.
3. **`parser/claude_usage.py`** (`source: "legacy"`) — estimates from local JSONL. Least accurate; no longer wired to the button.

## Repo layout

```
ClaudeAndroidDash/
├── AGENTS.md, CLAUDE.md            # ← you are here
├── app/
│   ├── AndroidManifest.xml         # package com.claudedash.widget
│   ├── src/com/claudedash/widget/  # see "Architecture" above
│   └── res/
│       ├── layout/                 # widget_usage, activity_onboarding
│       ├── xml/widget_info.xml     # AppWidgetProviderInfo
│       ├── drawable/               # widget_bg, progress_*, ic_launcher_*
│       ├── mipmap-anydpi-v26/      # adaptive ic_launcher
│       └── values/strings.xml, styles.xml
├── parser/
│   ├── claude_usage.py             # fallback JSONL aggregator
│   ├── install_cron.sh             # termux-job-scheduler (5 min refresh)
│   └── limits_default.json         # per-tier estimates (legacy)
├── build.sh                        # no-gradle aapt → kotlinc → d8 → sign pipeline
├── setup_sdk.sh                    # fetches the official android.jar from Google
└── debug.keystore                  # generated on first build, not for prod use
```

## JSON contract consumed by the widget

Written by `~/.claude/statusline-command.sh` (hook injected at the top):

```json
{
  "updated_at": "2026-06-06T...Z",
  "source": "statusline",
  "model": "Opus 4.7",
  "context_pct": 35,
  "session_cost_usd": 0.9295755,
  "five_hour":  {"used_pct": 23, "resets_at": 1780675800},
  "seven_day":  {"used_pct": 38, "resets_at": 1781121600}
}
```

`resets_at` is epoch seconds UTC. The widget derives the countdown (`XhYYm` / `Xd Yh`).

## The statusline hook

At the very beginning of `~/.claude/statusline-command.sh`, between `input=$(cat)` and the rest, a `{ … } &` block captures the input, `jq`-transforms it, and writes the file atomically (`.tmp` + `mv -f`). Backup at `~/.claude/statusline-command.sh.bak`. If the hook is missing, the widget falls back to "Open Claude Code" state.

## Module docs (`AGENTS/`)

The `AGENTS/` directory at the repo root holds one focused doc per module. Read the relevant one before editing that module. **Keep them in sync** — if a change to the project would invalidate something written in `AGENTS/<module>.md`, update the doc in the same commit as the code change.

| File | Covers |
|---|---|
| [`AGENTS/domain.md`](AGENTS/domain.md) | The pure-Kotlin hexagonal core (`domain/model`, `domain/port`, `domain/usecase`) |
| [`AGENTS/adapters.md`](AGENTS/adapters.md) | The driven adapters under `adapter/` (repository, refresh, clock) |
| [`AGENTS/ui.md`](AGENTS/ui.md) | The Android entry points under `ui/` (widget provider, renderer, activities) |
| [`AGENTS/parser.md`](AGENTS/parser.md) | The Python fallback parser under `parser/` |
| [`AGENTS/build.md`](AGENTS/build.md) | The no-Gradle Termux build pipeline (`build.sh`, `setup_sdk.sh`) |
| [`AGENTS/statusline-hook.md`](AGENTS/statusline-hook.md) | The bash hook in `~/.claude/statusline-command.sh` (outside this repo) |

When you add a new module or a new doc, list it here too.

## Conventions

- **Kotlin 2.x**, `minSdk 26`, `targetSdk 34`. No androidx, no WorkManager, no Gradle. Everything must compile on ARM64 Termux via CLI.
- **No external dependencies**: only `android.jar` and `kotlin-stdlib.jar`. JSON is parsed with `org.json` (already in the framework).
- **No Sonnet-only filter**: the Anthropic API ships rate_limits *aggregated*. Don't filter by model — that misleads users.
- **File security**: the JSON lives at `/sdcard/Download/` (public). That's intentional — it's the Termux ↔ APK bridge. The widget requests `MANAGE_EXTERNAL_STORAGE` on first launch.
- **No chatty comments**: the code speaks for itself.
- **Hexagonal discipline**: `domain/` stays Android-free. Cross a port (interface) to reach infrastructure.
- **Module docs are part of the code**: if you change a module, also update its `AGENTS/<module>.md`. A change is not done until the doc reflects reality.

## Build procedure (Termux, on-device, ARM64)

### Prerequisites (one-shot)

```bash
pkg install aapt aapt2 apksigner d8 kotlin openjdk-21 zipalign termux-api jq python
bash setup_sdk.sh                    # downloads android-34/android.jar (~25 MB)
                                     # from dl.google.com/android/repository/platform-34-ext7_r02.zip
```

⚠️ **Critical**: do NOT use `$PREFIX/share/aapt/android.jar` (the jar packaged by Termux), it is broken (all `android:*` attrs are missing). You must use the one at `~/.cache/android-sdk/android-34/android.jar`.

### Build

```bash
cd ~/Projects/ClaudeAndroidDash
bash build.sh
```

The script chains:

1. `aapt2 compile --dir app/res -o build/res.zip`
2. `aapt2 link -I <real-android.jar> --manifest app/AndroidManifest.xml --java build/gen build/res.zip`
3. `kotlinc -classpath <real-android.jar> -d build/classes app/src build/gen`
4. `d8 --lib <real-android.jar> kotlin-stdlib.jar build/classes/**/*.class --output build/dex`
5. `zip -uj build/app-with-dex.apk build/dex/classes.dex`
6. `zipalign -p -f 4 ... build/app-aligned.apk`
7. `apksigner sign --ks debug.keystore --ks-pass pass:android ... --out build/<APK>.apk`
8. `cp build/<APK>.apk /sdcard/Download/`

### Version bump

Before a release build, increment both:

- `app/AndroidManifest.xml` → `android:versionCode` (integer) + `android:versionName` (string)
- `build.sh` → `APK_NAME="${APK_NAME:-ClaudeDash-X.Y.apk}"`

### Install

```
Files → Download → ClaudeDash-X.Y.apk → Install
```

If Android refuses: Settings → "Unknown sources" for the file manager. On first launch: grant "All files access". Then long-press the home screen → Widgets → "Claude Dash".

### Rebuild after a change

```bash
bash build.sh   # idempotent, overwrites the APK in /sdcard/Download/
```

Uninstall the previous APK first if `versionCode` did not change. The widget pinned to the home screen still points at the old `Receiver` — remove it and re-pin after reinstalling.

## Known pitfalls

| Symptom | Cause | Fix |
|---|---|---|
| `ClassNotFoundException: kotlin.jvm.internal.Intrinsics` at launch | `kotlin-stdlib.jar` not included in `d8` | Verify that `build.sh` passes `$PREFIX/opt/kotlin/lib/kotlin-stdlib.jar` to `d8` |
| `error: failed to load include path .../android.jar` | Broken Termux jar | Use `~/.cache/android-sdk/android-34/android.jar` (see `setup_sdk.sh`) |
| `No resource identifier found for attribute 'layout_width'` | Termux jar lacks framework attrs | Same fix as above |
| Widget shows "Open Claude Code" | Statusline hook not written / Claude Code never launched / storage permission denied | Run a Claude Code session (the hook executes), or enable "All files access" for the app |
| Refresh does nothing | Termux:API missing, or `allow-external-apps=true` missing | `pkg install termux-api` + edit `~/.termux/termux.properties` + `termux-reload-settings` |
| Launcher icon is invisible | Old shape-only `mipmap-mdpi/ic_launcher.xml` | Use an `<adaptive-icon>` in `mipmap-anydpi-v26/` (already in the repo) |
| `d8: Unexpected error while reading kotlin.Metadata` | Kotlin 2.x metadata vs old d8 | Non-fatal, ignore |

## Out of scope

- No Play Store / public distribution
- No androidx, no Material Components
- No Android < 8 support (`minSdk=26`)
- No Sonnet/Opus/Haiku split — the Anthropic API does not expose it in this stream
