#!/data/data/com.termux/files/usr/bin/bash
# Build Claude Dash widget APK on-device (Termux, no gradle).
set -euo pipefail

PROJ="$(cd "$(dirname "$0")" && pwd)"
PKG="com.claudedash.widget"
APK_NAME="${APK_NAME:-ClaudeDash-2.26.apk}"
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
SDK_JAR="${SDK_JAR:-$HOME/.cache/android-sdk/android-34/android.jar}"
[[ -f "$SDK_JAR" ]] || { echo "Real android.jar missing at $SDK_JAR. Run setup_sdk.sh first."; exit 1; }

BUILD="$PROJ/build"
GEN="$BUILD/gen"
CLASSES="$BUILD/classes"
DEXDIR="$BUILD/dex"
RES_ZIP="$BUILD/res.zip"
UNSIGNED="$BUILD/app-unsigned.apk"
ALIGNED="$BUILD/app-aligned.apk"
OUT_APK="$BUILD/$APK_NAME"
KEYSTORE="$PROJ/debug.keystore"

rm -rf "$BUILD"
mkdir -p "$GEN" "$CLASSES" "$DEXDIR" "$BUILD"

echo "==> 1/7 aapt2 compile (res)"
aapt2 compile --dir "$PROJ/app/res" -o "$RES_ZIP"

echo "==> 2/7 aapt2 link (apk + R.java)"
aapt2 link \
  -o "$UNSIGNED" \
  -I "$SDK_JAR" \
  --manifest "$PROJ/app/AndroidManifest.xml" \
  --java "$GEN" \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  "$RES_ZIP"

echo "==> 3/7 kotlinc (Kotlin -> classes)"
mapfile -t KT_FILES < <(find "$PROJ/app/src" -name '*.kt')
mapfile -t JAVA_FILES < <(find "$GEN" -name '*.java')
kotlinc -classpath "$SDK_JAR" -d "$CLASSES" -jvm-target 11 \
  -nowarn \
  "${KT_FILES[@]}" "${JAVA_FILES[@]}"

echo "==> 4/7 d8 (classes + kotlin-stdlib -> dex)"
mapfile -t CLASS_FILES < <(find "$CLASSES" -name '*.class')
KOTLIN_STDLIB="$PREFIX/opt/kotlin/lib/kotlin-stdlib.jar"
[[ -f "$KOTLIN_STDLIB" ]] || { echo "kotlin-stdlib.jar missing at $KOTLIN_STDLIB"; exit 1; }
d8 --lib "$SDK_JAR" --min-api 26 --output "$DEXDIR" \
   "$KOTLIN_STDLIB" "${CLASS_FILES[@]}"

echo "==> 5/7 inject classes.dex into apk"
cp "$UNSIGNED" "$BUILD/app-with-dex.apk"
( cd "$DEXDIR" && zip -uj "$BUILD/app-with-dex.apk" classes.dex )

echo "==> 6/7 zipalign"
zipalign -p -f 4 "$BUILD/app-with-dex.apk" "$ALIGNED"

if [[ ! -f "$KEYSTORE" ]]; then
  echo "==> generating debug.keystore"
  keytool -genkey -v \
    -keystore "$KEYSTORE" \
    -storepass android -keypass android \
    -alias androiddebugkey \
    -dname "CN=Android Debug,O=Android,C=US" \
    -keyalg RSA -keysize 2048 -validity 10000
fi

echo "==> 7/7 apksigner sign"
apksigner sign \
  --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey \
  --min-sdk-version 26 \
  --out "$OUT_APK" \
  "$ALIGNED"

echo "==> verify"
apksigner verify "$OUT_APK" && echo "signature ok"

DEST_DIR="/sdcard/Download"
[[ -d "$DEST_DIR" ]] || DEST_DIR="$HOME/storage/downloads"
[[ -d "$DEST_DIR" ]] || { echo "Aucun Download accessible — APK reste dans $OUT_APK"; exit 0; }

cp "$OUT_APK" "$DEST_DIR/"
echo
echo "✅ APK : $DEST_DIR/$APK_NAME"
ls -lh "$DEST_DIR/$APK_NAME"
