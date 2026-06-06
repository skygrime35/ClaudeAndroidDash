#!/usr/bin/env python3
"""Parse Claude Code JSONL logs and emit a usage snapshot.

Reads every assistant turn from ~/.claude/projects/**/*.jsonl, computes:
  - active 5h rolling block (Sonnet-only)
  - current week window (Thursday 20:00 PT = Friday 04:00 UTC -> now)
  - estimated USD cost
Writes /sdcard/Download/claude_usage.json so the Android widget can read it.
"""
from __future__ import annotations

import glob
import json
import os
import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path

HOME = Path(os.environ.get("HOME", "/data/data/com.termux/files/home"))
PROJECTS = HOME / ".claude" / "projects"
LIMITS_FILE = HOME / ".config" / "claude-dash" / "limits.json"
DEFAULT_LIMITS = Path(__file__).resolve().parent / "limits_default.json"

OUT_DIR = Path("/sdcard/Download")
OUT_FILE = OUT_DIR / "claude_usage.json"
FALLBACK_OUT = HOME / "storage" / "downloads" / "claude_usage.json"

BLOCK_DURATION = timedelta(hours=5)

PRICING = {
    "sonnet": {"input": 3.0, "output": 15.0, "cache_create": 3.75, "cache_read": 0.30},
    "opus":   {"input": 15.0, "output": 75.0, "cache_create": 18.75, "cache_read": 1.50},
    "haiku":  {"input": 1.0, "output": 5.0, "cache_create": 1.25, "cache_read": 0.10},
}


def parse_ts(s: str) -> datetime:
    if s.endswith("Z"):
        s = s[:-1] + "+00:00"
    return datetime.fromisoformat(s).astimezone(timezone.utc)


def model_family(model: str) -> str:
    if "sonnet" in model:
        return "sonnet"
    if "opus" in model:
        return "opus"
    if "haiku" in model:
        return "haiku"
    return "other"


def cost(family: str, u: dict) -> float:
    p = PRICING.get(family)
    if not p:
        return 0.0
    return (
        u.get("input", 0) * p["input"] / 1_000_000
        + u.get("output", 0) * p["output"] / 1_000_000
        + u.get("cache_create", 0) * p["cache_create"] / 1_000_000
        + u.get("cache_read", 0) * p["cache_read"] / 1_000_000
    )


def load_limits() -> dict:
    src = LIMITS_FILE if LIMITS_FILE.exists() else DEFAULT_LIMITS
    with open(src) as f:
        cfg = json.load(f)
    tier = cfg.get("tier", "max5x")
    base = cfg["tiers"].get(tier, cfg["tiers"]["max5x"])
    ov = cfg.get("overrides", {}) or {}
    merged = dict(base)
    for k, v in ov.items():
        if isinstance(v, (int, float)) and v > 0:
            merged[k] = v
    merged["tier"] = tier
    return merged


def iter_assistant_events():
    """Yield (timestamp_utc, family, usage_dict) for every assistant turn."""
    for path in glob.glob(str(PROJECTS / "**" / "*.jsonl"), recursive=True):
        try:
            with open(path, "r", errors="ignore") as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        ev = json.loads(line)
                    except json.JSONDecodeError:
                        continue
                    if ev.get("type") != "assistant":
                        continue
                    msg = ev.get("message") or {}
                    usage = msg.get("usage") or {}
                    if not usage:
                        continue
                    model = msg.get("model") or ""
                    ts_raw = ev.get("timestamp")
                    if not ts_raw:
                        continue
                    try:
                        ts = parse_ts(ts_raw)
                    except Exception:
                        continue
                    u = {
                        "input": int(usage.get("input_tokens", 0) or 0),
                        "output": int(usage.get("output_tokens", 0) or 0),
                        "cache_create": int(usage.get("cache_creation_input_tokens", 0) or 0),
                        "cache_read": int(usage.get("cache_read_input_tokens", 0) or 0),
                    }
                    yield ts, model_family(model), u
        except (OSError, IOError):
            continue


def find_active_block(events_sorted) -> tuple[datetime | None, datetime | None]:
    """Apply ccusage's 5h rolling rule: a block starts at the first event after a >=5h gap."""
    block_start = None
    last_ts = None
    for ts, _, _ in events_sorted:
        if last_ts is None or (ts - last_ts) >= BLOCK_DURATION:
            block_start = ts
        last_ts = ts
    if block_start is None:
        return None, None
    block_end = block_start + BLOCK_DURATION
    now = datetime.now(timezone.utc)
    if now >= block_end:
        return None, None
    return block_start, block_end


def week_start_utc(now: datetime) -> datetime:
    """Thursday 20:00 America/Los_Angeles == Friday 04:00 UTC (ignoring DST drift)."""
    candidate = now.replace(hour=4, minute=0, second=0, microsecond=0)
    while candidate.weekday() != 4 or candidate > now:
        candidate -= timedelta(days=1)
    return candidate


def empty_usage() -> dict:
    return {"input": 0, "output": 0, "cache_create": 0, "cache_read": 0, "total": 0}


def add(dst: dict, u: dict) -> None:
    for k in ("input", "output", "cache_create", "cache_read"):
        dst[k] += u[k]
    dst["total"] = dst["input"] + dst["output"] + dst["cache_create"] + dst["cache_read"]


def pct(used: int, limit: int) -> float:
    if limit <= 0:
        return 0.0
    return round(min(100.0, 100.0 * used / limit), 2)


def write_atomic(path: Path, data: dict) -> bool:
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(".json.tmp")
        with open(tmp, "w") as f:
            json.dump(data, f, separators=(",", ":"))
        os.replace(tmp, path)
        return True
    except OSError as e:
        sys.stderr.write(f"write {path}: {e}\n")
        return False


def main() -> int:
    now = datetime.now(timezone.utc)
    limits = load_limits()

    events = list(iter_assistant_events())
    events.sort(key=lambda e: e[0])

    block_start, block_end = find_active_block(events)
    block_sonnet = empty_usage()
    block_cost = 0.0
    if block_start is not None:
        for ts, fam, u in events:
            if ts < block_start or ts >= block_end:
                continue
            if fam == "sonnet":
                add(block_sonnet, u)
                block_cost += cost(fam, u)

    wk_start = week_start_utc(now)
    wk_sonnet = empty_usage()
    wk_total = empty_usage()
    wk_sonnet_cost = 0.0
    wk_total_cost = 0.0
    for ts, fam, u in events:
        if ts < wk_start:
            continue
        add(wk_total, u)
        wk_total_cost += cost(fam, u)
        if fam == "sonnet":
            add(wk_sonnet, u)
            wk_sonnet_cost += cost(fam, u)

    block_info = {"active": False}
    if block_start is not None:
        remaining = block_end - now
        rem_min = max(0, int(remaining.total_seconds() // 60))
        block_info = {
            "active": True,
            "start": block_start.isoformat().replace("+00:00", "Z"),
            "end": block_end.isoformat().replace("+00:00", "Z"),
            "remaining_minutes": rem_min,
            "sonnet_tokens": block_sonnet,
            "sonnet_pct_used": pct(block_sonnet["total"], limits["block_5h_sonnet"]),
            "cost_usd": round(block_cost, 4),
        }

    payload = {
        "updated_at": now.isoformat().replace("+00:00", "Z"),
        "block_5h": block_info,
        "week": {
            "start": wk_start.isoformat().replace("+00:00", "Z"),
            "sonnet": {
                "tokens": wk_sonnet,
                "pct_used": pct(wk_sonnet["total"], limits["week_sonnet"]),
                "cost_usd": round(wk_sonnet_cost, 4),
            },
            "all_models": {
                "tokens": wk_total,
                "pct_used": pct(wk_total["total"], limits["week_total"]),
                "cost_usd": round(wk_total_cost, 4),
            },
        },
        "limits": limits,
    }

    ok = write_atomic(OUT_FILE, payload)
    if not ok:
        ok = write_atomic(FALLBACK_OUT, payload)
    if not ok:
        json.dump(payload, sys.stdout)
        return 1
    sys.stdout.write(f"wrote {OUT_FILE if OUT_FILE.exists() else FALLBACK_OUT}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
