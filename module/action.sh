#!/system/bin/sh
MODPATH="/data/adb/modules/auriya"
BIN="$MODPATH/system/bin/auriyactl"

if [ -x "$BIN" ]; then
  "$BIN" restart
else
  echo "auriyactl not found at $BIN"
  exit 1
fi
