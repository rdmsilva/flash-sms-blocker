#!/usr/bin/env bash
#
# Installs build/flash-sms-blocker.apk (from ./build.sh + ./sign.sh) on the
# phone via adb, then re-enables the accessibility service — Android disables
# it on every app update — and verifies it came back.
#
# Usage:  ./install.sh          auto: Wi-Fi connection if one exists, else USB
#         ./install.sh usb      USB only
#         ./install.sh wifi     Wi-Fi only; bootstraps from USB (adb tcpip +
#                               connect) or from $PHONE_IP if no Wi-Fi
#                               connection exists yet

set -euo pipefail
source "$(dirname "$0")/common.sh"

TARGET="${1:-auto}"
case "$TARGET" in auto|usb|wifi) ;; *) echo "Usage: $0 [auto|usb|wifi]"; exit 1;; esac

[ -f "$APK" ] || { echo "No APK at $APK — run ./build.sh && ./sign.sh first (or ./deploy.sh)."; exit 1; }

A11Y_SERVICE="com.flashblocker/com.flashblocker.FlashSmsAccessibilityService"

usb_serial()  { adb devices -l | awk '$2 == "device" && /usb:/       {print $1; exit}'; }
wifi_serial() { adb devices -l | awk '$2 == "device" && $1 ~ /:[0-9]+$/ {print $1; exit}'; }

SERIAL=""
case "$TARGET" in
  usb)
    SERIAL="$(usb_serial)"
    [ -n "$SERIAL" ] || { echo "No USB device (check the cable / USB debugging)."; exit 1; }
    ;;
  wifi|auto)
    SERIAL="$(wifi_serial)"
    if [ -z "$SERIAL" ] && [ "$TARGET" = "auto" ]; then
      SERIAL="$(usb_serial)"
    fi
    if [ -z "$SERIAL" ]; then
      # No Wi-Fi connection yet: bootstrap one. The phone's IP comes from
      # $PHONE_IP, or from the phone itself if it is on USB.
      IP="${PHONE_IP:-}"
      USB="$(usb_serial)"
      if [ -z "$IP" ] && [ -n "$USB" ]; then
        IP="$(adb -s "$USB" shell ip addr show wlan0 | tr -d '\r' \
              | awk '/inet / {sub(/\/.*/, "", $2); print $2; exit}')"
      fi
      [ -n "$IP" ] || { echo "No Wi-Fi connection. Plug the USB once or set PHONE_IP=<phone ip>."; exit 1; }
      if [ -n "$USB" ]; then
        echo "== adb tcpip 5555 (via USB) =="
        adb -s "$USB" tcpip 5555
        sleep 2
      fi
      CONNECT_OUT="$(adb connect "$IP:5555")"
      echo "$CONNECT_OUT"
      case "$CONNECT_OUT" in *connected*) ;; *)
        echo "adb connect $IP:5555 failed (after a phone reboot, plug the USB once)."; exit 1;;
      esac
      SERIAL="$IP:5555"
    fi
    ;;
esac

echo "== install ($SERIAL) =="
adb -s "$SERIAL" install -r "$APK"

# An app update always disables its accessibility service — turn it back on.
echo "== re-enable accessibility service =="
adb -s "$SERIAL" shell settings put secure enabled_accessibility_services "$A11Y_SERVICE"
adb -s "$SERIAL" shell settings put secure accessibility_enabled 1
sleep 2
if adb -s "$SERIAL" shell dumpsys accessibility | grep -q 'Flash SMS Blocker'; then
  echo "Installed and protection active."
else
  echo "WARNING: installed, but the accessibility service did not come back —"
  echo "re-enable it in the app (status card) or in the accessibility settings."
  exit 1
fi
