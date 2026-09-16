package com.mufid.terminalmirror

import com.mufid.terminalmirror.terminal.TerminalBufferProcessor
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalBufferProcessorTest {

    @Test
    fun `test backspace removes preceding character`() {
        val initial = "anb-0826014 ~ % u"
        val chunk = "\u0008uptime"
        val result = TerminalBufferProcessor.processChunk(initial, chunk)
        assertEquals("anb-0826014 ~ % uptime", result)
    }

    @Test
    fun `test zsh zle first character reprint scenario eliminates duplicate`() {
        val initial = ""
        val chunk1 = "e"
        val intermediate = TerminalBufferProcessor.processChunk(initial, chunk1)
        assertEquals("e", intermediate)

        val chunk2 = "\u0008echo E2EE_HELLO_FROM_MOBILE"
        val result = TerminalBufferProcessor.processChunk(intermediate, chunk2)
        assertEquals("echo E2EE_HELLO_FROM_MOBILE", result)
    }

    @Test
    fun `test multiple consecutive backspaces`() {
        val initial = "hello world"
        val chunk = "\b\b\b\b\bAndroid"
        val result = TerminalBufferProcessor.processChunk(initial, chunk)
        assertEquals("hello Android", result)
    }

    @Test
    fun `test crlf newline formatting`() {
        val initial = ""
        val chunk = "first line\r\nsecond line"
        val result = TerminalBufferProcessor.processChunk(initial, chunk)
        assertEquals("first line\nsecond line", result)
    }

    @Test
    fun `test ansi escape codes are stripped cleanly`() {
        val initial = ""
        val chunk = "\u001B[32mSUCCESS\u001B[0m\r\n"
        val result = TerminalBufferProcessor.processChunk(initial, chunk)
        assertEquals("SUCCESS\n", result)
    }

    @Test
    fun `test exact darwin pty chunk stream for uptime eliminates duplicate first char`() {
        val chunk0 = "u"
        val chunk1 = "\u0008uptime"
        val chunk2 = "\u001B[?2004l\r\r\n"
        val chunk3 = " 6:32  up 28 days, 22:58, 17 users, load averages: 3.28 3.79 3.79\r\n"

        var buffer = ""
        buffer = TerminalBufferProcessor.processChunk(buffer, chunk0)
        assertEquals("u", buffer)

        buffer = TerminalBufferProcessor.processChunk(buffer, chunk1)
        assertEquals("uptime", buffer) // NOT uuptime!

        buffer = TerminalBufferProcessor.processChunk(buffer, chunk2)
        assertEquals("uptime\n", buffer)

        buffer = TerminalBufferProcessor.processChunk(buffer, chunk3)
        assertEquals("uptime\n 6:32  up 28 days, 22:58, 17 users, load averages: 3.28 3.79 3.79\n", buffer)
    }

    @Test
    fun `test del ascii 0x7F removes character`() {
        val initial = "abc"
        val chunk = "\u007F"
        val result = TerminalBufferProcessor.processChunk(initial, chunk)
        assertEquals("ab", result)
    }
}
