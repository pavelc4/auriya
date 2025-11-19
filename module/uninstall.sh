#!/system/bin/sh

CONFIG_DIR="/data/adb/.config/auriya"
LOG_DIR="/data/adb/auriya"

ui_print ""
ui_print "============================================"
ui_print "      Uninstalling Auriya Daemon            "
ui_print "============================================"
ui_print ""

ui_print ">> Stopping daemon..."
pkill -KILL auriya 2>/dev/null
sleep 1
ui_print "   [OK] Done"

ui_print ">> Removing symlinks..."
rm -f /data/adb/ksu/bin/auriya /data/adb/ap/bin/auriya
ui_print "   [OK] Done"

ui_print ">> Removing logs..."
[ -d "$LOG_DIR" ] && rm -rf "$LOG_DIR" && ui_print "   [OK] Removed"

ui_print ">> Configuration:"
if [ -d "$CONFIG_DIR" ]; then
    ui_print "   Preserved at: $CONFIG_DIR"
    ui_print "   To remove: su -c 'rm -rf $CONFIG_DIR'"
else
    ui_print "   [OK] No config found"
fi

ui_print ""
ui_print "============================================"
ui_print "               Auriya Uninstalled           "
ui_print "============================================"
ui_print ""
