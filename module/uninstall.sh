#!/system/bin/sh

# Stop Daemon
killall -9 auriya 2>/dev/null

# Stop Companion Service (app_process with AuriyaSysMon nice-name)
if pgrep -f AuriyaSysMon >/dev/null 2>&1; then
    pkill -TERM -f AuriyaSysMon 2>/dev/null
    for i in 1 2 3; do
        pgrep -f AuriyaSysMon >/dev/null 2>&1 || break
        sleep 1
    done
    pkill -KILL -f AuriyaSysMon 2>/dev/null
fi

rm -f /dev/socket/auriya.sock

# User app (Compose UI — both release and debug variant)
pm uninstall dev.auriya.app 2>/dev/null
pm uninstall dev.auriya.app.debug 2>/dev/null

# Config and Logs
rm -rf /data/adb/.config/auriya
rm -rf /data/adb/auriya

# Symlinks (KSU/APatch)
rm -f /data/adb/ksu/bin/auriya
rm -f /data/adb/ap/bin/auriya
rm -f /data/adb/ksu/bin/auriyactl
rm -f /data/adb/ap/bin/auriyactl

exit 0
