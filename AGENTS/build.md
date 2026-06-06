# Module: build pipeline

> No-Gradle, on-device, Termux ARM64.

## Toolchain

| Binary | Source |
|---|---|
| `aapt2`, `apksigner`, `zipalign`, `d8` | `pkg install` (Termux repos) |
| `kotlinc` | `pkg install kotlin` (Kotlin 2.x) |
| `kotlin-stdlib.jar` | `$PREFIX/opt/kotlin/lib/kotlin-stdlib.jar` (shipped with the `kotlin` package) |
| `android.jar` (API 34) | `setup_sdk.sh` fetches it from `dl.google.com/android/repository/platform-34-ext7_r02.zip` into `~/.cache/android-sdk/android-34/` |

⚠️ **Do NOT use** `$PREFIX/share/aapt/android.jar` (the jar packaged by Termux). It is missing all `android:*` framework attributes and breaks `aapt2 link`.

## Pipeline (`build.sh`)

1. `aapt2 compile --dir app/res -o build/res.zip`
2. `aapt2 link -I <real-android.jar> --manifest app/AndroidManifest.xml --java build/gen -o build/app-unsigned.apk build/res.zip`
3. `kotlinc -classpath <real-android.jar> -d build/classes <kotlin sources> <generated R.java>`
4. `d8 --lib <real-android.jar> --output build/dex <kotlin-stdlib.jar> <classes>`
5. `zip -uj build/app-with-dex.apk build/dex/classes.dex`
6. `zipalign -p -f 4 ... build/app-aligned.apk`
7. `apksigner sign --ks debug.keystore --ks-pass pass:android ... -o build/<APK>.apk`
8. `cp build/<APK>.apk /sdcard/Download/`

Step 4 is the one most often broken: forgetting `kotlin-stdlib.jar` produces a runtime `ClassNotFoundException: kotlin.jvm.internal.Intrinsics`.

## Idempotency

`build.sh` wipes `build/` at the start. Re-running it is safe and reproduces a deterministic APK (up to signing nondeterminism).

## Version bump checklist

When making a user-visible change worth a new install:

- [ ] `app/AndroidManifest.xml` — increment `android:versionCode` (integer) AND `android:versionName` (string)
- [ ] `build.sh` — bump `APK_NAME="${APK_NAME:-ClaudeDash-X.Y.apk}"` to match `versionName`

If `versionCode` stays the same, the user must uninstall the previous APK before installing.

## Keystore

`debug.keystore` is generated on first build via `keytool` with the password `android` (Android-debug convention). Do not ship this for production — it is a debug keystore committed for convenience.

## Known noise

`d8` emits many `Info: Unexpected error while reading kotlin.Metadata` lines when Kotlin 2.x metadata meets the version of `d8` shipped by Termux. These are non-fatal — the resulting dex runs fine. Ignore.

## Keep this doc in sync

If you change the toolchain, the pipeline stages, or the version-bump procedure, update this file.
