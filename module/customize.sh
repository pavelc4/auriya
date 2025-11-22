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

ui_print "============================================"
ui_print "            Auriya  Daemon                  "
ui_print "     Frame-Aware Performance Optimizer      "
ui_print "============================================"
ui_print ""

ui_print "==== System Information ===="
ui_print " Android:  $(getprop ro.build.version.release)"
ui_print " SDK:      $(getprop ro.build.version.sdk)"
ui_print " Device:   $(getprop ro.product.model)"
ui_print " Arch:     $ARCH"
ui_print " SOC:      $(getprop ro.hardware)"

GPU_TYPE=$(detect_gpu)
ui_print " GPU:      $GPU_TYPE"

[ "$KSU" = "true" ] && ui_print " Root:     KernelSU" || ui_print " Root:     Magisk"
ui_print "============================"
ui_print ""

case $ARCH in
    arm64|arm|x64|x86) ui_print ">> Installing for $ARCH..." ;;
    *) ui_print "! Unsupported Architecture: $ARCH"; abort ;;
esac

ui_print ">> Extracting files..."

if [ ! -f "$MODPATH/system/bin/auriya" ]; then
    ui_print "   Using manual extraction..."

    if ! unzip -oq "$ZIPFILE" -d "$MODPATH" 2>&1; then
        ui_print "! ERROR: unzip failed!"
        if command -v busybox >/dev/null 2>&1; then
            ui_print "   Trying busybox unzip..."
            busybox unzip -oq "$ZIPFILE" -d "$MODPATH" 2>&1 || {
                ui_print "! ERROR: All extraction methods failed!"
                abort
            }
        else
            abort "Extraction failed"
        fi
    fi
fi

if [ ! -f "$MODPATH/system/bin/auriya" ]; then
    ui_print "! ERROR: Binary not found!"
    abort "Installation failed"
fi

SIZE=$(stat -c%s "$MODPATH/system/bin/auriya" 2>/dev/null || stat -f%z "$MODPATH/system/bin/auriya")
ui_print "   Binary: $((SIZE / 1024 / 1024))MB [OK]"

make_dir "$MODULE_CONFIG"
CONFIG_SETTINGS="$MODULE_CONFIG/settings.toml"
CONFIG_GAMELIST="$MODULE_CONFIG/gamelist.toml"

if [ -f "$CONFIG_SETTINGS" ]; then
    ui_print "[+] Settings config preserved"
else
    ui_print ">> Installing settings config..."
    mv "$MODPATH/settings.toml" "$CONFIG_SETTINGS"
    chmod 0644 "$CONFIG_SETTINGS"
    ui_print "[+] Settings config installed"
fi

if [ -f "$CONFIG_GAMELIST" ]; then
    ui_print "[+] Gamelist config preserved"
else
    ui_print ">> Installing gamelist config..."
    mv "$MODPATH/gamelist.toml" "$CONFIG_GAMELIST"
    chmod 0644 "$CONFIG_GAMELIST"
    ui_print "[+] Gamelist config installed"
fi

rm -f "$MODPATH/settings.toml" "$MODPATH/gamelist.toml"

make_node "$GPU_TYPE" "$MODULE_CONFIG/gpu_type"
make_node "$ARCH" "$MODULE_CONFIG/arch"

make_dir "$LOG_DIR"
chmod 0755 "$LOG_DIR"
[ -f "$LOG_DIR/daemon.log" ] && mv "$LOG_DIR/daemon.log" "$LOG_DIR/daemon.log.old"

if [ "$KSU" = "true" ] || [ "$APATCH" = "true" ]; then
    ui_print ">> Creating symlinks..."
    for dir in /data/adb/ksu/bin /data/adb/ap/bin; do
        [ -d "$dir" ] && ln -sf "$MODPATH/system/bin/auriya" "$dir/auriya" && ui_print "   $dir [OK]"
    done
fi

ui_print ""
ui_print "============================================"
ui_print "          Installation Successful!          "
ui_print "============================================"
ui_print ""
ui_print "Settings Config:  $CONFIG_SETTINGS"
ui_print "Gamelist Config:  $CONFIG_GAMELIST"
ui_print "Logs:             $LOG_DIR/daemon.log"
ui_print "IPC:              /dev/socket/auriya.sock"
ui_print ""
ui_print "Daemon starts on boot. Reboot to activate."
ui_print ""
[ "$GPU_TYPE" = "adreno" ] && ui_print ">> Enjoy smooth gaming!" || ui_print "[!] FAS limited on $GPU_TYPE"
ui_print ""
