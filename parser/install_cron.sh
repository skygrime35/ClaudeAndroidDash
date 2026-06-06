#!/data/data/com.termux/files/usr/bin/bash
# Schedule the Claude usage parser to run every 5 minutes via termux-job-scheduler.
# Requires the Termux:API app + `pkg install termux-api`.
set -euo pipefail

SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
PARSER="$SELF_DIR/claude_usage.py"
WRAPPER="$HOME/.local/bin/claude-dash-tick.sh"

mkdir -p "$HOME/.local/bin" "$HOME/.config/claude-dash"

cat > "$WRAPPER" <<EOF
#!/data/data/com.termux/files/usr/bin/bash
exec /data/data/com.termux/files/usr/bin/python3 "$PARSER" "\$@"
EOF
chmod +x "$WRAPPER"

if ! command -v termux-job-scheduler >/dev/null 2>&1; then
  echo "termux-job-scheduler not found. Install Termux:API app then: pkg install termux-api" >&2
  exit 1
fi

termux-job-scheduler \
  --job-id 4242 \
  --period-ms 300000 \
  --persisted true \
  --script "$WRAPPER"

echo "Scheduled. Inspect with: termux-job-scheduler -p"
