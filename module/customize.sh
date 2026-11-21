#!/system/bin/sh
# Auriya - Module Customization / Installer Script

MODPATH="${MODPATH:-/data/adb/modules/auriya}"
MODULE_CONFIG="${MODULE_CONFIG:-/data/adb/.config/auriya}"
LOG_DIR="${LOG_DIR:-/data/adb/auriya}"

validate_arch() {
  case "$ARCH" in
    arm64)
      SOURCE_ARCH="aarch64"
      ;;
    *)
      ui_print "! Unsupported architecture: $ARCH (arm64 only)"
      abort
      ;;
  esac
}

print_banner() {
  ui_print "    ___               _               "
  ui_print "   /   |  __  _______(_)__  _____     "
  ui_print '  / /| | / / / / ___/ / / / / __ \    '
  ui_print " / ___ |/ /_/ / /  / / /_/ / /_/ /    "
  ui_print '/_/  |_|\__,_/_/  /_/\__, /\__,_/     '
  ui_print "                    /____/            "
  ui_print ""
  ui_print "- Device: $(getprop ro.product.model) ($ARCH)"
  ui_print "- Android: $(getprop ro.build.version.release) (SDK $(getprop ro.build.version.sdk))"
}

extract_payload() {
  ui_print "- Extracting files..."
  if ! unzip -oq "$ZIPFILE" -d "$MODPATH" 2>/dev/null; then
    if command -v busybox >/dev/null 2>&1; then
      busybox unzip -oq "$ZIPFILE" -d "$MODPATH" || abort "! Extraction failed"
    else
      abort "! Extraction failed"
    fi
  fi
}

verify_sha256() {
  local target_file="$1"
  local expected_hash="$2"
  local actual_hash=""

  if command -v sha256sum >/dev/null 2>&1; then
    actual_hash=$(sha256sum "$target_file" | cut -d' ' -f1)
  elif command -v shasum >/dev/null 2>&1; then
    actual_hash=$(shasum -a 256 "$target_file" | cut -d' ' -f1)
  else
    ui_print "! sha256 tool unavailable, skipping checksum"
    return 0
  fi

  if [ "$actual_hash" != "$expected_hash" ]; then
    ui_print ""
    ui_print "! CHECKSUM MISMATCH: $(basename "$target_file")"
    ui_print "  Expected: ${expected_hash:0:16}..."
    ui_print "  Actual:   ${actual_hash:0:16}..."
    ui_print ""
    ui_print "The module file is corrupted or incomplete."
    ui_print "Download again from: https://github.com/pavelc4/auriya/releases"
    ui_print ""
    abort "! Installation aborted (integrity check failed)"
  fi
}

verify_binaries() {
  ui_print "- Verifying binary integrity..."
  local checksums_file="$LIBS_DIR/checksums.sha256"
  local daemon_sha="$LIBS_DIR/auriya.sha256"

  if [ -f "$checksums_file" ]; then
    local expected_daemon_sha expected_cli_sha
    expected_daemon_sha=$(grep "auriya$" "$checksums_file" | cut -d' ' -f1)
    [ -n "$expected_daemon_sha" ] && verify_sha256 "$DAEMON_SRC" "$expected_daemon_sha"

    if [ -f "$CLI_SRC" ]; then
      expected_cli_sha=$(grep "auriyactl$" "$checksums_file" | cut -d' ' -f1)
      [ -n "$expected_cli_sha" ] && verify_sha256 "$CLI_SRC" "$expected_cli_sha"
    fi
    ui_print "  SHA256 checksums verified"
  elif [ -f "$daemon_sha" ]; then
    local expected_daemon_sha
    expected_daemon_sha=$(cut -d' ' -f1 "$daemon_sha")
    [ -n "$expected_daemon_sha" ] && verify_sha256 "$DAEMON_SRC" "$expected_daemon_sha"
    ui_print "  SHA256 checksum verified"
  fi
}

deploy_binaries() {
  mkdir -p "$MODPATH/system/bin" "$MODPATH/system/etc/auriya"
  cp -f "$DAEMON_SRC" "$MODPATH/system/bin/auriya"
  chmod 0755 "$MODPATH/system/bin/auriya"

  HAS_CLI=false
  if [ -f "$CLI_SRC" ]; then
    cp -f "$CLI_SRC" "$MODPATH/system/bin/auriyactl"
    chmod 0755 "$MODPATH/system/bin/auriyactl"
    HAS_CLI=true
  fi
}

install_manager_app() {
  if [ -f "$USER_APP_SRC" ] && command -v pm >/dev/null 2>&1; then
    ui_print "- Installing Auriya Manager..."
    pm install -r -d -g "$USER_APP_SRC" >/dev/null 2>&1 || ui_print "! Manager install skipped"
  fi
}

setup_configs() {
  mkdir -p "$MODULE_CONFIG" "$LOG_DIR"
  chmod 0755 "$MODULE_CONFIG" "$LOG_DIR"

  local config_src="$MODPATH"
  if [ -d "$MODPATH/config" ]; then
    config_src="$MODPATH/config"
  elif [ -d "$MODPATH/toml" ]; then
    config_src="$MODPATH/toml"
  fi

  for cfg in settings.toml gamelist.toml blacklist.toml; do
    if [ ! -f "$MODULE_CONFIG/$cfg" ] && [ -f "$config_src/$cfg" ]; then
      mv "$config_src/$cfg" "$MODULE_CONFIG/$cfg"
    fi
  done

  rm -rf "$MODPATH/config" "$MODPATH/toml"
  rm -f "$MODPATH/settings.toml" "$MODPATH/gamelist.toml" "$MODPATH/blacklist.toml"
}

setup_symlinks() {
  local perm_modpath="/data/adb/modules/auriya"
  for bin_dir in /data/adb/ksu/bin /data/adb/ap/bin; do
    if [ -d "$bin_dir" ]; then
      ln -sf "$perm_modpath/system/bin/auriya" "$bin_dir/auriya"
      [ "$HAS_CLI" = true ] && ln -sf "$perm_modpath/system/bin/auriyactl" "$bin_dir/auriyactl"
    fi
  done
}

set_permissions_and_sepolicy() {
  set_perm_recursive "$MODPATH" 0 0 0755 0644
  set_perm "$MODPATH/service.sh" 0 0 0755
  set_perm "$MODPATH/system/bin/auriya" 0 0 0755
  [ "$HAS_CLI" = true ] && set_perm "$MODPATH/system/bin/auriyactl" 0 0 0755
  [ -f "$MODPATH/action.sh" ] && set_perm "$MODPATH/action.sh" 0 0 0755
  [ -f "$MODPATH/uninstall.sh" ] && set_perm "$MODPATH/uninstall.sh" 0 0 0755

  local rule_file="$MODPATH/sepolicy.rule"
  if [ -f "$rule_file" ]; then
    if [ -x /data/adb/ksu/bin/ksud ]; then
      /data/adb/ksu/bin/ksud sepolicy apply "$rule_file" >/dev/null 2>&1 || true
    elif [ -x /data/adb/ksud ]; then
      /data/adb/ksud sepolicy apply "$rule_file" >/dev/null 2>&1 || true
    elif command -v ksud >/dev/null 2>&1; then
      ksud sepolicy apply "$rule_file" >/dev/null 2>&1 || true
    elif command -v magiskpolicy >/dev/null 2>&1; then
      magiskpolicy --live --apply "$rule_file" >/dev/null 2>&1 || true
    elif [ -x /data/adb/ap/bin/magiskpolicy ]; then
      /data/adb/ap/bin/magiskpolicy --live --apply "$rule_file" >/dev/null 2>&1 || true
    elif [ -x /data/adb/magisk/magiskpolicy ]; then
      /data/adb/magisk/magiskpolicy --live --apply "$rule_file" >/dev/null 2>&1 || true
    fi
  fi
}

# Main Installer Entry Point
validate_arch
print_banner
extract_payload

LIBS_DIR="$MODPATH/libs/$SOURCE_ARCH"
DAEMON_SRC="$LIBS_DIR/auriya"
CLI_SRC="$LIBS_DIR/auriyactl"
USER_APP_SRC="$MODPATH/libs/app/auriya-app.apk"

[ ! -f "$DAEMON_SRC" ] && abort "! Daemon binary not found for $SOURCE_ARCH"

verify_binaries
deploy_binaries
install_manager_app
setup_configs
setup_symlinks
set_permissions_and_sepolicy
rm -rf "$MODPATH/libs"

ui_print "- Installation complete"
