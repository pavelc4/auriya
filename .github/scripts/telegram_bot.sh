#!/bin/bash

# Check secrets
if [ -z "$BOT_TOKEN" ] || [ -z "$CHAT_ID" ]; then
    echo "Error: BOT_TOKEN or CHAT_ID not set."
    exit 0
fi

# Find the zip file
ZIP_FILE=$(find build/release -name "auriya-*.zip" | head -n 1)

if [ -z "$ZIP_FILE" ]; then
    echo "Error: No zip file found in build/release/"
    exit 1
fi

# Calculate stats
FILE_NAME=$(basename "$ZIP_FILE")
FILE_SIZE=$(du -h "$ZIP_FILE" | cut -f1)
CHECKSUM=$(sha256sum "$ZIP_FILE" | cut -d' ' -f1)
COMMIT_HASH=$(git rev-parse --short HEAD)
COMMIT_MSG=$(git log -1 --pretty=%s)

# Calculate duration
if [ -f "build_start_time" ]; then
    START_TIME=$(cat build_start_time)
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))
    MINUTES=$((DURATION / 60))
    SECONDS=$((DURATION % 60))
    TOTAL_TIME="${MINUTES}m ${SECONDS}s"
else
    TOTAL_TIME="Unknown"
fi

CAPTION="🚀 <b>New Build Deployed Successfully!</b>

📦 <b>File:</b> <code>$FILE_NAME</code>
<b>Size:</b> $FILE_SIZE
<b>Path:</b> <code>build/release/</code>

<b>Checksum (SHA256):</b>
<code>$CHECKSUM</code>

<b>Build Info:</b>
  • <b>Commit:</b> <code>$COMMIT_HASH</code>
  • <b>Message:</b> $COMMIT_MSG
  • <b>Runner:</b> $RUNNER_OS ($RUNNER_ARCH)
  • <b>Total Time:</b> $TOTAL_TIME"

echo "Uploading $FILE_NAME to Telegram..."

curl -F chat_id="$CHAT_ID" \
     -F document=@"$ZIP_FILE" \
     -F caption="$CAPTION" \
     -F parse_mode="HTML" \
     "https://api.telegram.org/bot$BOT_TOKEN/sendDocument"

# Upload Binary
BINARY_FILE="build/release/module/system/bin/auriya"
if [ -f "$BINARY_FILE" ]; then
    echo "Uploading binary to Telegram..."
    BINARY_NAME="auriya-binary-$COMMIT_HASH"
    cp "$BINARY_FILE" "$BINARY_NAME"
    
    BINARY_SIZE=$(du -h "$BINARY_NAME" | cut -f1)
    BINARY_SUM=$(sha256sum "$BINARY_NAME" | cut -d' ' -f1)
    
    BINARY_CAPTION="<b>Auriya Binary</b>
    
Target: <code>aarch64-linux-android</code>
Size: $BINARY_SIZE
Checksum: <code>$BINARY_SUM</code>"

    curl -F chat_id="$CHAT_ID" \
         -F document=@"$BINARY_NAME" \
         -F caption="$BINARY_CAPTION" \
         -F parse_mode="HTML" \
         "https://api.telegram.org/bot$BOT_TOKEN/sendDocument"
    
    rm "$BINARY_NAME"
fi

echo "Upload complete."
