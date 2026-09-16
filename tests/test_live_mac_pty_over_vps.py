import asyncio
import hashlib
import os
import signal
import struct
import pytest
import websockets
import msgpack
from cryptography.hazmat.primitives.ciphers.aead import ChaCha20Poly1305

RELAY_HOST = "172.23.127.184:8888"
WS_URL = f"ws://{RELAY_HOST}/ws"
AUTH_TOKEN = "masmufid_super_secret_relay_2026"
SESSION_ID = "live-mac-darwin-vps-test"
PASSPHRASE = "batu-merah-kuda-terbang"

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAC_BINARY = os.path.join(PROJECT_ROOT, "target", "debug", "terminal-mirror-mac")


def derive_key(passphrase: str) -> bytes:
    return hashlib.sha256(passphrase.encode("utf-8")).digest()


def derive_nonce(seq: int) -> bytes:
    return b"\x00\x00\x00\x00" + struct.pack(">Q", seq)


@pytest.mark.asyncio
async def test_live_mac_darwin_pty_streaming_through_vps_relay():
    """
    Spawns real macOS Darwin PTY agent connected to live Hostinger VPS.
    Subscriber connects over ZeroTier, receives encrypted shell prompt,
    injects keystrokes, and verifies remote command execution output.
    """
    assert os.path.exists(MAC_BINARY), f"Binary not found: {MAC_BINARY}"

    key = derive_key(PASSPHRASE)
    cipher = ChaCha20Poly1305(key)

    # 1. Spawn local macOS Agent process connected to VPS relay
    agent_proc = await asyncio.create_subprocess_exec(
        MAC_BINARY,
        "--relay-url", WS_URL,
        "--host-id", "mac-darwin-test",
        "--session-id", SESSION_ID,
        "--passphrase", PASSPHRASE,
        "--auth-token", AUTH_TOKEN,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )

    try:
        # Give agent 1.5s to establish connection with VPS
        await asyncio.sleep(1.5)

        # 2. Connect Mobile Subscriber client over VPS
        sub_ws_url = f"{WS_URL}?token={AUTH_TOKEN}&session_id={SESSION_ID}&role=client"
        async with websockets.connect(sub_ws_url) as ws_sub:
            # 3. Read initial Darwin shell prompt / output frames
            received_chunks = []
            for _ in range(5):
                try:
                    raw_msg = await asyncio.wait_for(ws_sub.recv(), timeout=2.0)
                    packet = msgpack.unpackb(raw_msg, raw=False)
                    if packet.get("payload", {}).get("type") == "EncryptedBlob":
                        blob = packet["payload"]["data"]
                        nonce = derive_nonce(blob["nonce"])
                        raw_cipher = bytes(blob["ciphertext"]) if isinstance(blob["ciphertext"], list) else blob["ciphertext"]
                        plaintext = cipher.decrypt(nonce, raw_cipher, None)
                        received_chunks.append(plaintext)
                except asyncio.TimeoutError:
                    break

            # 4. Inject remote keystroke into macOS Darwin PTY via VPS
            keystroke_cmd = b"echo E2EE_HELLO_FROM_MOBILE\r"
            seq_up = 8888
            nonce_up = derive_nonce(seq_up)
            ciphertext_up = cipher.encrypt(nonce_up, keystroke_cmd, None)

            up_packet = {
                "version": 1,
                "trace_id": "trace-sub-keystroke-01",
                "session_id": SESSION_ID,
                "sequence": seq_up,
                "timestamp_ms": 1726528900000,
                "payload": {
                    "type": "EncryptedBlob",
                    "data": {
                        "nonce": seq_up,
                        "ciphertext": ciphertext_up,
                    },
                },
            }
            await ws_sub.send(msgpack.packb(up_packet, use_bin_type=True))

            # 5. Collect Darwin PTY echoed terminal response through VPS
            all_received_text = b"".join(received_chunks)
            found_output = False
            deadline = asyncio.get_event_loop().time() + 6.0

            while asyncio.get_event_loop().time() < deadline:
                try:
                    raw_msg = await asyncio.wait_for(ws_sub.recv(), timeout=1.0)
                    packet = msgpack.unpackb(raw_msg, raw=False)
                    if packet.get("payload", {}).get("type") == "EncryptedBlob":
                        blob = packet["payload"]["data"]
                        nonce = derive_nonce(blob["nonce"])
                        raw_cipher = bytes(blob["ciphertext"]) if isinstance(blob["ciphertext"], list) else blob["ciphertext"]
                        plaintext = cipher.decrypt(nonce, raw_cipher, None)
                        all_received_text += plaintext
                        if b"E2EE_HELLO_FROM_MOBILE" in all_received_text:
                            found_output = True
                            break
                except asyncio.TimeoutError:
                    continue

            assert found_output, (
                f"Did not observe echoed command output in stream. Received: {all_received_text!r}"
            )

    finally:
        # Gracefully terminate macOS Agent
        if agent_proc.returncode is None:
            agent_proc.send_signal(signal.SIGTERM)
            try:
                await asyncio.wait_for(agent_proc.wait(), timeout=3.0)
            except asyncio.TimeoutError:
                agent_proc.kill()
