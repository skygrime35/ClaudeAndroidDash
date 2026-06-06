# Module: statusline hook

> Bridge between live Claude Code sessions and the widget. Lives **outside** this repo, in the user's `~/.claude/`.

## What it is

`~/.claude/statusline-command.sh` is the script Claude Code calls every ~2 seconds to render the status bar. It receives a JSON on stdin from Anthropic containing (among others) `rate_limits.five_hour`, `rate_limits.seven_day`, `model.display_name`, `cost.total_cost_usd`, `context_window.used_percentage`.

We inject a one-shot block at the very top — right after `input=$(cat)` — that captures the same input, jq-shapes it into the schema the widget reads, and writes `/sdcard/Download/claude_usage.json` atomically.

## Why outside the repo

The hook belongs to the user's Claude Code installation, not this project. Editing it here would not affect the running shell. We document it so any agent reinstalling on a new device can recreate it.

## Backup

The pre-injection version is preserved at `~/.claude/statusline-command.sh.bak`. Restore by `cp ~/.claude/statusline-command.sh.bak ~/.claude/statusline-command.sh`.

## Atomicity

The hook writes to `claude_usage.json.tmp` then `mv -f` to the final name. The widget reading mid-write is therefore impossible — it either sees the previous version or the new one.

## Shape of the output

```json
{
  "updated_at": "2026-06-06T...Z",
  "source": "statusline",
  "model": "Opus 4.7",
  "context_pct": 35,
  "session_cost_usd": 0.93,
  "five_hour": {"used_pct": 23, "resets_at": 1780675800},
  "seven_day": {"used_pct": 38, "resets_at": 1781121600}
}
```

`JsonFileUsageRepository.parseStatusline` reads exactly this shape. If you change keys in the hook, change the parser in lockstep.

## Verifying

After editing the hook, run a short Claude Code message and check:

```bash
jq '.updated_at, .source, .five_hour, .seven_day' /sdcard/Download/claude_usage.json
```

`source` must be `"statusline"`. If you see `"legacy"`, the Python parser ran instead — the hook never fired.

## Failure modes

- **Hook missing** — the widget falls back to the empty state ("Open Claude Code").
- **`jq` not on PATH** — the inner pipeline silently fails; the file is never updated. The hook backgrounds the write so the user's status bar is never blocked.
- **No write access to `/sdcard/Download/`** — same outcome. Termux storage permissions must be granted (`termux-setup-storage`).

## Keep this doc in sync

If you change the injected block's behavior, the output schema, or the backup convention, update this file. If you delete the hook entirely, mark this doc accordingly.
