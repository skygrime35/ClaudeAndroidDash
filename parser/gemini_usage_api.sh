#!/data/data/com.termux/files/usr/bin/bash
# Garantit que python3 (Termux) est dans le PATH, y compris via `proot-distro login`.
export PATH="/data/data/com.termux/files/usr/bin:$PATH"
python3 /data/data/com.termux/files/home/Projects/AndroidApp/ClaudeAndroidDash/parser/gemini_usage_api.py
