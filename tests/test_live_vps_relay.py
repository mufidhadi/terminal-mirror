import asyncio
import hashlib
import json
import struct
import urllib.request
import pytest
import websockets
from websockets.exceptions import InvalidStatus
import msgpack
from cryptography.hazmat.primitives.ciphers.aead import ChaCha20Poly1305

RELAY_HOST = "172.23.127.184:8888"
WS_URL = f"ws://{RELAY_HOST}/ws"
HTTP_URL = f"http://{RELAY_HOST}"
AUTH_TOKEN = "masmufid_super_secret_relay_2026"


def derive_key(passphrase: str) -> bytes:
    """Matches Rust Sha256::digest and Android MessageDigest.getInstance('SHA-256')"""
    return hashlib.sha256(passphrase.encode("utf-8")).digest()


def derive_nonce(seq: int) -> bytes:
    """Matches Rust derive_nonce (4 zeros + 8 bytes big-endian)"""
    return b"\x00\x00\x00\x00" + struct.pack(">Q", seq)


def test_vps_relay_healthz():
    """Verify live VPS relay container health endpoint"""
    req = urllib.request.Request(f"{HTTP_URL}/healthz")
    with urllib.request.urlopen(req, timeout=5) as resp:
        assert resp.status == 200
        body = resp.read().decode("utf-8")
        assert "OK" in body


def test_vps_relay_metrics():
    """Verify Prometheus metrics exposed on live VPS relay"""
    req = urllib.request.Request(f"{HTTP_URL}/metrics")
    with urllib.request.urlopen(req, timeout=5) as resp:
        assert resp.status == 200
        body = resp.read().decode("utf-8")
        assert "relay_active_sessions" in body
        assert "relay_frames_routed_total" in body


@pytest.mark.asyncio
async def test_vps_relay_unauthorized_token_rejection():
    """Verify VPS relay rejects connections with bad auth tokens (401)"""
    bad_url = f"{WS_URL}?token=wrong_secret_123&session_id=sec-test&role=client"
    with pytest.raises(InvalidStatus) as excinfo:
        async with websockets.connect(bad_url):
            pass
    assert excinfo.value.response.status_code == 401


@pytest.mark.asyncio
async def test_vps_relay_e2ee_chacha20poly1305_live_exchange():
    """
    Verify complete End-to-End Encrypted (Zero-Knowledge) exchange over the live Hostinger VPS.
    Host encrypts terminal stream using ChaCha20-Poly1305.
    Subscriber decrypts ciphertext received through VPS relay.
    Subscriber encrypts remote keystrokes upstream.
    """
    session_id = "vps-live-e2ee-session"
    passphrase = "kuda-terbang-angin-gunung"
    key = derive_key(passphrase)
    cipher = ChaCha20Poly1305(key)

    host_ws_url = f"{WS_URL}?token={AUTH_TOKEN}&session_id={session_id}&role=host"
    sub_ws_url = f"{WS_URL}?token={AUTH_TOKEN}&session_id={session_id}&role=client"

    async with websockets.connect(host_ws_url) as ws_host, websockets.connect(sub_ws_url) as ws_sub:
        # 1. Host encrypts terminal output downstream
        raw_terminal_output = b"\x1b[32m[mas-mufid@macbook:~]$ uname -mprsv\x1b[0m\r\n"
        seq_down = 701
        nonce_down = derive_nonce(seq_down)
        ciphertext_down = cipher.encrypt(nonce_down, raw_terminal_output, None)

        packet_down = {
            "version": 1,
            "trace_id": "trace-py-host-001",
            "session_id": session_id,
            "sequence": seq_down,
            "timestamp_ms": 1726528800000,
            "payload": {
                "type": "EncryptedBlob",
                "data": {
                    "nonce": seq_down,
                    "ciphertext": ciphertext_down,
                },
            },
        }

        # Send MessagePack encoded packet
        await ws_host.send(msgpack.packb(packet_down, use_bin_type=True))

        # 2. Subscriber receives from live VPS relay
        raw_sub_msg = await asyncio.wait_for(ws_sub.recv(), timeout=5.0)
        assert isinstance(raw_sub_msg, bytes)
        decoded_packet = msgpack.unpackb(raw_sub_msg, raw=False)

        assert decoded_packet["session_id"] == session_id
        assert decoded_packet["sequence"] == seq_down
        payload = decoded_packet["payload"]
        assert payload["type"] == "EncryptedBlob"

        # Decrypt with ChaCha20Poly1305
        recv_nonce = derive_nonce(payload["data"]["nonce"])
        recv_ciphertext = payload["data"]["ciphertext"]
        decrypted_output = cipher.decrypt(recv_nonce, recv_ciphertext, None)
        assert decrypted_output == raw_terminal_output

        # 3. Subscriber encrypts keystrokes upstream
        raw_keystroke = b"top -n 1\n"
        seq_up = 901
        nonce_up = derive_nonce(seq_up)
        ciphertext_up = cipher.encrypt(nonce_up, raw_keystroke, None)

        packet_up = {
            "version": 1,
            "trace_id": "trace-py-sub-002",
            "session_id": session_id,
            "sequence": seq_up,
            "timestamp_ms": 1726528801000,
            "payload": {
                "type": "EncryptedBlob",
                "data": {
                    "nonce": seq_up,
                    "ciphertext": ciphertext_up,
                },
            },
        }

        await ws_sub.send(msgpack.packb(packet_up, use_bin_type=True))

        # 4. Host receives upstream keystrokes from live VPS relay
        raw_host_msg = await asyncio.wait_for(ws_host.recv(), timeout=5.0)
        assert isinstance(raw_host_msg, bytes)
        decoded_host_packet = msgpack.unpackb(raw_host_msg, raw=False)

        assert decoded_host_packet["sequence"] == seq_up
        up_payload = decoded_host_packet["payload"]
        assert up_payload["type"] == "EncryptedBlob"

        recv_up_nonce = derive_nonce(up_payload["data"]["nonce"])
        recv_up_ciphertext = up_payload["data"]["ciphertext"]
        decrypted_keystroke = cipher.decrypt(recv_up_nonce, recv_up_ciphertext, None)
        assert decrypted_keystroke == raw_keystroke
