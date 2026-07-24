#!/usr/bin/env bash
#
# One-shot pipeline: ./build.sh → ./sign.sh → ./install.sh.
#
# Usage:  ./deploy.sh [debug|release] [auto|usb|wifi]
#         ./deploy.sh                  debug build, auto-picked device
#         ./deploy.sh release wifi     what goes on Rafael's phone (release-
#                                      signed install); apksigner asks for the
#                                      keystore password, so run it on a real
#                                      terminal

set -euo pipefail
cd "$(dirname "$0")"

MODE="${1:-debug}"
TARGET="${2:-auto}"
case "$MODE" in debug|release) ;; *) echo "Usage: $0 [debug|release] [auto|usb|wifi]"; exit 1;; esac
case "$TARGET" in auto|usb|wifi) ;; *) echo "Usage: $0 [debug|release] [auto|usb|wifi]"; exit 1;; esac

./build.sh
./sign.sh "$MODE"
./install.sh "$TARGET"
