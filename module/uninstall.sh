#!/system/bin/sh

# Config and Logs
rm -rf /data/adb/.config/auriya
rm -rf /data/adb/auriya

# Symlinks (KSU/APatch)
rm -f /data/adb/ksu/bin/auriya
rm -f /data/adb/ap/bin/auriya
rm -f /data/adb/ksu/bin/auriya-ctl
rm -f /data/adb/ap/bin/auriya-ctl

# Post-fs-data/Service scripts (if any external ones were created, though typically handled by module system)
# We only clean our custom directories and symlinks.

exit 0
