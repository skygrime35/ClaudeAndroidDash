# Module: parser/

> Python fallback. Used only when the user has no live Claude Code session writing via the statusline hook.

## What lives here

| Path | Role |
|---|---|
| `parser/claude_usage.py` | Walks `~/.claude/projects/**/*.jsonl`, aggregates assistant turns, writes `/sdcard/Download/claude_usage.json` with `source: "legacy"` |
| `parser/install_cron.sh` | Registers a `termux-job-scheduler` job (id 4242) that re-runs the parser every 5 min |
| `parser/limits_default.json` | Per-tier token caps used when no override exists at `~/.config/claude-dash/limits.json` |

## When to use vs. statusline

Prefer the statusline path always. The Python parser ships estimates, not the real Anthropic counters. Reasons to fall back here:

- No Claude Code session is open (the hook never fires without one).
- The user is offline / running pre-deprecation tooling.
- Debugging the widget without launching `claude`.

## Algorithm

- **5h rolling block** — ccusage rule. A new block starts at the first event after a gap of ≥ 5 hours. Block lasts 5 hours from that start. Counts Sonnet-only tokens.
- **Weekly window** — from the most recent Thursday 20:00 America/Los_Angeles (= Friday 04:00 UTC) up to now. DST drift is ignored (good-enough for a display).
- **Cost** — public per-model pricing constants at the top of the file. Update if Anthropic changes pricing.

## Output schema

`source: "legacy"`. `JsonFileUsageRepository.parseLegacy` knows this shape. If you change the schema, update the parser there too.

## Limits resolution

1. Read `~/.config/claude-dash/limits.json` if present.
2. Else `parser/limits_default.json`.
3. The selected `tier` chooses base caps; an `overrides` block can override per-bucket.

There is no in-app UI to pick a tier any more (the SettingsActivity was removed in 2.1). The parser falls back to the `tier` field of `limits_default.json` (currently `max5x`). If you want a different tier, hand-edit `~/.config/claude-dash/limits.json`.

## Keep this doc in sync

If you change the algorithm, schema, pricing constants, or limits file location, update the relevant section.
