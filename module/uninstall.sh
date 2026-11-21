#!/system/bin/sh
# Auriya - Clean Uninstallation Script

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
  local attempt=1
  local max_attempts=3

  pm list packages "$pkg" 2>/dev/null | grep -qx "package:$pkg" || return 0

  while [ "$attempt" -le "$max_attempts" ]; do
    ui_status "Removing $pkg (attempt $attempt/$max_attempts)..."
    local output="/data/local/tmp/auriya-uninstall-$$-$attempt.log"
    local timeout=15

    ( pm uninstall "$pkg" >"$output" 2>&1 ) &
    local pid=$!

    while [ "$timeout" -gt 0 ]; do
      if ! kill -0 "$pid" 2>/dev/null; then
        wait "$pid" 2>/dev/null
        if grep -q "Success" "$output" 2>/dev/null || ! pm list packages "$pkg" 2>/dev/null | grep -qx "package:$pkg"; then
          rm -f "$output"
          return 0
        fi
        break
      fi
      sleep 1
      timeout=$((timeout - 1))
    done

    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
    echo "Uninstall attempt $attempt failed for $pkg: $(cat "$output" 2>/dev/null)"
    rm -f "$output"
    attempt=$((attempt + 1))
    [ "$attempt" -le "$max_attempts" ] && sleep 1
  done

  return 1
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
