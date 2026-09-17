#!/bin/bash
cd "$(dirname "$0")"

# Set terminal window title
echo -ne "\033]0;Terminal Mirror - Host Agent\007"

set -a
[ -f .env ] && . ./.env
set +a

RELAY_SERVER_URL="${RELAY_SERVER_URL:-ws://127.0.0.1:8888/ws}"
SESSION_ID="${SESSION_ID:-mac-live-session}"

clear
echo "================================================================="
echo "   TERMINAL MIRROR — macOS Host Agent"
echo "================================================================="
echo " Relay: ${RELAY_SERVER_URL}"
echo " Session: ${SESSION_ID}"
echo " (passphrase dimuat dari env/PASSPHRASE atau dibuat acak; tidak ditampilkan)"
echo "================================================================="
echo ""

./target/debug/terminal-mirror-mac --session-id "${SESSION_ID}" --relay-url "${RELAY_SERVER_URL}" "$@"
