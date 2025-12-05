#!/bin/bash

if [ -z "$BOT_TOKEN" ] || [ -z "$CHAT_ID" ]; then
    echo "Error: BOT_TOKEN or CHAT_ID not set."
    exit 0
fi

COMMIT_HASH=$(git rev-parse --short HEAD)
COMMIT_MSG=$(git log -1 --pretty=%s)
AUTHOR=$(git log -1 --pretty=%an)

MESSAGE="🚀 <b>Build Started: Auriya</b>

<b>Commit:</b> <code>$COMMIT_HASH</code>
<b>Author:</b> $AUTHOR
<b>Message:</b> <code>$COMMIT_MSG</code>
<b>Runner:</b> $RUNNER_OS ($RUNNER_ARCH)

<i>Building artifacts...</i>"

curl -s -X POST "https://api.telegram.org/bot$BOT_TOKEN/sendMessage" \
    -d chat_id="$CHAT_ID" \
    -d text="$MESSAGE" \
    -d parse_mode="HTML"
