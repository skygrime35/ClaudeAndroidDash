#!/data/data/com.termux/files/usr/bin/bash
# Fetch a real Android 34 platform jar from Google's official CDN.
set -euo pipefail

CACHE="$HOME/.cache/android-sdk"
JAR="$CACHE/android-34/android.jar"
URL="https://dl.google.com/android/repository/platform-34-ext7_r02.zip"

if [[ -f "$JAR" && $(stat -c %s "$JAR") -gt 20000000 ]]; then
  echo "android.jar already present: $JAR"
  exit 0
fi

mkdir -p "$CACHE"
cd "$CACHE"
echo "Downloading $URL ..."
curl -fL --progress-bar -o platform-34.zip "$URL"
unzip -o platform-34.zip "android-34/android.jar" -d .
echo "Ready: $JAR"
