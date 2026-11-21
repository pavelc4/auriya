#!/system/bin/sh
# Auriya - Quick Action Trigger

MODDIR="${0%/*}"
[ "$MODDIR" = "$0" ] && MODDIR="/data/adb/modules/auriya"

for bin in \
  "/system/bin/auriyactl" \
  "/data/adb/ksu/bin/auriyactl" \
  "/data/adb/ap/bin/auriyactl" \
  "$MODDIR/system/bin/auriyactl"; do
  if [ -x "$bin" ]; then
    exec "$bin" restart
  fi
done

if command -v auriyactl >/dev/null 2>&1; then
  exec auriyactl restart
fi

echo "ERROR: auriyactl binary not found" >&2
exit 1
