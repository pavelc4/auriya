#!/system/bin/sh
MODDIR="${0%/*}"
MODPATH="/data/adb/modules/auriya"
MODULE_CONFIG="/data/adb/.config/auriya"
AURIYA_BIN="$MODPATH/system/bin/auriya"
COMPANION_APK="$MODPATH/system/etc/auriya/service.apk"
SETTINGS_CFG="$MODULE_CONFIG/settings.toml"
GAMELIST_CFG="$MODULE_CONFIG/gamelist.toml"
STATUS_FILE="$MODULE_CONFIG/system_status"
COMPANION_LOCK="$MODULE_CONFIG/companion.lock"
LOGDIR="/data/adb/auriya"
AURIYA_LOG="$LOGDIR/daemon.log"
COMPANION_LOG="$LOGDIR/companion.log"
SOCK="/dev/socket/auriya.sock"

mkdir -p "$MODULE_CONFIG" "$LOGDIR"
chmod 0755 "$MODULE_CONFIG" "$LOGDIR"

if [ "$(getprop sys.boot_completed)" != "1" ]; then
  until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
  done
  sleep 5
fi

chmod 0755 "$AURIYA_BIN"

if [ ! -f "$AURIYA_BIN" ]; then
  log -t auriya "ERROR: Binary not found at $AURIYA_BIN"
  echo "[$(date)] ERROR: Binary not found" >>"$AURIYA_LOG"
  exit 1
fi

if [ ! -f "$COMPANION_APK" ]; then
  log -t auriya "ERROR: Companion APK not found at $COMPANION_APK"
  echo "[$(date)] ERROR: Companion APK not found" >>"$AURIYA_LOG"
  exit 1
fi

if [ ! -f "$SETTINGS_CFG" ]; then
  log -t auriya "ERROR: Settings config not found at $SETTINGS_CFG"
  echo "[$(date)] ERROR: Settings config not found" >>"$AURIYA_LOG"
  exit 1
fi

if [ ! -f "$GAMELIST_CFG" ]; then
  log -t auriya "ERROR: Gamelist config not found at $GAMELIST_CFG"
  echo "[$(date)] ERROR: Gamelist config not found" >>"$AURIYA_LOG"
  exit 1
fi

# Stop any previous companion. The flock will block us otherwise.
if pgrep -f AuriyaSysMon >/dev/null 2>&1; then
  log -t auriya "Stopping existing companion service..."
  pkill -TERM -f AuriyaSysMon 2>/dev/null
  for i in 1 2 3; do
    pgrep -f AuriyaSysMon >/dev/null 2>&1 || break
    sleep 1
  done
  pkill -KILL -f AuriyaSysMon 2>/dev/null
fi

if pidof auriya >/dev/null 2>&1; then
  log -t auriya "Stopping existing daemon..."
  killall -TERM auriya 2>/dev/null
  for i in 1 2 3; do
    pidof auriya >/dev/null 2>&1 || break
    sleep 1
  done

  if pidof auriya >/dev/null 2>&1; then
    killall -KILL auriya 2>/dev/null
    sleep 1
  fi
fi
[ -S "$SOCK" ] && rm -f "$SOCK"
# Force a fresh status file so the daemon's await loop only succeeds
# once the companion has actually produced fresh data this boot.
rm -f "$STATUS_FILE" "$COMPANION_LOCK"

# Rotate companion log if it grew past 1MB.
if [ -f "$COMPANION_LOG" ]; then
  LOG_SIZE=$(stat -c%s "$COMPANION_LOG" 2>/dev/null || stat -f%z "$COMPANION_LOG" 2>/dev/null || echo 0)
  if [ "$LOG_SIZE" -gt 1048576 ]; then
    [ -f "$COMPANION_LOG.1" ] && rm -f "$COMPANION_LOG.1"
    mv "$COMPANION_LOG" "$COMPANION_LOG.1"
  fi
fi
echo "=== Companion starting at $(date) ===" >>"$COMPANION_LOG"
log -t auriya "Starting Auriya companion service..."

# Launch the headless Kotlin companion via app_process so it inherits
# the system uid handed to us by Magisk's service.d hook.
nohup app_process -Djava.class.path="$COMPANION_APK" \
  /system/bin --nice-name=AuriyaSysMon \
  dev.auriya.service.Main \
  >>"$COMPANION_LOG" 2>&1 &

# Give the companion a couple of seconds to acquire its flock and
# produce the first status snapshot. The daemon also waits up to 10s
# internally so this is belt-and-braces.
for i in 1 2 3 4 5 6 7 8 9 10; do
  if [ -f "$STATUS_FILE" ]; then
    log -t auriya "Companion produced status file"
    break
  fi
  sleep 1
done

if [ ! -f "$STATUS_FILE" ]; then
  log -t auriya "ERROR: Companion did not produce $STATUS_FILE within 10s"
  echo "[$(date)] ERROR: Companion startup timeout" >>"$AURIYA_LOG"
  exit 1
fi

if [ -f "$AURIYA_LOG" ]; then
  LOG_SIZE=$(stat -c%s "$AURIYA_LOG" 2>/dev/null || stat -f%z "$AURIYA_LOG" 2>/dev/null || echo 0)
  if [ "$LOG_SIZE" -gt 1048576 ]; then
    [ -f "$AURIYA_LOG.2" ] && rm -f "$AURIYA_LOG.2"
    [ -f "$AURIYA_LOG.1" ] && mv "$AURIYA_LOG.1" "$AURIYA_LOG.2"
    mv "$AURIYA_LOG" "$AURIYA_LOG.1"
  fi
fi
echo "=== Auriya starting at $(date) ===" >>"$AURIYA_LOG"
log -t auriya "Starting Auriya daemon..."

export RUST_LOG=info
export RUST_BACKTRACE=1

if command -v stdbuf >/dev/null 2>&1; then
  stdbuf -oL -eL "$AURIYA_BIN" --settings "$SETTINGS_CFG" --gamelist "$GAMELIST_CFG" 2>&1 | while IFS= read -r line; do
    log -t auriya "$line"
    echo "$line" >>"$AURIYA_LOG"
  done &
else
  "$AURIYA_BIN" --settings "$SETTINGS_CFG" --gamelist "$GAMELIST_CFG" 2>&1 | while IFS= read -r line; do
    log -t auriya "$line"
    echo "$line" >>"$AURIYA_LOG"
  done &
fi

PIPE_PID=$!
sleep 2

if pidof auriya >/dev/null 2>&1; then
  DAEMON_PID=$(pidof auriya | awk '{print $1}')
  log -t auriya "Daemon started successfully (PID: $DAEMON_PID)"
  echo "Auriya started (Daemon PID: $DAEMON_PID, Pipe PID: $PIPE_PID)" >>"$AURIYA_LOG"
  for i in 1 2 3 4 5; do
    if [ -S "$SOCK" ]; then
      if echo "PING" | nc -U "$SOCK" >/dev/null 2>&1; then
        log -t auriya "IPC socket ready"
        echo "[$(date)] IPC socket ready" >>"$AURIYA_LOG"
        exit 0
      fi
    fi
    sleep 1
  done

  log -t auriya "WARNING: IPC socket not responding"
  echo "[$(date)] WARNING: IPC socket not responding" >>"$AURIYA_LOG"
else
  log -t auriya "ERROR: Failed to start daemon"
  echo "[$(date)] ERROR: Failed to start daemon" >>"$AURIYA_LOG"
  exit 1
fi
