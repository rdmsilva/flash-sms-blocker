#!/usr/bin/env bash
#
# Signs the APK produced by ./build.sh → build/flash-sms-blocker.apk.
#
# Usage:  ./sign.sh            throwaway debug key (testing only)
#         ./sign.sh release    durable release keystore; apksigner prompts for
#                              the password on the terminal (it never touches
#                              env/shell history — needs a real TTY)
#
# Release keystore path: $RELEASE_KEYSTORE (default ~/.keystores/flash-sms-blocker.jks).
# If it has more than one alias, set RELEASE_KEY_ALIAS. The keystore and its
# password are never committed to the repository. To create the keystore
# (once), see README, section "Assinando o release".
#
# The phone only accepts updates signed with the same key as the installed
# APK — a debug build cannot update a release install.

set -euo pipefail
source "$(dirname "$0")/common.sh"

MODE="${1:-debug}"
case "$MODE" in debug|release) ;; *) echo "Usage: $0 [debug|release]"; exit 1;; esac

[ -f "$UNSIGNED_APK" ] || { echo "Nothing to sign ($UNSIGNED_APK) — run ./build.sh first."; exit 1; }
find_sdk

if [ "$MODE" = "release" ]; then
  RELEASE_KS="${RELEASE_KEYSTORE:-$HOME/.keystores/flash-sms-blocker.jks}"
  if [ ! -f "$RELEASE_KS" ]; then
    echo "Release keystore not found: $RELEASE_KS"
    echo "Create it (see README, 'Assinando o release') or set RELEASE_KEYSTORE."
    exit 1
  fi
  echo "== sign (release keystore: $RELEASE_KS) =="
  ALIAS_ARGS=()
  [ -n "${RELEASE_KEY_ALIAS:-}" ] && ALIAS_ARGS=(--ks-key-alias "$RELEASE_KEY_ALIAS")
  # apksigner asks for the password interactively — it is never stored.
  "$BT/apksigner" sign --ks "$RELEASE_KS" "${ALIAS_ARGS[@]}" \
    --out "$APK" "$UNSIGNED_APK"
else
  echo "== local debug keystore =="
  KS="$OUT/debug.keystore"
  [ -f "$KS" ] || keytool -genkeypair -keyalg RSA -keysize 2048 -validity 10000 \
    -keystore "$KS" -alias debug -storepass android -keypass android \
    -dname "CN=Flash SMS Blocker Debug" 2>/dev/null

  echo "== sign (debug) =="
  "$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --out "$APK" "$UNSIGNED_APK"
fi

echo
echo "Signed APK: $APK ($MODE) — install it: ./install.sh [auto|usb|wifi]"
