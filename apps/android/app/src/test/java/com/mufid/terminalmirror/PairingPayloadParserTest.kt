package com.mufid.terminalmirror

import com.mufid.terminalmirror.model.PairingPayloadParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PairingPayloadParserTest {

    @Test
    fun testParseCompactUri() {
        val uri = "tm://relay.example.internal:8888/ws?s=mac-live-session&k=TEST_RELAY_TOKEN&p=kilo-lima-sierra-tango"
        val payload = PairingPayloadParser.parse(uri)

        assertNotNull(payload)
        assertEquals("ws://relay.example.internal:8888/ws", payload?.relayUrl)
        assertEquals("mac-live-session", payload?.sessionId)
        assertEquals("TEST_RELAY_TOKEN", payload?.preSharedKey)
        assertEquals("kilo-lima-sierra-tango", payload?.getFormattedPassphrase())
    }

    @Test
    fun testParseStandardJson() {
        val json = """
            {
                "relay_url": "ws://relay.example.internal:8888/ws",
                "session_id": "mac-live-session",
                "host_id": "macbook-pro",
                "pre_shared_key": "TEST_RELAY_TOKEN",
                "passphrase_words": ["kilo", "lima", "sierra", "tango"]
            }
        """.trimIndent()

        val payload = PairingPayloadParser.parse(json)
        assertNotNull(payload)
        assertEquals("ws://relay.example.internal:8888/ws", payload?.relayUrl)
        assertEquals("mac-live-session", payload?.sessionId)
        assertEquals("kilo-lima-sierra-tango", payload?.getFormattedPassphrase())
    }
}
