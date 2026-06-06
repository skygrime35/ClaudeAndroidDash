# Module: ui/

> Driving adapters — Android entry points. AppWidgetProvider, Activities, view rendering.

## What lives here

| Path | Role |
|---|---|
| `UsageWidget.kt` (package root, **not** under `ui/`) | `AppWidgetProvider`. Handles `onUpdate` (system schedule + initial bind) and `onReceive` for the custom `ACTION_REFRESH` |
| `ui/WidgetRenderer.kt` | Pure renderer — given a `UsageSnapshot?`, builds a `RemoteViews` |
| `ui/OnboardingActivity.kt` | First-launch screen — explains storage permission + Termux RUN_COMMAND setup, then closes |

### Why `UsageWidget` is not under `ui/`

The Android launcher caches the AppWidgetProvider class name (`android:name=".UsageWidget"`) across installs. If we moved the receiver into `com.claudedash.widget.ui.UsageWidget`, users upgrading from a previous version would see **two entries** in the widget picker — a phantom (the cached, now-missing `.ui.UsageWidget` reference) and the real one. Keep this class pinned at the package root for cross-version stability.

## AppWidget lifecycle

- `onUpdate` fires when the system wants to refresh (per `updatePeriodMillis` in `widget_info.xml` — currently 30 min) or when a new widget instance is bound.
- `onReceive(ACTION_REFRESH)` fires when the user taps the widget root. Steps:
  1. Call `RefreshTrigger.trigger()` (kicks Python parser via Termux IPC).
  2. Render the indeterminate (refreshing) state immediately.
  3. After `REFRESH_RENDER_DELAY_MS` (2.5 s), re-read the JSON and render the data state.

## Rendering rules

`WidgetRenderer` has three modes:

- **`renderSnapshot(null)`** — empty state, prompts the user to open Claude Code.
- **`renderSnapshot(data)`** — fills both bars and overlay labels.
- **`renderRefreshing()`** — indeterminate progress on both bars, `…` in the percent slots.

All modes attach the `ACTION_REFRESH` PendingIntent on `R.id.widget_root` so the entire widget is a tap target.

## Layout invariants (`res/layout/widget_usage.xml`)

- 1-cell-tall capable: `widget_info.xml` declares `minHeight=60dp`, `targetCellHeight=1`.
- Each row is a `FrameLayout` with a `ProgressBar` (bottom) and a `LinearLayout` of overlaid `TextView`s (top), so labels sit *on* the bar — no vertical space wasted on titles.
- Text uses `android:shadowColor="#000000"` for legibility against any progress color.
- Icon is an adaptive icon at `mipmap-anydpi-v26/ic_launcher.xml`. The bare `mipmap-mdpi` shape drawable is *not* a valid launcher icon and was removed.

## Stable intent action

`UsageWidget.ACTION_REFRESH = "com.claudedash.widget.ACTION_REFRESH"`. Don't rename — pinned widgets reference it across versions.

## Keep this doc in sync

If you add a render mode, a new activity, a new clickable region, or change widget sizing, update the relevant section.
