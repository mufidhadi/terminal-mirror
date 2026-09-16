#!/bin/bash
cd "$(dirname "$0")"

# Set terminal window title
echo -ne "\033]0;Terminal Mirror - Host Agent\007"

clear
echo "================================================================="
echo "   🚀 TERMINAL MIRROR — macOS Host Agent"
echo "================================================================="
echo " Menghubungkan ke VPS Relay di ws://172.23.127.184:8888/ws..."
echo " Session: mac-live-session"
echo " Passphrase: batu-merah-kuda-terbang"
echo "================================================================="
echo ""

./target/debug/terminal-mirror-mac --session-id mac-live-session --passphrase batu-merah-kuda-terbang --relay-url ws://172.23.127.184:8888/ws --auth-token masmufid_super_secret_relay_2026 "$@"
