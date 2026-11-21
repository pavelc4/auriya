#!/system/bin/sh
# Auriya - Clean Uninstallation Script

export PATH="/system/bin:/system/xbin:$PATH"

ui_status() {
  echo "$1"
}

kill_process() {
  local proc_name="$1"
  local pid
  pid=$(pidof "$proc_name" 2>/dev/null || true)
  [ -z "$pid" ] && return 0

  kill -TERM "$pid" 2>/dev/null || true
  for _ in 1 2 3 4 5; do
    pidof "$proc_name" >/dev/null 2>&1 || return 0
    sleep 1
  done
  kill -KILL "$pid" 2>/dev/null || true
}

uninstall_app() {
  local pkg="$1"
  pm list packages "$pkg" 2>/dev/null | grep -q "package:$pkg" || return 0
  ui_status "Removing $pkg..."
  am force-stop "$pkg" >/dev/null 2>&1 || true
  pm uninstall "$pkg" >/dev/null 2>&1 || pm uninstall --user 0 "$pkg" >/dev/null 2>&1 || cmd package uninstall "$pkg" >/dev/null 2>&1 || true
}

cleanup_module_data() {
  ui_status "Cleaning up socket, configs, and telemetry logs..."
  rm -f /dev/socket/auriya.sock
  rm -rf /data/adb/.config/auriya
  rm -rf /data/adb/auriya

  # Clean symlinks
  rm -f /data/adb/ksu/bin/auriya \
        /data/adb/ap/bin/auriya \
        /data/adb/ksu/bin/auriyactl \
        /data/adb/ap/bin/auriyactl

  # Clean temp files
  rm -f /data/local/tmp/auriya* 2>/dev/null || true
}

main() {
  ui_status "Auriya uninstall started. Do not reboot."

  kill_process auriya

  am force-stop dev.auriya.app 2>/dev/null || true
  am force-stop dev.auriya.app.debug 2>/dev/null || true

  uninstall_app dev.auriya.app || true
  uninstall_app dev.auriya.app.debug || true

  cleanup_module_data

  for sec in 3 2 1; do
    ui_status "Finishing Auriya uninstall: ${sec}s. Do not reboot."
    sleep 1
  done

  ui_status "Auriya uninstall complete. Safe to reboot."
  exit 0
}

main "$@"
