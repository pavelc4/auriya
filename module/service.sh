#!/system/bin/sh
MODDIR="${MODDIR:-${0%/*}}"
[ "$MODDIR" = "$0" ] && MODDIR="/data/adb/modules/auriya"

MODULE_CONFIG="${MODULE_CONFIG:-/data/adb/.config/auriya}"
AURIYA_BIN="$MODDIR/system/bin/auriya"
COMPANION_APK="$MODDIR/system/etc/auriya/service.apk"
SETTINGS_CFG="$MODULE_CONFIG/settings.toml"
GAMELIST_CFG="$MODULE_CONFIG/gamelist.toml"
STATUS_FILE="$MODULE_CONFIG/system_status"
COMPANION_LOCK="$MODULE_CONFIG/companion.lock"
LOGDIR="${LOGDIR:-/data/adb/auriya}"
COMPANION_LOG="$LOGDIR/companion.log"
SOCK="/dev/socket/auriya.sock"

# 1. Removal Check
if [ -f "$MODDIR/remove" ] || [ -f "/data/adb/modules_update/auriya/remove" ]; then
  log -t auriya "Module marked for removal, executing cleanup..."
  [ -f "$MODDIR/uninstall.sh" ] && sh "$MODDIR/uninstall.sh"
  exit 0
fi

# 2. Wait for Android Boot
if [ "$(getprop sys.boot_completed)" != "1" ]; then
  until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
  done
  sleep 3
fi

# 3. Prerequisites Check
mkdir -p "$MODULE_CONFIG" "$LOGDIR"
chmod 0755 "$MODULE_CONFIG" "$LOGDIR"

if [ ! -f "$AURIYA_BIN" ] || [ ! -f "$COMPANION_APK" ]; then
  log -t auriya "ERROR: Required daemon or companion binary not found"
  exit 1
fi
chmod 0755 "$AURIYA_BIN"

# 4. Stop Existing Instances
stop_proc() {
  local proc_name="$1"
  local pids
  pids=$(pidof "$proc_name" 2>/dev/null || true)
  if [ -z "$pids" ]; then
    pids=$(pgrep -x "$proc_name" 2>/dev/null || true)
  fi
  # Safety: exclude own process and subshells
  pids=$(echo "$pids" | grep -v "^$$$" | tr '\n' ' ' || true)
  [ -z "${pids// /}" ] && return 0

  kill -TERM $pids 2>/dev/null || true
  for _ in 1 2 3; do
    kill -0 $pids 2>/dev/null || return 0
    sleep 1
  done
  kill -KILL $pids 2>/dev/null || true
}

stop_proc AuriyaSysMon
stop_proc auriya

rm -f "$SOCK" "$STATUS_FILE" "$COMPANION_LOCK"

# 5. Companion Log Rotation
if [ -f "$COMPANION_LOG" ]; then
  log_size=$(stat -c%s "$COMPANION_LOG" 2>/dev/null || echo 0)
  if [ "$log_size" -gt 1048576 ]; then
    mv -f "$COMPANION_LOG" "$COMPANION_LOG.old"
  fi
fi

# 6. Launch Companion Service
log -t auriya "Starting companion service..."
nohup app_process -Djava.class.path="$COMPANION_APK" \
  /system/bin --nice-name=AuriyaSysMon \
  dev.auriya.service.Main \
  >>"$COMPANION_LOG" 2>&1 &

for _ in 1 2 3 4 5 6 7 8 9 10; do
  [ -f "$STATUS_FILE" ] && break
  sleep 1
done

if [ ! -f "$STATUS_FILE" ]; then
  log -t auriya "ERROR: Companion failed to initialize $STATUS_FILE within 10s"
  exit 1
fi

# 7. Launch Auriya Daemon
log -t auriya "Starting Auriya daemon..."
export RUST_LOG=info
export RUST_BACKTRACE=1

nohup "$AURIYA_BIN" --settings "$SETTINGS_CFG" --gamelist "$GAMELIST_CFG" >/dev/null 2>&1 &

for _ in 1 2 3 4 5; do
  if pidof auriya >/dev/null 2>&1 && [ -S "$SOCK" ]; then
    log -t auriya "Auriya daemon and IPC socket ready"
    exit 0
  fi
  sleep 1
done

if pidof auriya >/dev/null 2>&1; then
  log -t auriya "Auriya daemon running (socket pending)"
  exit 0
fi

log -t auriya "ERROR: Failed to start Auriya daemon"
exit 1
