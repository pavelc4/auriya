#!/usr/bin/env bash
#
# Cargo target runner for aarch64-linux-android.
#
# Cargo invokes this as: adb-runner.sh <test-binary> [harness args...]
# It pushes the cross-compiled binary to a connected Android device,
# executes it there, streams the output back, and propagates the exit
# code so `cargo test` reflects the real on-device result.
#
# Set AURIYA_TEST_ROOT=1 to run the binary via `su` (needed for tests
# that read root-owned sysfs/proc nodes). Default runs as the adb shell
# user, which is enough for pure-logic and self-owned-process tests.

BIN="$1"
shift

SERIAL="${ANDROID_SERIAL:-$(adb get-serialno 2>/dev/null)}"
if [ -z "$SERIAL" ]; then
  echo "adb-runner: no Android device connected" >&2
  exit 1
fi

REMOTE="/data/local/tmp/auriya-test-$(basename "$BIN")"

if ! adb -s "$SERIAL" push "$BIN" "$REMOTE" >/dev/null; then
  echo "adb-runner: push failed" >&2
  exit 1
fi
adb -s "$SERIAL" shell chmod 0755 "$REMOTE" >/dev/null 2>&1

if [ "${AURIYA_TEST_ROOT:-0}" = "1" ]; then
  CMD="su -c '$REMOTE $*'"
else
  CMD="$REMOTE $*"
fi

# Run on device. Append an exit-code marker because not every adb shell
# forwards the remote exit status.
OUT="$(adb -s "$SERIAL" shell "cd /data/local/tmp && $CMD; echo __ADB_EXIT__:\$?")"

# Echo everything except the marker line.
printf '%s\n' "$OUT" | grep -v '^__ADB_EXIT__:'

CODE="$(printf '%s\n' "$OUT" | sed -n 's/^__ADB_EXIT__:\([0-9][0-9]*\).*/\1/p' | tail -1)"

# Best-effort cleanup of the pushed binary.
adb -s "$SERIAL" shell rm -f "$REMOTE" >/dev/null 2>&1

exit "${CODE:-1}"
