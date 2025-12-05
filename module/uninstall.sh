#!/system/bin/sh

CONFIG_DIR="/data/adb/.config/auriya"
SETTINGS_CFG="$CONFIG_DIR/settings.toml"
GAMELIST_CFG="$CONFIG_DIR/gamelist.toml"
LOG_FILE="/data/adb/auriya/daemon.log"
SOCKET="/dev/socket/auriya.sock"
GPU_TYPE_FILE="$CONFIG_DIR/gpu_type"
MODPATH="/data/adb/modules/auriya"

print_header() {
    echo ""
    echo "    ___               _              "
    echo "   /   |  __  _______(_)__  ______ _ "
    echo "  / /| | / / / / ___/ / / / / __ \`/ "
    echo " / ___ |/ /_/ / /  / / /_/ / /_/ /   "
    echo "/_/  |_|\__,_/_/  /_/\__, /\__,_/    "
    echo "                    /____/           "
    echo ""
    echo "- Auriya Daemon Control"
    echo ""
}

print_section() {
    echo "- $1"
}

if [ "$1" = "restart" ] || [ "$1" = "-r" ]; then
    print_header
    echo "- Restarting daemon..."
    pkill -9 auriya 2>/dev/null
    sleep 1
    
    if [ -f "$MODPATH/service.sh" ]; then
        sh "$MODPATH/service.sh" > /dev/null 2>&1 &
        sleep 3
        if pgrep -f auriya > /dev/null 2>&1; then
            PID=$(pgrep auriya | head -1)
            echo "  Started (PID: $PID)"
            [ -e "$SOCKET" ] && echo "  IPC ready"
        else
            echo "  ! Failed to start"
        fi
    else
        echo "  ! service.sh not found"
    fi
    echo ""
    exit 0
fi

print_header

print_section "Daemon Status"
if pgrep -f auriya > /dev/null 2>&1; then
    PID=$(pgrep auriya | head -1)
    UPTIME=$(ps -o etime= -p "$PID" 2>/dev/null | tr -d ' ')
    echo "  Running (PID: $PID, Uptime: ${UPTIME:-unknown})"
else
    echo "  Not running"
fi
echo ""

print_section "IPC Socket"
if [ -e "$SOCKET" ]; then
    echo "  Ready"
else
    echo "  Not found"
fi
echo ""

if [ -e "$SOCKET" ]; then
    print_section "Live Status"
    STATUS=$(echo "STATUS" | nc -U "$SOCKET" 2>/dev/null | tail -1)
    if [ -n "$STATUS" ] && [ "$STATUS" != "IPC_ERROR" ]; then
        echo "$STATUS" | tr ' ' '\n' | while IFS='=' read -r key value; do
            case "$key" in
                ENABLED) echo "  Daemon:   $value" ;;
                PACKAGES) echo "  Games:    $value configured" ;;
                PROFILE) echo "  Profile:  $value" ;;
            esac
        done
    fi
    echo ""
fi

print_section "Configuration"

if [ -f "$SETTINGS_CFG" ]; then
    FAS=$(grep '^fas_enabled' "$SETTINGS_CFG" 2>/dev/null | cut -d'=' -f2 | tr -d ' ')
    MODE=$(grep '^fas_mode' "$SETTINGS_CFG" 2>/dev/null | cut -d'=' -f2 | tr -d ' "')
    [ "$FAS" = "true" ] && echo "  FAS: Enabled (mode: ${MODE:-balance})" || echo "  FAS: Disabled"
else
    echo "  ! settings.toml not found"
fi

if [ -f "$GPU_TYPE_FILE" ]; then
    echo "  GPU: $(cat "$GPU_TYPE_FILE")"
fi
echo ""

print_section "Logs"
if [ -f "$LOG_FILE" ]; then
    SIZE=$(du -h "$LOG_FILE" 2>/dev/null | cut -f1)
    echo "  Size: $SIZE"
    echo "  Last 5 lines:"
    tail -5 "$LOG_FILE" 2>/dev/null | sed 's/^/    /'
else
    echo "  No log file"
fi
echo ""

echo "Commands:"
echo "  $0 restart   # Restart daemon"
echo "  echo STATUS | nc -U $SOCKET"
echo "  tail -f $LOG_FILE"
echo ""
