#!/system/bin/sh
MODPATH="${MODPATH:-/data/adb/modules/auriya}"
MODULE_CONFIG="/data/adb/.config/auriya"
LOG_DIR="/data/adb/auriya"

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
ui_print "  / /| | / / / / ___/ / / / / __ \    "
ui_print " / ___ |/ /_/ / /  / / /_/ / /_/ /    "
ui_print "/_/  |_|\__,_/_/  /_/\__, /\__,_/     "
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

ui_print "Installing for $ARCH ($SOURCE_ARCH)"
ui_print "Extracting module files..."

# Extract module files
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

LIBS_DIR="$MODPATH/libs/$SOURCE_ARCH"
ARCH_BINARY="$LIBS_DIR/auriya"
ARCH_SHA="$LIBS_DIR/auriya.sha256"

if [ ! -f "$ARCH_BINARY" ]; then
    ui_print ""
    ui_print "! BINARY NOT FOUND"
    ui_print ""
    ui_print "Architecture: $SOURCE_ARCH"
    ui_print "Expected: libs/$SOURCE_ARCH/auriya"
    ui_print ""
    ui_print "This ZIP doesn't include binaries for your device."
    ui_print ""
    ui_print "Download the version from:"
    ui_print "https://github.com/pavelc4/auriya/releases"
    abort
fi

ui_print "Verifying binary integrity..."

if [ -f "$ARCH_SHA" ]; then
    if command -v sha256sum > /dev/null 2>&1; then
        ACTUAL_SHA=$(sha256sum "$ARCH_BINARY" | cut -d' ' -f1)
    elif command -v shasum > /dev/null 2>&1; then
        ACTUAL_SHA=$(shasum -a 256 "$ARCH_BINARY" | cut -d' ' -f1)
    else
        ui_print "! sha256sum not available, skipping verification"
        ACTUAL_SHA=""
    fi
    
    if [ -n "$ACTUAL_SHA" ]; then
        EXPECTED_SHA=$(cut -d' ' -f1 "$ARCH_SHA")
        
        if [ "$ACTUAL_SHA" = "$EXPECTED_SHA" ]; then
            ui_print "✓ SHA256: ${EXPECTED_SHA:0:16}... (verified)"
        else
            ui_print ""
            ui_print "! CHECKSUM MISMATCH"
            ui_print ""
            ui_print "Expected: ${EXPECTED_SHA:0:16}..."
            ui_print "Actual:   ${ACTUAL_SHA:0:16}..."
            ui_print ""
            ui_print "The module file is corrupted or incomplete."
            ui_print ""
            ui_print "Download again from:"
            ui_print "https://github.com/pavelc4/auriya/releases"
            ui_print ""
            abort "Installation aborted (integrity check failed)"
        fi
    fi
else
    ui_print "! SHA256 not found, cannot verify integrity"
fi

mkdir -p "$MODPATH/system/bin"
cp "$ARCH_BINARY" "$MODPATH/system/bin/auriya" || abort "! Failed to copy binary"
chmod 0755 "$MODPATH/system/bin/auriya" "$MODPATH/system/bin/auriya-ctl"

rm -rf "$MODPATH/libs"

ui_print "✓ Installed $SOURCE_ARCH binary"
ui_print ""

make_dir "$MODULE_CONFIG"
CONFIG_SETTINGS="$MODULE_CONFIG/settings.toml"
CONFIG_GAMELIST="$MODULE_CONFIG/gamelist.toml"

if [ ! -f "$CONFIG_SETTINGS" ]; then
    mv "$MODPATH/settings.toml" "$CONFIG_SETTINGS"
    chmod 0644 "$CONFIG_SETTINGS"
fi

if [ ! -f "$CONFIG_GAMELIST" ]; then
    mv "$MODPATH/gamelist.toml" "$CONFIG_GAMELIST"
    chmod 0644 "$CONFIG_GAMELIST"
fi

rm -f "$MODPATH/settings.toml" "$MODPATH/gamelist.toml"

make_node "$GPU_TYPE" "$MODULE_CONFIG/gpu_type"
make_node "$ARCH" "$MODULE_CONFIG/arch"

make_dir "$LOG_DIR"
chmod 0755 "$LOG_DIR"
[ -f "$LOG_DIR/daemon.log" ] && mv "$LOG_DIR/daemon.log" "$LOG_DIR/daemon.log.old"
set_perm "$MODPATH/system/bin/auriya" 0 0 0755
set_perm "$MODPATH/system/bin/auriya-ctl" 0 0 0755

ui_print "Installation complete"
ui_print ""
