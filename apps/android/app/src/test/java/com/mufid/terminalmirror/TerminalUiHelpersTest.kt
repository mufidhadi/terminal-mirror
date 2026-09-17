package com.mufid.terminalmirror

import com.mufid.terminalmirror.ui.KeystrokeEncoder
import com.mufid.terminalmirror.ui.RelayConfig
import com.mufid.terminalmirror.ui.TerminalUiHelpers
import org.junit.Assert.*
import org.junit.Test

class TerminalUiHelpersTest {

    @Test
    fun `subtitle stays single line and trimmed`() {
        val subtitle = TerminalUiHelpers.sessionSubtitle("MacBook Pro (Darwin zsh)", "/bin/zsh")
        assertFalse(subtitle.contains("\n"))
        assertEquals("MacBook Pro (Darwin zsh) • /bin/zsh", subtitle)
    }

    @Test
    fun `subtitle falls back when blank`() {
        val subtitle = TerminalUiHelpers.sessionSubtitle("  ", "  ")
        assertEquals("Unknown host", subtitle)
    }

    @Test
    fun `banner lines never contain fixed ascii box and never leak real relay ip`() {
        val lines = TerminalUiHelpers.bannerLines(
            hostName = "MacBook Pro (Darwin zsh)",
            sessionId = "mac-live-session",
            relayLabel = RelayConfig.PLACEHOLDER_HOST,
            isConnected = false,
            isReadOnly = false
        )
        val joined = lines.joinToString("\n")
        assertFalse(joined.contains("┌"))
        assertFalse(joined.contains("┘"))
        assertFalse(joined.contains("172.23.127.184"))
        assertFalse(joined.contains("masmufid_super_secret"))
        lines.forEach { line ->
            assertTrue("line too long for mobile viewport: $line", line.length <= 64)
        }
        assertTrue(lines.any { it.contains("CONNECTING") })
        assertTrue(lines.any { it.contains("UNLOCKED") })
    }

    @Test
    fun `banner connected and locked states render distinctly`() {
        val lines = TerminalUiHelpers.bannerLines(
            hostName = "ThinkPad Win (pwsh)",
            sessionId = "win-live-session",
            relayLabel = RelayConfig.PLACEHOLDER_HOST,
            isConnected = true,
            isReadOnly = true
        )
        val joined = lines.joinToString("\n")
        assertTrue(joined.contains("CONNECTED"))
        assertTrue(joined.contains("LOCKED"))
    }

    @Test
    fun `keystroke encoder maps control keys to bytes`() {
        assertArrayEquals("\r".toByteArray(Charsets.UTF_8), KeystrokeEncoder.encode("ENTER"))
        assertArrayEquals("\t".toByteArray(Charsets.UTF_8), KeystrokeEncoder.encode("TAB"))
        assertArrayEquals(byteArrayOf(0x1b), KeystrokeEncoder.encode("ESC"))
        assertArrayEquals(byteArrayOf(0x03), KeystrokeEncoder.encode("CTRL+C"))
        assertArrayEquals(byteArrayOf(0x04), KeystrokeEncoder.encode("CTRL+D"))
        assertArrayEquals("\u001b[A".toByteArray(Charsets.UTF_8), KeystrokeEncoder.encode("UP"))
        assertArrayEquals("\u001b[A".toByteArray(Charsets.UTF_8), KeystrokeEncoder.encode("↑"))
        assertArrayEquals("\u001b[D".toByteArray(Charsets.UTF_8), KeystrokeEncoder.encode("←"))
    }

    @Test
    fun `keystroke encoder never sends bare modifiers as text`() {
        assertNull(KeystrokeEncoder.encode("CTRL"))
        assertNull(KeystrokeEncoder.encode("ALT"))
    }

    @Test
    fun `keystroke encoder passes printable shell operators literally`() {
        assertArrayEquals("|".toByteArray(Charsets.UTF_8), KeystrokeEncoder.encode("|"))
        assertArrayEquals("~".toByteArray(Charsets.UTF_8), KeystrokeEncoder.encode("~"))
    }

    @Test
    fun `relay url builder rejects blank secrets`() {
        try {
            RelayConfig.wsUrl("relay.example.internal:8888", "", "mac-live-session")
            fail("expected IllegalArgumentException for blank token")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `relay url builder produces well-formed url`() {
        val url = RelayConfig.wsUrl("relay.example.internal:8888", "TOKEN", "mac-live-session")
        assertEquals(
            "ws://relay.example.internal:8888/ws?token=TOKEN&session_id=mac-live-session&role=client",
            url
        )
    }
}
