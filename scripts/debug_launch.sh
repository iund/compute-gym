#!/usr/bin/env bash
# Install, launch suspended, and forward a JDWP port for IntelliJ's Remote JVM Debug.
set -euo pipefail

ADB="$HOME/Library/Android/sdk/platform-tools/adb"
PKG="dev.computegym.hello"
PORT="${1:-8700}"

"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" shell am start -D -n "$PKG/.MainActivity"

PID=""
for _ in $(seq 1 20); do
    PID="$("$ADB" shell pidof "$PKG" || true)"
    [ -n "$PID" ] && break
    sleep 0.3
done
if [ -z "$PID" ]; then
    echo "could not find PID for $PKG" >&2
    exit 1
fi

"$ADB" forward "tcp:$PORT" "jdwp:$PID"
echo "PID $PID, JDWP forwarded on localhost:$PORT — attach IntelliJ's Remote JVM Debug now"
