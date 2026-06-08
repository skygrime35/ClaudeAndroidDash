# Module: parser/

> Off-session refreshers. Used when no live Claude Code session is writing via the statusline hook on **this** device (e.g. the user runs Claude elsewhere and only taps the widget here).

## What lives here

| Path | Role |
|---|---|
| `parser/claude_usage_api.sh` | **Primary off-session path.** Reads the OAuth token from `~/.claude/.credentials.json`, refreshes it if expired, makes a 1-token Haiku request, maps the `anthropic-ratelimit-unified-*` response headers, writes `/sdcard/Download/claude_usage.json` with `source: "api"`. Account-scoped, 1:1 with claude.ai. Runs **inside the PRoot distro** (that's where the credentials live). |
| `parser/install_cron_api.sh` | Registers a `termux-job-scheduler` job (id 4243, every 15 min) whose wrapper does `proot-distro login ubuntu -- bash claude_usage_api.sh`. **Run from host Termux.** |
| `parser/claude_usage.py` | Legacy estimator. Walks `~/.claude/projects/**/*.jsonl`, aggregates assistant turns, writes `source: "legacy"`. No longer wired to the widget refresh button (kept as an offline estimate). |
| `parser/install_cron.sh` | Registers a `termux-job-scheduler` job (id 4242) that re-runs the **Python** parser every 5 min |
| `parser/limits_default.json` | Per-tier token caps used by the Python parser when no override exists at `~/.config/claude-dash/limits.json` |

## The API path (`claude_usage_api.sh`)

This is what the widget's refresh button now triggers (see `adapter/refresh/TermuxRefreshTrigger`). It needs **no live session** because the 5h/7d rate limits are account-scoped and returned in every API response's headers:

```
anthropic-ratelimit-unified-5h-utilization  -> five_hour.used_pct  (×100, rounded)
anthropic-ratelimit-unified-5h-reset        -> five_hour.resets_at
anthropic-ratelimit-unified-7d-utilization  -> seven_day.used_pct
anthropic-ratelimit-unified-7d-reset        -> seven_day.resets_at
```

**Token refresh**: the OAuth access token expires ~every 8h. The script refreshes it via the `refreshToken` against `https://console.anthropic.com/v1/oauth/token` (Claude Code client_id `9d1c250a-e61b-44d9-88ed-5944d1962f5e`) when expired or on a 401, then rewrites `~/.claude/.credentials.json` atomically (preserving all other fields). Without this the widget would die ~8h after the last real Claude Code use on this device.

**PRoot bridge**: Claude Code and its credentials live inside the PRoot distro (`ubuntu`), but the widget can only fire host-Termux intents. Both the refresh button and the cron therefore wrap the script in `proot-distro login ubuntu -- bash …`. The repo home is bind-mounted at the same path inside and outside the distro, so the script path is identical. Override the distro name with `CLAUDE_DASH_DISTRO`.

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

The Python parser writes `source: "legacy"` (`JsonFileUsageRepository.parseLegacy`). `claude_usage_api.sh` writes `source: "api"`, reusing the exact `statusline` shape (`five_hour`/`seven_day` with `used_pct`+`resets_at`; `model`/`context`/`cost` omitted), so `parseStatusline` handles it. If you change either schema, update the matching parser branch.

## Limits resolution

1. Read `~/.config/claude-dash/limits.json` if present.
2. Else `parser/limits_default.json`.
3. The selected `tier` chooses base caps; an `overrides` block can override per-bucket.

There is no in-app UI to pick a tier any more (the SettingsActivity was removed in 2.1). The parser falls back to the `tier` field of `limits_default.json` (currently `max5x`). If you want a different tier, hand-edit `~/.config/claude-dash/limits.json`.

## Keep this doc in sync

If you change the algorithm, schema, pricing constants, or limits file location, update the relevant section.
