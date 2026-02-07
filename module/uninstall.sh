#!/system/bin/sh

# Stop Daemon
killall -9 auriya 2>/dev/null
rm -f /dev/socket/auriya.sock

# Config and Logs
rm -rf /data/adb/.config/auriya
rm -rf /data/adb/auriya

# Symlinks (KSU/APatch)
rm -f /data/adb/ksu/bin/auriya
rm -f /data/adb/ap/bin/auriya
rm -f /data/adb/ksu/bin/auriyactl
rm -f /data/adb/ap/bin/auriyactl

# Post-fs-data/Service scripts (if any external ones were created, though typically handled by module system)
# We only clean our custom directories and symlinks.

exit 0
