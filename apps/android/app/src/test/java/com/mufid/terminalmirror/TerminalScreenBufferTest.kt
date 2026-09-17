package com.mufid.terminalmirror

import com.mufid.terminalmirror.terminal.TerminalScreenBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalScreenBufferTest {

    @Test
    fun testBasicTextWritingAndCursorAdvance() {
        val buffer = TerminalScreenBuffer(cols = 80, rows = 24)
        buffer.processChunk("Hello World")
        assertEquals("Hello World", buffer.renderScreen().trim())
    }

    @Test
    fun testCarriageReturnOverwritesFromStartOfLine() {
        val buffer = TerminalScreenBuffer(cols = 80, rows = 24)
        buffer.processChunk("Loading 10%")
        buffer.processChunk("\rLoading 50%")
        assertEquals("Loading 50%", buffer.renderScreen().trim())
    }

    @Test
    fun testAnsiClearLine2K() {
        val buffer = TerminalScreenBuffer(cols = 80, rows = 24)
        buffer.processChunk("Some temporary status message")
        buffer.processChunk("\r\u001B[2KFinished!")
        assertEquals("Finished!", buffer.renderScreen().trim())
    }

    @Test
    fun testAnsiClearScreen2J() {
        val buffer = TerminalScreenBuffer(cols = 80, rows = 24)
        buffer.processChunk("Old Screen Content\r\nSecond Line")
        buffer.processChunk("\u001B[2J\u001B[HFresh Screen")
        assertEquals("Fresh Screen", buffer.renderScreen().trim())
    }

    @Test
    fun testAnsiCursorPositioning() {
        val buffer = TerminalScreenBuffer(cols = 80, rows = 24)
        buffer.processChunk("Line 1\r\nLine 2\r\nLine 3")
        // Move cursor to row 1, col 1 and overwrite
        buffer.processChunk("\u001B[1;1HHeader")
        val lines = buffer.renderScreen().lines()
        assertEquals("Header", lines[0])
        assertEquals("Line 2", lines[1])
        assertEquals("Line 3", lines[2])
    }

    @Test
    fun testAnsiCursorUp() {
        val buffer = TerminalScreenBuffer(cols = 80, rows = 24)
        buffer.processChunk("First Line\r\nSecond Line")
        // Cursor up 1 line and carriage return
        buffer.processChunk("\u001B[1A\rUpdated First")
        val lines = buffer.renderScreen().lines()
        assertEquals("Updated First", lines[0])
        assertEquals("Second Line", lines[1])
    }

    @Test
    fun testTuiProgressAnimationSimulation() {
        val buffer = TerminalScreenBuffer(cols = 80, rows = 24)
        buffer.processChunk("Prompt > \r\n")
        buffer.processChunk("• Thought for 1s (fetching...)\r")
        buffer.processChunk("• Thought for 2s (fetching...)\r")
        buffer.processChunk("• Thought for 3s (completed!)\r\n")
        buffer.processChunk("Done!")

        val rendered = buffer.renderScreen()
        // Must NOT contain duplicate intermediate progress frames stacked vertically!
        assertFalse(rendered.contains("Thought for 1s"))
        assertFalse(rendered.contains("Thought for 2s"))
        assertTrue(rendered.contains("Thought for 3s (completed!)"))
        assertTrue(rendered.contains("Done!"))
    }

    @Test
    fun testZleFirstCharDuplicateScenario() {
        val buffer = TerminalScreenBuffer(cols = 80, rows = 24)
        buffer.processChunk("u")
        buffer.processChunk("\u0008uptime\r\n")
        val rendered = buffer.renderScreen()
        assertFalse(rendered.contains("uuptime"))
        assertTrue(rendered.contains("uptime"))
    }
}
