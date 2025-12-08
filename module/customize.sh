#!/system/bin/sh
MODPATH="${0%/*}"
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
ui_print "- Frame-Aware Performance Optimizer"
ui_print ""

ui_print "- Device: $(getprop ro.product.model) ($ARCH)"
ui_print "- Android: $(getprop ro.build.version.release) (SDK $(getprop ro.build.version.sdk))"

GPU_TYPE=$(detect_gpu)
ui_print "- GPU: $GPU_TYPE"

[ "$KSU" = "true" ] && ui_print "- Root: KernelSU" || ui_print "- Root: Magisk"
ui_print ""

case $ARCH in
    arm64|arm|x64|x86) ;;
    *) ui_print "! Unsupported Architecture: $ARCH"; abort ;;
esac

ui_print "- Extracting module files"

if [ ! -f "$MODPATH/system/bin/auriya" ]; then
    if ! unzip -oq "$ZIPFILE" -d "$MODPATH" 2>&1; then
        if command -v busybox >/dev/null 2>&1; then
            busybox unzip -oq "$ZIPFILE" -d "$MODPATH" 2>&1 || {
                ui_print "! Extraction failed"
                abort
            }
        else
            abort "! Extraction failed"
        fi
    fi
fi

if [ ! -f "$MODPATH/system/bin/auriya" ]; then
    abort "! Binary not found"
fi

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

if [ "$KSU" = "true" ] || [ "$APATCH" = "true" ]; then
    for dir in /data/adb/ksu/bin /data/adb/ap/bin; do
        if [ -d "$dir" ]; then
            ln -sf "$MODPATH/system/bin/auriya" "$dir/auriya"
            ln -sf "$MODPATH/system/bin/auriya-ctl" "$dir/auriya-ctl"
        fi
    done
fi

set_perm "$MODPATH/system/bin/auriya" 0 0 0755
set_perm "$MODPATH/system/bin/auriya-ctl" 0 0 0755

ui_print "- Installation complete"
ui_print ""
