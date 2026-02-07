#!/system/bin/sh
MODPATH="${MODPATH:-/data/adb/modules/auriya}"
MODULE_CONFIG="/data/adb/.config/auriya"
LOG_DIR="/data/adb/auriya"
case $ARCH in
    arm64) SOURCE_ARCH="aarch64" ;;
    arm)   SOURCE_ARCH="arm" ;;
    x64|x86) SOURCE_ARCH="x64" ;;
    *)
        ui_print "! Unsupported architecture: $ARCH"
        ui_print ""
        ui_print "Auriya supports:"
        ui_print "• arm64 (aarch64) - Modern Android phones"
        ui_print "• arm (armv7) - Older Android phones"
        ui_print "• x64 (x86_64) - Emulators/Chromebooks"
        abort
        ;;
esac

LIBS_DIR="$MODPATH/libs/$SOURCE_ARCH"
DAEMON_BINARY="$LIBS_DIR/auriya"
CLI_BINARY="$LIBS_DIR/auriyactl"
DAEMON_SHA="$LIBS_DIR/auriya.sha256"
CHECKSUMS_FILE="$LIBS_DIR/checksums.sha256"
HAS_CLI=false

make_dir() { [ ! -d "$1" ] && mkdir -p "$1"; }
make_node() { [ ! -f "$2" ] && echo "$1" > "$2"; }

detect_gpu() {
    local gpu=$(getprop ro.hardware.vulkan 2>/dev/null || echo "unknown")
    echo "$gpu" | grep -qi "adreno" && echo "adreno" && return
    echo "$gpu" | grep -qi "mali" && echo "mali" && return
    echo "unknown"
}

ui_print "    ___               _               "
ui_print "   /   |  __  _______(_)__  _____     "
ui_print "  / /| | / / / / ___/ / / / / __ \\    "
ui_print " / ___ |/ /_/ / /  / / /_/ / /_/ /    "
ui_print "/_/  |_|\\__,_/_/  /_/\\__, /\\__,_/     "
ui_print "                    /____/            "
ui_print ""
ui_print "- Auriya Performance Optimizer"
ui_print ""

ui_print "Device: $(getprop ro.product.model) ($ARCH)"
ui_print "Android: $(getprop ro.build.version.release) (SDK $(getprop ro.build.version.sdk))"

GPU_TYPE=$(detect_gpu)
ui_print "GPU: $GPU_TYPE"

[ "$KSU" = "true" ] && ui_print "Root: KernelSU" || ui_print "Root: Magisk"
ui_print ""



ui_print "Installing for $ARCH ($SOURCE_ARCH)"
ui_print "Extracting module files..."

if ! unzip -oq "$ZIPFILE" -d "$MODPATH" 2>&1; then
    if command -v busybox > /dev/null 2>&1; then
        busybox unzip -oq "$ZIPFILE" -d "$MODPATH" 2>&1 || {
            ui_print "! Extraction failed"
            abort
        }
    else
        abort "! Extraction failed"
    fi
fi

if [ ! -f "$DAEMON_BINARY" ]; then
    ui_print ""
    ui_print "! DAEMON BINARY NOT FOUND"
    ui_print ""
    ui_print "Architecture: $SOURCE_ARCH"
    ui_print "Expected: libs/$SOURCE_ARCH/auriya"
    ui_print ""
    ui_print "This ZIP doesn't include binaries for your device."
    ui_print ""
    ui_print "Download the correct version from:"
    ui_print "https://github.com/pavelc4/auriya/releases"
    abort
fi

if [ -f "$CLI_BINARY" ]; then
    HAS_CLI=true
else
    ui_print "! CLI binary not found (daemon-only mode)"
fi

ui_print "Verifying binary integrity..."

if [ -f "$DAEMON_SHA" ] || [ -f "$CHECKSUMS_FILE" ]; then
    if command -v sha256sum > /dev/null 2>&1; then
        DAEMON_ACTUAL_SHA=$(sha256sum "$DAEMON_BINARY" | cut -d' ' -f1)
        [ "$HAS_CLI" = true ] && CLI_ACTUAL_SHA=$(sha256sum "$CLI_BINARY" | cut -d' ' -f1)
    elif command -v shasum > /dev/null 2>&1; then
        DAEMON_ACTUAL_SHA=$(shasum -a 256 "$DAEMON_BINARY" | cut -d' ' -f1)
        [ "$HAS_CLI" = true ] && CLI_ACTUAL_SHA=$(shasum -a 256 "$CLI_BINARY" | cut -d' ' -f1)
    else
        ui_print "! sha256sum not available, skipping verification"
        DAEMON_ACTUAL_SHA=""
    fi
    
    if [ -n "$DAEMON_ACTUAL_SHA" ]; then
        if [ -f "$CHECKSUMS_FILE" ]; then
            DAEMON_EXPECTED_SHA=$(grep "auriya$" "$CHECKSUMS_FILE" | cut -d' ' -f1)
        elif [ -f "$DAEMON_SHA" ]; then
            DAEMON_EXPECTED_SHA=$(cut -d' ' -f1 "$DAEMON_SHA")
        fi
        
        if [ "$DAEMON_ACTUAL_SHA" = "$DAEMON_EXPECTED_SHA" ]; then
            ui_print "✓ Daemon SHA256: ${DAEMON_EXPECTED_SHA:0:16}... (verified)"
        else
            ui_print ""
            ui_print "! DAEMON CHECKSUM MISMATCH"
            ui_print ""
            ui_print "Expected: ${DAEMON_EXPECTED_SHA:0:16}..."
            ui_print "Actual:   ${DAEMON_ACTUAL_SHA:0:16}..."
            ui_print ""
            ui_print "The module file is corrupted or incomplete."
            ui_print ""
            ui_print "Download again from:"
            ui_print "https://github.com/pavelc4/auriya/releases"
            ui_print ""
            abort "Installation aborted (integrity check failed)"
        fi
        
        if [ "$HAS_CLI" = true ] && [ -n "$CLI_ACTUAL_SHA" ]; then
            if [ -f "$CHECKSUMS_FILE" ]; then
                CLI_EXPECTED_SHA=$(grep "auriyactl$" "$CHECKSUMS_FILE" | cut -d' ' -f1)
                
                if [ "$CLI_ACTUAL_SHA" = "$CLI_EXPECTED_SHA" ]; then
                    ui_print " CLI SHA256: ${CLI_EXPECTED_SHA:0:16}... (verified)"
                else
                    ui_print "CLI checksum mismatch (continuing with daemon only)"
                    HAS_CLI=false
                fi
            else
                ui_print " CLI SHA256: ${CLI_ACTUAL_SHA:0:16}... (calculated)"
            fi
        fi
    fi
else
    ui_print "! Checksums not found, cannot verify integrity"
fi

mkdir -p "$MODPATH/system/bin"

cp "$DAEMON_BINARY" "$MODPATH/system/bin/auriya" || abort "! Failed to copy daemon"
chmod 0755 "$MODPATH/system/bin/auriya"
DAEMON_SIZE=$(du -h "$MODPATH/system/bin/auriya" | cut -f1)
ui_print "Daemon installed ($DAEMON_SIZE)"

if [ "$HAS_CLI" = true ]; then
    cp "$CLI_BINARY" "$MODPATH/system/bin/auriyactl" || {
        ui_print "! Failed to copy CLI (continuing without)"
        HAS_CLI=false
    }
    
    if [ "$HAS_CLI" = true ]; then
        chmod 0755 "$MODPATH/system/bin/auriyactl"
        CLI_SIZE=$(du -h "$MODPATH/system/bin/auriyactl" | cut -f1)
        ui_print "✓ CLI installed ($CLI_SIZE)"
    fi
fi

rm -rf "$MODPATH/libs"
ui_print ""

make_dir "$MODULE_CONFIG"
CONFIG_SETTINGS="$MODULE_CONFIG/settings.toml"
CONFIG_GAMELIST="$MODULE_CONFIG/gamelist.toml"

if [ ! -f "$CONFIG_SETTINGS" ]; then
    mv "$MODPATH/settings.toml" "$CONFIG_SETTINGS" 2>/dev/null
    chmod 0644 "$CONFIG_SETTINGS"
fi

if [ ! -f "$CONFIG_GAMELIST" ]; then
    mv "$MODPATH/gamelist.toml" "$CONFIG_GAMELIST" 2>/dev/null
    chmod 0644 "$CONFIG_GAMELIST"
fi

rm -f "$MODPATH/settings.toml" "$MODPATH/gamelist.toml"

make_node "$GPU_TYPE" "$MODULE_CONFIG/gpu_type"
make_node "$ARCH" "$MODULE_CONFIG/arch"

make_dir "$LOG_DIR"
chmod 0755 "$LOG_DIR"
[ -f "$LOG_DIR/daemon.log" ] && mv "$LOG_DIR/daemon.log" "$LOG_DIR/daemon.log.old"

set_perm "$MODPATH/system/bin/auriya" 0 0 0755
[ "$HAS_CLI" = true ] && set_perm "$MODPATH/system/bin/auriyactl" 0 0 0755

ui_print "Installation complete!"
ui_print ""
ui_print "Usage after reboot:"
ui_print "  • Daemon runs automatically"

if [ "$HAS_CLI" = true ]; then
    ui_print "  • Control: auriyactl"
    ui_print ""
    ui_print "Examples:"
    ui_print "  auriyactl status"
    ui_print "  auriyactl set-profile performance"
    ui_print "  auriyactl set-fps 90"
    ui_print "  auriyactl add-game com.game.pkg"
else
    ui_print "  • Config: /data/adb/.config/auriya/"
fi

ui_print ""
