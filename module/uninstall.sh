#!/system/bin/sh

_status() {
  echo "$1"
}

_kill_by_name() {
  local name="$1"
  local pid
  pid=$(pidof "$name" 2>/dev/null)
  [ -z "$pid" ] && return 0
  kill -TERM "$pid" 2>/dev/null
  local i=0
  while [ $i -lt 5 ]; do
    pidof "$name" >/dev/null 2>&1 || return 0
    sleep 1
    i=$((i + 1))
  done
  kill -KILL "$pid" 2>/dev/null
}

_pm_uninstall() {
  local pkg="$1"
  local attempt=1

  pm list packages "$pkg" 2>/dev/null | grep -qx "package:$pkg" || return 0

  while [ "$attempt" -le 3 ]; do
    _status "Removing $pkg (attempt $attempt/3). Do not reboot."
    local output="/data/local/tmp/auriya-uninstall-$$-$attempt.log"
    local timeout=15
    (
      pm uninstall "$pkg" >"$output" 2>&1
    ) &
    local pid=$!

    while [ "$timeout" -gt 0 ]; do
      if ! kill -0 "$pid" 2>/dev/null; then
        wait "$pid" 2>/dev/null
        grep -q "Success" "$output" 2>/dev/null && {
          rm -f "$output"
          return 0
        }
        pm list packages "$pkg" 2>/dev/null | grep -qx "package:$pkg" || {
          rm -f "$output"
          return 0
        }
        break
      fi
      sleep 1
      timeout=$((timeout - 1))
    done

    kill "$pid" 2>/dev/null
    wait "$pid" 2>/dev/null
    echo "Uninstall attempt $attempt failed for $pkg: $(cat "$output" 2>/dev/null)"
    rm -f "$output"
    attempt=$((attempt + 1))
    [ "$attempt" -le 3 ] && sleep 1
  done

  return 1
}

_status "Auriya uninstall started. Do not reboot."
_kill_by_name auriya

COMPANION_PID=$(pgrep -f AuriyaSysMon 2>/dev/null)
if [ -n "$COMPANION_PID" ]; then
  kill -TERM "$COMPANION_PID" 2>/dev/null
  for i in 1 2 3; do
    pgrep -f AuriyaSysMon >/dev/null 2>&1 || break
    sleep 1
  done
  pgrep -f AuriyaSysMon >/dev/null 2>&1 && kill -KILL "$COMPANION_PID" 2>/dev/null
fi

am force-stop dev.auriya.app 2>/dev/null
am force-stop dev.auriya.app.debug 2>/dev/null
am force-stop dev.auriya.service 2>/dev/null
_pm_uninstall dev.auriya.app || true
_pm_uninstall dev.auriya.app.debug || true
_pm_uninstall dev.auriya.service || true

_status "Apps removed. Cleaning module data."
rm -f /dev/socket/auriya.sock
rm -rf /data/adb/.config/auriya
rm -rf /data/adb/auriya

rm -f /data/adb/ksu/bin/auriya
rm -f /data/adb/ap/bin/auriya
rm -f /data/adb/ksu/bin/auriyactl
rm -f /data/adb/ap/bin/auriyactl

for seconds in 3 2 1; do
  _status "Finishing Auriya uninstall: ${seconds}s. Do not reboot."
  sleep 1
done
_status "Auriya uninstall complete. Safe to reboot."

exit 0
