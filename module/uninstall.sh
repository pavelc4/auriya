#!/system/bin/sh
ui_print " Uninstalling Auriya module..."

rm -f /data/adb/ksu/bin/auriya
rm -f /data/adb/ap/bin/auriya
rm -rf /data/adb/.config/auriya

ui_print " Auriya module removed completely."
