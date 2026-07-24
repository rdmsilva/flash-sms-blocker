#!/usr/bin/env bash
#
# Compiles Flash SMS Blocker WITHOUT Gradle (no wrapper is committed):
# aapt → javac → d8 → aapt package → zipalign → build/app-aligned.apk (UNSIGNED).
# Requirements: Android SDK (platform android-34 + build-tools) and JDK 17.
#
# The unsigned APK cannot be installed — next steps:
#   ./sign.sh [debug|release]        sign it  → build/flash-sms-blocker.apk
#   ./install.sh [auto|usb|wifi]     push it to the phone
#   ./deploy.sh                      all three in one go

set -euo pipefail
source "$(dirname "$0")/common.sh"

[ $# -eq 0 ] || { echo "Usage: $0    (no arguments — see sign.sh / install.sh / deploy.sh)"; exit 1; }

find_sdk
rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"
cd "$ROOT"

echo "== 1. aapt: generate R.java =="
"$BT/aapt" package -f -m -J "$OUT/gen" \
  -M app/src/main/AndroidManifest.xml -S app/src/main/res -I "$PLATFORM"

echo "== 2. javac =="
javac -source 1.8 -target 1.8 -classpath "$PLATFORM" -d "$OUT/classes" \
  app/src/main/java/com/flashblocker/*.java \
  "$OUT"/gen/com/flashblocker/R.java

echo "== 3. d8 -> classes.dex =="
"$BT/d8" --release --min-api 24 --lib "$PLATFORM" \
  --output "$OUT/dex" "$OUT"/classes/com/flashblocker/*.class

echo "== 4. aapt: package resources =="
"$BT/aapt" package -f \
  -M app/src/main/AndroidManifest.xml -S app/src/main/res -I "$PLATFORM" \
  -F "$OUT/app-unsigned.apk"

echo "== 5. add classes.dex =="
( cd "$OUT/dex" && "$BT/aapt" add "$OUT/app-unsigned.apk" classes.dex >/dev/null )

echo "== 6. zipalign =="
"$BT/zipalign" -f 4 "$OUT/app-unsigned.apk" "$UNSIGNED_APK"

echo
echo "Unsigned APK: $UNSIGNED_APK — now sign it: ./sign.sh [debug|release]"
