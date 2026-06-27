#!/system/bin/sh

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
  local timeout=15
  (
    pm uninstall "$pkg" 2>/dev/null
  ) &
  local pid=$!
  while [ $timeout -gt 0 ]; do
    kill -0 $pid 2>/dev/null || return 0
    sleep 1
    timeout=$((timeout - 1))
  done
  kill $pid 2>/dev/null
}


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

rm -f /dev/socket/auriya.sock
rm -rf /data/adb/.config/auriya
rm -rf /data/adb/auriya

rm -f /data/adb/ksu/bin/auriya
rm -f /data/adb/ap/bin/auriya
rm -f /data/adb/ksu/bin/auriyactl
rm -f /data/adb/ap/bin/auriyactl

am force-stop dev.auriya.app 2>/dev/null
am force-stop dev.auriya.app.debug 2>/dev/null
_pm_uninstall dev.auriya.app
_pm_uninstall dev.auriya.app.debug

exit 0
