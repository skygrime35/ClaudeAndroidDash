#!/data/data/com.termux/files/usr/bin/bash
# Schedule the account-wide API usage refresh every 15 min via termux-job-scheduler.
# RUN THIS FROM HOST TERMUX (not inside the PRoot distro).
# Requires the Termux:API app + `pkg install termux-api`, and a proot-distro named "ubuntu".
set -euo pipefail

DISTRO="${CLAUDE_DASH_DISTRO:-ubuntu}"
API_SCRIPT="/data/data/com.termux/files/home/Projects/ClaudeAndroidDash/parser/claude_usage_api.sh"
WRAPPER="$HOME/.local/bin/claude-dash-api-tick.sh"

mkdir -p "$HOME/.local/bin"

cat > "$WRAPPER" <<EOF
#!/data/data/com.termux/files/usr/bin/bash
# Enter the PRoot distro (where ~/.claude credentials live) and refresh the widget JSON.
exec /data/data/com.termux/files/usr/bin/proot-distro login "$DISTRO" -- bash "$API_SCRIPT"
EOF
chmod +x "$WRAPPER"

if ! command -v termux-job-scheduler >/dev/null 2>&1; then
  echo "termux-job-scheduler not found. Install Termux:API app then: pkg install termux-api" >&2
  exit 1
fi

# 900000 ms = 15 min, the minimum Android JobScheduler allows for periodic jobs.
termux-job-scheduler \
  --job-id 4243 \
  --period-ms 900000 \
  --persisted true \
  --script "$WRAPPER"

echo "Scheduled API refresh every 15 min. Inspect with: termux-job-scheduler -p"
echo "Cancel with: termux-job-scheduler --cancel --job-id 4243"
