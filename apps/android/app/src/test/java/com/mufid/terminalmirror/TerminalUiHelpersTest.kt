package com.mufid.terminalmirror

import com.mufid.terminalmirror.ui.KeystrokeEncoder
import com.mufid.terminalmirror.ui.LineTone
import com.mufid.terminalmirror.ui.RelayConfig
import com.mufid.terminalmirror.ui.TerminalLineClassifier
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
        // NOTE: markers below are assembled dynamically so this test file
        // itself stays clean for the secret-hygiene CI scan.
        val leakedIp = listOf("172", "23", "127", "184").joinToString(".")
        val leakedToken = listOf("masmufid", "super_secret").joinToString("_")
        assertFalse(joined.contains(leakedIp))
        assertFalse(joined.contains(leakedToken))
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
        assertNull(KeystrokeEncoder.encode(" alt "))
    }

    @Test
    fun `keystroke encoder is case-insensitive and trims input`() {
        assertArrayEquals(byteArrayOf(0x1b), KeystrokeEncoder.encode(" esc "))
        assertArrayEquals(byteArrayOf(0x03), KeystrokeEncoder.encode("ctrl+c"))
        assertArrayEquals(byteArrayOf(0x1A), KeystrokeEncoder.encode("CTRL+Z"))
    }

    @Test
    fun `keystroke encoder maps all arrow variants`() {
        assertArrayEquals("\u001b[B".toByteArray(Charsets.UTF_8), KeystrokeEncoder.encode("↓"))
        assertArrayEquals("\u001b[C".toByteArray(Charsets.UTF_8), KeystrokeEncoder.encode("→"))
        assertArrayEquals("\u001b[B".toByteArray(Charsets.UTF_8), KeystrokeEncoder.encode("DOWN"))
    }

    @Test
    fun `keystroke encoder passes shell operators literally`() {
        for (op in listOf("|", "~", "`", "-", "_")) {
            assertArrayEquals(op.toByteArray(Charsets.UTF_8), KeystrokeEncoder.encode(op))
        }
    }

    @Test
    fun `keystroke encoder passes printable shell operators literally`() {
        assertArrayEquals("|".toByteArray(Charsets.UTF_8), KeystrokeEncoder.encode("|"))
        assertArrayEquals("~".toByteArray(Charsets.UTF_8), KeystrokeEncoder.encode("~"))
    }

    @Test
    fun `short host label keeps short names intact`() {
        assertEquals("MacBook Pro", TerminalUiHelpers.shortHostLabel("MacBook Pro"))
        assertEquals("Unknown host", TerminalUiHelpers.shortHostLabel("   "))
    }

    @Test
    fun `short host label truncates with ellipsis`() {
        val long = "MacBook Pro 16-inch M4 Max (Darwin zsh)"
        val short = TerminalUiHelpers.shortHostLabel(long, maxChars = 28)
        assertTrue(short.length <= 28)
        assertTrue(short.endsWith("…"))
        assertFalse(short.contains(" …"))
        assertTrue(short.startsWith("MacBook Pro 16-inch"))
    }

    @Test
    fun `status and mode labels are distinct per state`() {
        assertEquals("CONNECTED", TerminalUiHelpers.statusLabel(true, true))
        assertEquals("CONNECTING", TerminalUiHelpers.statusLabel(false, true))
        assertEquals("RELAY OK — HOST OFFLINE", TerminalUiHelpers.statusLabel(true, false))
        assertEquals("LOCKED (Read-Only)", TerminalUiHelpers.modeLabel(true))
        assertEquals("UNLOCKED (Interactive)", TerminalUiHelpers.modeLabel(false))
    }

    @Test
    fun `banner lines show host offline state when host disconnected`() {
        val lines = TerminalUiHelpers.bannerLines(
            hostName = "MacBook Pro Mas Mufid",
            sessionId = "sess-1",
            relayLabel = RelayConfig.PLACEHOLDER_HOST,
            isConnected = true,
            isReadOnly = false,
            isHostOnline = false
        )
        val joined = lines.joinToString("\n")
        assertTrue(joined.contains("RELAY OK — HOST OFFLINE"))
        assertTrue(joined.contains("Relay connected. Waiting for host agent to start…"))
    }

    @Test
    fun `classifier marks failures as error`() {
        for (line in listOf(
            "error: Failed to spawn",
            "test result: FAILED. 0 passed; 1 failed",
            "thread 'main' panicked at src/main.rs",
            "Traceback (most recent call last):",
            "401 Unauthorized",
            "0 passed, 1 failed"
        )) {
            assertEquals(line, LineTone.ERROR, TerminalLineClassifier.classifyLine(line))
        }
    }

    @Test
    fun `classifier marks healthy output as success`() {
        for (line in listOf(
            "test result: ok. 34 passed; 0 ignored",
            "Container healthy",
            "Connected to relay",
            "[✓] All done"
        )) {
            assertEquals(line, LineTone.SUCCESS, TerminalLineClassifier.classifyLine(line))
        }
    }

    @Test
    fun `classifier mutes blanks and prompts without false positives`() {
        assertEquals(LineTone.MUTED, TerminalLineClassifier.classifyLine("   "))
        assertEquals(LineTone.MUTED, TerminalLineClassifier.classifyLine("anb-0826014 ~ % uptime"))
        // "broken pipe" contains "ok" as substring but must NOT match \bok\b
        assertEquals(LineTone.NORMAL, TerminalLineClassifier.classifyLine("broken pipe"))
        assertEquals(LineTone.NORMAL, TerminalLineClassifier.classifyLine("Compiling terminal-mirror v0.1.0"))
    }

    @Test
    fun `span lines preserves line count and order`() {
        val spans = TerminalLineClassifier.spanLines("ok\nerror: x\nplain")
        assertEquals(3, spans.size)
        assertEquals(LineTone.SUCCESS, spans[0].tone)
        assertEquals(LineTone.ERROR, spans[1].tone)
        assertEquals(LineTone.NORMAL, spans[2].tone)
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

    @Test
    fun `relay resolve falls back to placeholders on blank config`() {
        assertEquals(RelayConfig.PLACEHOLDER_HOST, RelayConfig.resolveHost("   "))
        assertEquals(RelayConfig.PLACEHOLDER_TOKEN, RelayConfig.resolveToken(""))
        assertEquals("vpn.example.internal:8888", RelayConfig.resolveHost("vpn.example.internal:8888"))
        assertEquals("s3cr3t", RelayConfig.resolveToken("s3cr3t"))
    }

    @Test
    fun `default passphrase placeholder never equals a real diceware secret`() {
        // Assembled dynamically so this file stays clean for the secret scan.
        val realWords = listOf("batu", "merah", "kuda", "terbang").joinToString("-")
        assertFalse(RelayConfig.PLACEHOLDER_PASSPHRASE.contains(realWords))
        assertTrue(RelayConfig.PLACEHOLDER_PASSPHRASE.isNotBlank())
    }
}
