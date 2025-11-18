#!/system/bin/sh


ui_print ">> Installing Auriya"

mkdir -p "$MODPATH/system/bin"
ARCH="$(getprop ro.product.cpu.abi)"
case "$ARCH" in
  arm64-v8a|arm64*|aarch64*) BIN_SRC="$TMPDIR/auriya-aarch64";;
  armeabi-v7a|armeabi*|armv7*) BIN_SRC="$TMPDIR/auriya-armv7";;
  *) BIN_SRC="$TMPDIR/auriya-aarch64";;
esac

[ -f "$TMPDIR/auriya" ] && BIN_SRC="$TMPDIR/auriya"

if [ -f "$BIN_SRC" ]; then
  cp -fp "$BIN_SRC" "$MODPATH/system/bin/auriya"
  chmod 0755 "$MODPATH/system/bin/auriya"
  ui_print "  + Binary installed"
else
  ui_print "  ! Binary not found in zip"
fi

CFGDIR="/data/adb/.config/auriya"
CFGPATH="$CFGDIR/auriya.toml"
mkdir -p "$CFGDIR"
chmod 0771 "$CFGDIR"

if [ ! -f "$CFGPATH" ]; then
  if [ -f "$TMPDIR/auriya.toml" ]; then
    cp -fp "$TMPDIR/auriya.toml" "$CFGPATH"
    chmod 0644 "$CFGPATH"
    ui_print "  + Default config created at $CFGPATH"
  else
    ui_print "  ! No default config found in zip"
  fi
else
  ui_print "  = Keeping existing config at $CFGPATH"
fi

ui_print ">> Install done"
