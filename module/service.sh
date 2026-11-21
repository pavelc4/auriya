#!/system/bin/sh
# Auriya - Background Daemon Service Runner

MODDIR="${MODDIR:-${0%/*}}"
[ "$MODDIR" = "$0" ] && MODDIR="/data/adb/modules/auriya"

MODULE_CONFIG="${MODULE_CONFIG:-/data/adb/.config/auriya}"
LOGDIR="${LOGDIR:-/data/adb/auriya}"
SOCK="/dev/socket/auriya.sock"

# 1. Wait for Android Boot Completion
wait_for_boot() {
  if [ "$(getprop sys.boot_completed)" != "1" ]; then
    until [ "$(getprop sys.boot_completed)" = "1" ]; do
      sleep 2
    done
    sleep 3
  fi
}

# 2. Removal Guard (only runs after boot completed so PackageManager is up)
check_removal() {
  if [ -f "$MODDIR/remove" ] || [ -f "/data/adb/modules_update/auriya/remove" ]; then
    log -t auriya "Module marked for removal, executing cleanup..."
    [ -f "$MODDIR/uninstall.sh" ] && sh "$MODDIR/uninstall.sh"
    exit 0
  fi
}

# 3. Apply SELinux Policy Rules
apply_sepolicy() {
  local rule_file="$MODDIR/sepolicy.rule"
  [ -f "$rule_file" ] || return 0

  if [ -x /data/adb/ksu/bin/ksud ]; then
    /data/adb/ksu/bin/ksud sepolicy apply "$rule_file" >/dev/null 2>&1 || true
  elif [ -x /data/adb/ksud ]; then
    /data/adb/ksud sepolicy apply "$rule_file" >/dev/null 2>&1 || true
  elif command -v ksud >/dev/null 2>&1; then
    ksud sepolicy apply "$rule_file" >/dev/null 2>&1 || true
  elif command -v magiskpolicy >/dev/null 2>&1; then
    magiskpolicy --live --apply "$rule_file" >/dev/null 2>&1 || true
  elif [ -x /data/adb/ap/bin/magiskpolicy ]; then
    /data/adb/ap/bin/magiskpolicy --live --apply "$rule_file" >/dev/null 2>&1 || true
  elif [ -x /data/adb/magisk/magiskpolicy ]; then
    /data/adb/magisk/magiskpolicy --live --apply "$rule_file" >/dev/null 2>&1 || true
  fi
}

# 4. Resolve Daemon Binary Path
resolve_daemon_bin() {
  for candidate in \
    "$MODDIR/system/bin/auriya" \
    "/data/adb/ksu/bin/auriya" \
    "/data/adb/ap/bin/auriya" \
    "/system/bin/auriya"; do
    if [ -x "$candidate" ]; then
      echo "$candidate"
      return 0
    fi
  done

  if command -v auriya >/dev/null 2>&1; then
    command -v auriya
    return 0
  fi

  echo "$MODDIR/system/bin/auriya"
}

# 5. Stop Running Processes
stop_proc() {
  local proc_name="$1"
  local pids
  pids=$(pidof "$proc_name" 2>/dev/null || pgrep -x "$proc_name" 2>/dev/null || true)
  pids=$(echo "$pids" | grep -v "^$$$" | tr '\n' ' ' || true)
  [ -z "${pids// /}" ] && return 0

  kill -TERM $pids 2>/dev/null || true
  for _ in 1 2 3; do
    kill -0 $pids 2>/dev/null || return 0
    sleep 1
  done
  kill -KILL $pids 2>/dev/null || true
}

# 6. Ensure PATH Symlinks for KernelSU & APatch
setup_symlinks() {
  for bin_dir in /data/adb/ksu/bin /data/adb/ap/bin; do
    if [ -d "$bin_dir" ]; then
      ln -sf "$MODDIR/system/bin/auriya" "$bin_dir/auriya"
      if [ -f "$MODDIR/system/bin/auriyactl" ]; then
        chmod 0755 "$MODDIR/system/bin/auriyactl" 2>/dev/null || true
        ln -sf "$MODDIR/system/bin/auriyactl" "$bin_dir/auriyactl"
      fi
    fi
  done
}

# Main Execution Flow
main() {
  wait_for_boot
  check_removal

  mkdir -p "$MODULE_CONFIG" "$LOGDIR"
  chmod 0755 "$MODULE_CONFIG" "$LOGDIR"

  apply_sepolicy

  AURIYA_BIN="$(resolve_daemon_bin)"
  if [ ! -f "$AURIYA_BIN" ] && [ ! -L "$AURIYA_BIN" ]; then
    log -t auriya "ERROR: Daemon binary not found: $AURIYA_BIN"
    exit 1
  fi
  chmod 0755 "$AURIYA_BIN" 2>/dev/null || true

  setup_symlinks
  stop_proc auriya
  rm -f "$SOCK"

  log -t auriya "Starting Auriya daemon from $AURIYA_BIN..."
  export RUST_LOG=info
  export RUST_BACKTRACE=1

  if command -v setsid >/dev/null 2>&1; then
    setsid "$AURIYA_BIN" >> "$LOGDIR/daemon.log" 2>&1 &
  else
    nohup "$AURIYA_BIN" >> "$LOGDIR/daemon.log" 2>&1 &
  fi

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
}

main "$@"
