package com.mufid.terminalmirror

import com.mufid.terminalmirror.model.PairingPayloadParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PairingPayloadParserTest {

    @Test
    fun testParseCompactUri() {
        val uri = "tm://172.23.127.184:8888/ws?s=mac-live-session&k=masmufid_super_secret_relay_2026&p=batu-merah-kuda-terbang"
        val payload = PairingPayloadParser.parse(uri)

        assertNotNull(payload)
        assertEquals("ws://172.23.127.184:8888/ws", payload?.relayUrl)
        assertEquals("mac-live-session", payload?.sessionId)
        assertEquals("masmufid_super_secret_relay_2026", payload?.preSharedKey)
        assertEquals("batu-merah-kuda-terbang", payload?.getFormattedPassphrase())
    }

    @Test
    fun testParseStandardJson() {
        val json = """
            {
                "relay_url": "ws://172.23.127.184:8888/ws",
                "session_id": "mac-live-session",
                "host_id": "macbook-pro",
                "pre_shared_key": "masmufid_super_secret_relay_2026",
                "passphrase_words": ["batu", "merah", "kuda", "terbang"]
            }
        """.trimIndent()

        val payload = PairingPayloadParser.parse(json)
        assertNotNull(payload)
        assertEquals("ws://172.23.127.184:8888/ws", payload?.relayUrl)
        assertEquals("mac-live-session", payload?.sessionId)
        assertEquals("batu-merah-kuda-terbang", payload?.getFormattedPassphrase())
    }
}
