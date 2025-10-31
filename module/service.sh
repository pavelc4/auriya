#!/system/bin/sh

MODPATH="/data/adb/modules/auriya"
MODULE_CONFIG="/data/adb/.config/auriya"
AURIYA_BIN="$MODPATH/system/bin/auriya"
AURIYA_LOG="$MODULE_CONFIG/auriya.log"

mkdir -p "$MODULE_CONFIG"
chmod 0755 "$MODULE_CONFIG"

# Wait until system ready
until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
done

sleep 5

chmod 0755 "$AURIYA_BIN"

{
    echo "=== Auriya starting at $(date) ==="
    exec "$AURIYA_BIN"
} >> "$AURIYA_LOG" 2>&1 &

echo "Auriya started (PID: $!)"
