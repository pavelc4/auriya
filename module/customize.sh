#!/system/bin/sh

if [ -z "$MODPATH" ]; then
    MODPATH=$(dirname "$0")
    case "$MODPATH" in
        /*) : ;;
        *) MODPATH="$PWD/$MODPATH" ;;
    esac
fi

MODNAME="auriya"
MODVERSION="1.0.0"
MODULE_CONFIG="/data/adb/.config/$MODNAME"

make_dir() {
    [ ! -d "$1" ] && mkdir -p "$1"
}


case "$ARCH" in
    arm64) ARCH_TMP="arm64-v8a" ;;
    arm) ARCH_TMP="armeabi-v7a" ;;
    x64) ARCH_TMP="x86_64" ;;
    x86) ARCH_TMP="x86" ;;
    riscv64) ARCH_TMP="riscv64" ;;
    *) abort " Unsupported architecture: $ARCH" ;;
esac

ui_print "- Detected architecture: $ARCH_TMP"

if [ "$KSU" = "true" ] || [ "$APATCH" = "true" ]; then
    ui_print "- KSU/APatch detected, enabling skip_mount"

    if ! touch "$MODPATH/skip_mount" 2>/dev/null; then
        ui_print " Failed to create skip_mount at $MODPATH"
    fi

    manager_paths="/data/adb/ap/bin /data/adb/ksu/bin"
    BIN_PATH="$MODPATH/system/bin"

    for dir in $manager_paths; do
        if [ -d "$dir" ]; then
            ui_print "- Creating symlink in $dir"
            ln -sf "$BIN_PATH/auriya" "$dir/auriya" 2>/dev/null
        fi
    done
fi


make_dir "$MODULE_CONFIG"
chmod 0755 "$MODULE_CONFIG"

if [ ! -f "$MODULE_CONFIG/auriya.toml" ]; then
    if [ -f "$MODPATH/Packages.toml" ]; then
        ui_print "- Copying default config"
        cp "$MODPATH/Packages.toml" "$MODULE_CONFIG/auriya.toml"
        chmod 0644 "$MODULE_CONFIG/auriya.toml"
    else
        ui_print " Packages.toml not found!"
    fi
fi

# Permissions
if [ -d "$MODPATH/system/bin" ]; then
    ui_print "- Setting permissions"
    set_perm_recursive "$MODPATH/system/bin" 0 0 0755 0755
    set_perm "$MODPATH/system/bin/auriya" 0 0 0755
else
    ui_print " Missing directory: $MODPATH/system/bin"
fi

ui_print ""
ui_print " Installation complete!"
