package com.mufid.terminalmirror

import com.fasterxml.jackson.databind.ObjectMapper
import com.mufid.terminalmirror.network.DecodedPayload
import com.mufid.terminalmirror.network.ProtocolCodec
import org.junit.Assert.*
import org.junit.Test
import org.msgpack.jackson.dataformat.MessagePackFactory

class ProtocolCodecTest {

    private val codec = ProtocolCodec(null)
    private val mapper = ObjectMapper(MessagePackFactory())

    private fun packetBytes(payloadType: String, data: Map<String, Any?>, sequence: Long = 7L): ByteArray {
        val packet = mapOf(
            "version" to 1,
            "trace_id" to "trace-test",
            "session_id" to "sess-1",
            "sequence" to sequence,
            "timestamp_ms" to 1726532000000L,
            "payload" to mapOf("type" to payloadType, "data" to data)
        )
        return mapper.writeValueAsBytes(packet)
    }

    @Test
    fun `decode TerminalOutput preserves sequence`() {
        val bytes = packetBytes("TerminalOutput", mapOf("bytes" to "hi".toByteArray()), 42L)
        val out = codec.decodePacket(bytes) as DecodedPayload.TerminalOutput
        assertEquals("hi", out.text)
        assertEquals(42L, out.sequence)
    }

    @Test
    fun `decode ScreenStateSync exposes snapshot lines`() {
        val bytes = packetBytes(
            "ScreenStateSync",
            mapOf(
                "snapshot" to mapOf(
                    "cols" to 80,
                    "rows" to 24,
                    "cursor_x" to 3,
                    "cursor_y" to 1,
                    "in_alternate_screen" to false,
                    "lines" to listOf("alpha", "beta")
                )
            )
        )
        val out = codec.decodePacket(bytes) as DecodedPayload.ScreenSync
        assertEquals(listOf("alpha", "beta"), out.lines)
        assertEquals(3, out.cursorX)
        assertEquals(1, out.cursorY)
    }

    @Test
    fun `decode SessionRevoked exposes reason`() {
        val bytes = packetBytes("SessionRevoked", mapOf("reason" to "kill-switch"))
        val out = codec.decodePacket(bytes) as DecodedPayload.SessionRevoked
        assertEquals("kill-switch", out.reason)
    }

    @Test
    fun `decode Error exposes code and message`() {
        val bytes = packetBytes("Error", mapOf("code" to 4401, "message" to "unauthorized"))
        val out = codec.decodePacket(bytes) as DecodedPayload.ProtocolError
        assertEquals(4401, out.code)
        assertEquals("unauthorized", out.message)
    }

    @Test
    fun `unknown type stays Unhandled`() {
        val bytes = packetBytes("Ping", mapOf("nonce" to 1))
        val out = codec.decodePacket(bytes) as DecodedPayload.Unhandled
        assertEquals("Ping", out.type)
    }

    @Test
    fun `decode HostPresence online true parses metadata correctly`() {
        val bytes = packetBytes(
            "HostPresence",
            mapOf(
                "session_id" to "sess-1",
                "online" to true,
                "host_name" to "MacBook Pro Mas Mufid",
                "shell" to "/bin/zsh"
            )
        )
        val out = codec.decodePacket(bytes) as DecodedPayload.HostPresence
        assertEquals("sess-1", out.sessionId)
        assertTrue(out.online)
        assertEquals("MacBook Pro Mas Mufid", out.hostName)
        assertEquals("/bin/zsh", out.shell)
        assertEquals(1726532000000L, out.timestampMs)
    }

    @Test
    fun `decode HostPresence online false parses correctly`() {
        val bytes = packetBytes(
            "HostPresence",
            mapOf(
                "session_id" to "sess-1",
                "online" to false,
                "host_name" to null,
                "shell" to null
            )
        )
        val out = codec.decodePacket(bytes) as DecodedPayload.HostPresence
        assertEquals("sess-1", out.sessionId)
        assertFalse(out.online)
        assertNull(out.hostName)
        assertNull(out.shell)
    }
}
