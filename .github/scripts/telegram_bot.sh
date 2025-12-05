#!/bin/bash

# Check secrets
if [ -z "$BOT_TOKEN" ] || [ -z "$CHAT_ID" ]; then
    echo "Error: BOT_TOKEN or CHAT_ID not set."
    exit 1
fi

# Find the zip file
ZIP_FILE=$(find build/release -name "auriya-*.zip" | head -n 1)

if [ -z "$ZIP_FILE" ]; then
    echo "Error: No zip file found in build/release/"
    exit 1
fi

FILE_NAME=$(basename "$ZIP_FILE")
COMMIT_HASH=$(git rev-parse --short HEAD)
CHANGELOG=$(git log -3 --pretty=format:"- %s (%h)")

CAPTION="🚀 *Auriya Build Complete*

📦 *File:* \`$FILE_NAME\`
Commit: \`$COMMIT_HASH\`

*Changelog:*
$CHANGELOG"

echo "Uploading $FILE_NAME to Telegram..."

curl -F chat_id="$CHAT_ID" \
     -F document=@"$ZIP_FILE" \
     -F caption="$CAPTION" \
     -F parse_mode="Markdown" \
     "https://api.telegram.org/bot$BOT_TOKEN/sendDocument"

# Upload Binary
BINARY_FILE="build/release/module/system/bin/auriya"
if [ -f "$BINARY_FILE" ]; then
    echo "Uploading binary to Telegram..."
    BINARY_NAME="auriya-binary-$COMMIT_HASH"
    cp "$BINARY_FILE" "$BINARY_NAME"
    
    BINARY_CAPTION="⚙️ *Auriya Binary*
    
Target: \`aarch64-linux-android\`
Commit: \`$COMMIT_HASH\`"

    curl -F chat_id="$CHAT_ID" \
         -F document=@"$BINARY_NAME" \
         -F caption="$BINARY_CAPTION" \
         -F parse_mode="Markdown" \
         "https://api.telegram.org/bot$BOT_TOKEN/sendDocument"
    
    rm "$BINARY_NAME"
fi

echo "Upload complete."
