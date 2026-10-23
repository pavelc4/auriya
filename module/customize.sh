#!/system/bin/sh
MODPATH="${MODPATH:-/data/adb/modules/auriya}"
MODULE_CONFIG="${MODULE_CONFIG:-/data/adb/.config/auriya}"
LOG_DIR="${LOG_DIR:-/data/adb/auriya}"

case $ARCH in
arm64) SOURCE_ARCH="aarch64" ;;
*)
  ui_print "! Unsupported architecture: $ARCH (arm64 only)"
  abort
  ;;
esac

ui_print "    ___               _               "
ui_print "   /   |  __  _______(_)__  _____     "
ui_print '  / /| | / / / / ___/ / / / / __ \    '
ui_print " / ___ |/ /_/ / /  / / /_/ / /_/ /    "
ui_print '/_/  |_|\__,_/_/  /_/\__, /\__,_/     '
ui_print "                    /____/            "
ui_print ""
ui_print "- Device: $(getprop ro.product.model) ($ARCH)"
ui_print "- Android: $(getprop ro.build.version.release) (SDK $(getprop ro.build.version.sdk))"

# 1. Extraction
ui_print "- Extracting files..."
if ! unzip -oq "$ZIPFILE" -d "$MODPATH" 2>/dev/null; then
  if command -v busybox >/dev/null 2>&1; then
    busybox unzip -oq "$ZIPFILE" -d "$MODPATH" || abort "! Extraction failed"
  else
    abort "! Extraction failed"
  fi
fi

LIBS_DIR="$MODPATH/libs/$SOURCE_ARCH"
DAEMON_SRC="$LIBS_DIR/auriya"
CLI_SRC="$LIBS_DIR/auriyactl"
COMPANION_SRC="$MODPATH/libs/companion/service.apk"
USER_APP_SRC="$MODPATH/libs/companion/auriya-app.apk"
CHECKSUMS_FILE="$LIBS_DIR/checksums.sha256"
DAEMON_SHA="$LIBS_DIR/auriya.sha256"

[ ! -f "$DAEMON_SRC" ] && abort "! Daemon binary not found for $SOURCE_ARCH"
[ ! -f "$COMPANION_SRC" ] && abort "! Companion service APK not found"

# 2. SHA256 Integrity Verification
verify_sha() {
  local file="$1"
  local expected="$2"
  local actual=""

  if command -v sha256sum >/dev/null 2>&1; then
    actual=$(sha256sum "$file" | cut -d' ' -f1)
  elif command -v shasum >/dev/null 2>&1; then
    actual=$(shasum -a 256 "$file" | cut -d' ' -f1)
  else
    ui_print "! sha256 tool unavailable, skipping checksum"
    return 0
  fi

  if [ "$actual" != "$expected" ]; then
    ui_print ""
    ui_print "! CHECKSUM MISMATCH: $(basename "$file")"
    ui_print "  Expected: ${expected:0:16}..."
    ui_print "  Actual:   ${actual:0:16}..."
    ui_print ""
    ui_print "The module file is corrupted or incomplete."
    ui_print "Download again from:"
    ui_print "https://github.com/pavelc4/auriya/releases"
    ui_print ""
    abort "! Installation aborted (integrity check failed)"
  fi
}

ui_print "- Verifying binary integrity..."
if [ -f "$CHECKSUMS_FILE" ]; then
  EXPECTED_DAEMON_SHA=$(grep "auriya$" "$CHECKSUMS_FILE" | cut -d' ' -f1)
  [ -n "$EXPECTED_DAEMON_SHA" ] && verify_sha "$DAEMON_SRC" "$EXPECTED_DAEMON_SHA"

  if [ -f "$CLI_SRC" ]; then
    EXPECTED_CLI_SHA=$(grep "auriyactl$" "$CHECKSUMS_FILE" | cut -d' ' -f1)
    [ -n "$EXPECTED_CLI_SHA" ] && verify_sha "$CLI_SRC" "$EXPECTED_CLI_SHA"
  fi
  ui_print "  SHA256 checksums verified"
elif [ -f "$DAEMON_SHA" ]; then
  EXPECTED_DAEMON_SHA=$(cut -d' ' -f1 "$DAEMON_SHA")
  [ -n "$EXPECTED_DAEMON_SHA" ] && verify_sha "$DAEMON_SRC" "$EXPECTED_DAEMON_SHA"
  ui_print "  SHA256 checksum verified"
fi

# 3. Deploy Binaries & Companion Service
mkdir -p "$MODPATH/system/bin" "$MODPATH/system/etc/auriya"

cp -f "$DAEMON_SRC" "$MODPATH/system/bin/auriya"
chmod 0755 "$MODPATH/system/bin/auriya"

HAS_CLI=false
if [ -f "$CLI_SRC" ]; then
  cp -f "$CLI_SRC" "$MODPATH/system/bin/auriyactl"
  chmod 0755 "$MODPATH/system/bin/auriyactl"
  HAS_CLI=true
fi

cp -f "$COMPANION_SRC" "$MODPATH/system/etc/auriya/service.apk"
chmod 0644 "$MODPATH/system/etc/auriya/service.apk"

# 4. Install User Manager App
if [ -f "$USER_APP_SRC" ] && command -v pm >/dev/null 2>&1; then
  ui_print "- Installing Auriya Manager..."
  pm install -r -g "$USER_APP_SRC" >/dev/null 2>&1 || ui_print "! Manager install skipped"
fi

rm -rf "$MODPATH/libs"

# 5. Initialize Configs & Directories
mkdir -p "$MODULE_CONFIG" "$LOG_DIR"
chmod 0755 "$MODULE_CONFIG" "$LOG_DIR"

[ ! -f "$MODULE_CONFIG/settings.toml" ] && mv "$MODPATH/settings.toml" "$MODULE_CONFIG/settings.toml"
[ ! -f "$MODULE_CONFIG/gamelist.toml" ] && mv "$MODPATH/gamelist.toml" "$MODULE_CONFIG/gamelist.toml"
rm -f "$MODPATH/settings.toml" "$MODPATH/gamelist.toml"

# 6. KernelSU / APatch PATH Integration
for bin_dir in /data/adb/ksu/bin /data/adb/ap/bin; do
  if [ -d "$bin_dir" ]; then
    ln -sf "$MODPATH/system/bin/auriya" "$bin_dir/auriya"
    [ "$HAS_CLI" = true ] && ln -sf "$MODPATH/system/bin/auriyactl" "$bin_dir/auriyactl"
  fi
done

# 7. Permissions
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/system/bin/auriya" 0 0 0755
[ "$HAS_CLI" = true ] && set_perm "$MODPATH/system/bin/auriyactl" 0 0 0755
[ -f "$MODPATH/action.sh" ] && set_perm "$MODPATH/action.sh" 0 0 0755
[ -f "$MODPATH/uninstall.sh" ] && set_perm "$MODPATH/uninstall.sh" 0 0 0755

ui_print "- Installation complete"
