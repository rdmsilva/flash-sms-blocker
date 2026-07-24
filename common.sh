#!/usr/bin/env bash
# Shared definitions for build.sh / sign.sh / install.sh — source it, don't run it.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$ROOT/build"
UNSIGNED_APK="$OUT/app-aligned.apk"     # ./build.sh output (not installable yet)
APK="$OUT/flash-sms-blocker.apk"        # ./sign.sh output (what gets installed)

# Locates the Android SDK; sets SDK, BT (newest build-tools) and PLATFORM.
find_sdk() {
  SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
  [ -d "$SDK" ] || { echo "Android SDK not found. Set ANDROID_HOME."; exit 1; }
  BT="$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)"
  BT="${BT%/}"
  [ -n "$BT" ] || { echo "Missing build-tools in the SDK."; exit 1; }
  PLATFORM="$SDK/platforms/android-34/android.jar"
  [ -f "$PLATFORM" ] || { echo "Missing platform android-34 in the SDK."; exit 1; }
}
