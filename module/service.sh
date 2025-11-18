#!/system/bin/sh

MODDIR="${0%/*}"
MODPATH="/data/adb/modules/auriya"
MODULE_CONFIG="/data/adb/.config/auriya"
AURIYA_BIN="$MODDIR/system/bin/auriya"
CFG="$MODULE_CONFIG/auriya.toml"
LOGDIR="/data/adb/auriya"
AURIYA_LOG="$LOGDIR/daemon.log"
SOCK="/dev/socket/auriya.sock"


mkdir -p "$MODULE_CONFIG" "$LOGDIR"
chmod 0771 "$MODULE_CONFIG" "$LOGDIR"

until [ "$(getprop sys.boot_completed)" = "1" ]; do
  sleep 2
done
sleep 3

chmod 0755 "$AURIYA_BIN"
if pidof auriya >/dev/null 2>&1; then
  killall -TERM auriya 2>/dev/null
  for i in 1 2 3; do
    pidof auriya >/dev/null 2>&1 || break
    sleep 1
  done
fi
[ -S "$SOCK" ] && rm -f "$SOCK"

if [ -f "$AURIYA_LOG" ] && [ "$(stat -c%s "$AURIYA_LOG" 2>/dev/null || echo 0)" -gt 1048576 ]; then
  mv "$AURIYA_LOG" "$AURIYA_LOG.1"
fi

echo "=== Auriya starting at $(date) ===" >> "$AURIYA_LOG"

if command -v stdbuf >/dev/null 2>&1; then
  stdbuf -oL -eL "$AURIYA_BIN" --packages "$CFG" 2>&1 | while IFS= read -r line; do
    log -t auriya "$line"
    echo "$line" >> "$AURIYA_LOG"
  done &
else
  "$AURIYA_BIN" --packages "$CFG" 2>&1 | while IFS= read -r line; do
    log -t auriya "$line"
    echo "$line" >> "$AURIYA_LOG"
  done &
fi

sleep 1
echo PING | socat - UNIX-CONNECT:"$SOCK" >/dev/null 2>&1 || log -t auriya "IPC not ready"

echo "Auriya started (PIPE PID: $!)" >> "$AURIYA_LOG"
