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
SESSION_ID = "live-keystroke-trace-session"
PASSPHRASE = "batu-merah-kuda-terbang"

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAC_BINARY = os.path.join(PROJECT_ROOT, "target", "debug", "terminal-mirror-mac")


def derive_key(passphrase: str) -> bytes:
    return hashlib.sha256(passphrase.encode("utf-8")).digest()


def derive_nonce(seq: int) -> bytes:
    return b"\x00\x00\x00\x00" + struct.pack(">Q", seq)


@pytest.mark.asyncio
async def test_trace_raw_keystroke_bytes():
    """
    Traces exact byte chunks received from Darwin PTY when sending a command.
    """
    key = derive_key(PASSPHRASE)
    cipher = ChaCha20Poly1305(key)

    agent_proc = await asyncio.create_subprocess_exec(
        MAC_BINARY,
        "--relay-url", WS_URL,
        "--host-id", "mac-trace-test",
        "--session-id", SESSION_ID,
        "--passphrase", PASSPHRASE,
        "--auth-token", AUTH_TOKEN,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )

    try:
        await asyncio.sleep(1.5)
        sub_ws_url = f"{WS_URL}?token={AUTH_TOKEN}&session_id={SESSION_ID}&role=client"
        async with websockets.connect(sub_ws_url) as ws_sub:
            # Drain initial prompt
            for _ in range(5):
                try:
                    await asyncio.wait_for(ws_sub.recv(), timeout=1.0)
                except asyncio.TimeoutError:
                    break

            # Send command 'echo MAS_MUFID_DOUBLE_CHAR_FIXED\r'
            keystroke_cmd = b"echo MAS_MUFID_DOUBLE_CHAR_FIXED\r"
            seq_up = 9991
            nonce_up = derive_nonce(seq_up)
            ciphertext_up = cipher.encrypt(nonce_up, keystroke_cmd, None)

            up_packet = {
                "version": 1,
                "trace_id": "trace-keystroke-bytes",
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

            chunks = []
            deadline = asyncio.get_event_loop().time() + 3.0
            while asyncio.get_event_loop().time() < deadline:
                try:
                    raw_msg = await asyncio.wait_for(ws_sub.recv(), timeout=1.0)
                    packet = msgpack.unpackb(raw_msg, raw=False)
                    if packet.get("payload", {}).get("type") == "EncryptedBlob":
                        blob = packet["payload"]["data"]
                        nonce = derive_nonce(blob["nonce"])
                        raw_cipher = bytes(blob["ciphertext"]) if isinstance(blob["ciphertext"], list) else blob["ciphertext"]
                        plaintext = cipher.decrypt(nonce, raw_cipher, None)
                        chunks.append(plaintext)
                except asyncio.TimeoutError:
                    break

            print("\n--- DETAILED CHUNKS RECEIVED ---")
            for idx, c in enumerate(chunks):
                print(f"Chunk {idx}: {c!r}")
            print("--- END CHUNKS ---")

            assert len(chunks) > 0

    finally:
        if agent_proc.returncode is None:
            agent_proc.send_signal(signal.SIGTERM)
            try:
                await asyncio.wait_for(agent_proc.wait(), timeout=3.0)
            except asyncio.TimeoutError:
                agent_proc.kill()
