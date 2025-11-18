#!/bin/bash

echo "Auriya Daemon Module"
echo ""

if pgrep -f auriya > /dev/null; then
    echo "Status: Daemon is RUNNING"
    echo "PID: $(pgrep -f auriya)"
else
    echo "Status: Daemon is NOT running"
    echo "Starting daemon..."
    /data/adb/modules/auriya/system/bin/auriya /data/adb/.config/auriya/auriya.toml &
    sleep 1
    if pgrep -f auriya > /dev/null; then
        echo "Daemon started successfully"
    else
        echo "Failed to start daemon"
    fi
fi

echo ""
echo "Config: /data/adb/.config/auriya/auriya.toml"
echo "Binary: /data/adb/modules/auriya/system/bin/auriya"
echo ""
sleep 3