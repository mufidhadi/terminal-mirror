package com.mufid.terminalmirror.terminal

/**
 * High-performance 2D Terminal Screen Matrix supporting ANSI VT100/xterm cursor addressing,
 * line/screen erasures, and carriage-return overwrites.
 *
 * This provides correct TUI (Text User Interface) animations (spinners, progress bars,
 * full-screen TUI apps like Antigravity CLI, vim, htop) without stacking stale intermediate frames.
 */
class TerminalScreenBuffer(
    val cols: Int = 80,
    val rows: Int = 24,
    private val maxScrollback: Int = 2000
) {
    private var grid: Array<CharArray> = Array(rows) { CharArray(cols) { ' ' } }
    private val altGrid: Array<CharArray> = Array(rows) { CharArray(cols) { ' ' } }
    private var isAltScreen: Boolean = false

    var cursorRow: Int = 0
        private set
    var cursorCol: Int = 0
        private set

    private val scrollback = ArrayDeque<String>()

    /**
     * Process an incoming raw or ANSI stream chunk from the Darwin PTY.
     */
    @Synchronized
    fun processChunk(chunk: String) {
        var i = 0
        val len = chunk.length

        while (i < len) {
            val c = chunk[i]

            if (c == '\u001B') {
                // Escape sequence parsing
                if (i + 1 < len) {
                    when (chunk[i + 1]) {
                        '[' -> {
                            // CSI sequence: ESC [ <params> <cmd>
                            var j = i + 2
                            while (j < len && (chunk[j] in '0'..'9' || chunk[j] == ';' || chunk[j] == '?' || chunk[j] == '=' || chunk[j] == '>')) {
                                j++
                            }
                            if (j < len) {
                                val cmd = chunk[j]
                                val paramsStr = chunk.substring(i + 2, j)
                                handleCsiCommand(cmd, paramsStr)
                                i = j + 1
                                continue
                            } else {
                                // Incomplete CSI at boundary
                                break
                            }
                        }
                        ']' -> {
                            // OSC sequence: ESC ] ... (BEL \u0007 or ST \u001B\\)
                            var j = i + 2
                            while (j < len && chunk[j] != '\u0007' && !(chunk[j] == '\u001B' && j + 1 < len && chunk[j + 1] == '\\')) {
                                j++
                            }
                            if (j < len) {
                                if (chunk[j] == '\u0007') {
                                    i = j + 1
                                } else {
                                    i = j + 2
                                }
                                continue
                            } else {
                                break
                            }
                        }
                        '(', ')' -> {
                            // Charset designation, e.g. ESC ( B
                            i = (i + 3).coerceAtMost(len)
                            continue
                        }
                        '=' , '>' -> {
                            // Keypad mode
                            i += 2
                            continue
                        }
                        'M' -> {
                            // Reverse index (cursor up / scroll down)
                            if (cursorRow > 0) {
                                cursorRow--
                            }
                            i += 2
                            continue
                        }
                        else -> {
                            // Unknown escape sequence
                            i += 2
                            continue
                        }
                    }
                } else {
                    i++
                    continue
                }
            }

            // Control characters
            when (c) {
                '\r' -> {
                    cursorCol = 0
                }
                '\n' -> {
                    lineFeed()
                }
                '\b', '\u007F' -> {
                    if (cursorCol > 0) {
                        cursorCol--
                        activeGrid()[cursorRow][cursorCol] = ' '
                    }
                }
                '\t' -> {
                    cursorCol = ((cursorCol + 8) / 8 * 8).coerceAtMost(cols - 1)
                }
                else -> {
                    if (c >= ' ') {
                        if (cursorCol >= cols) {
                            cursorCol = 0
                            lineFeed()
                        }
                        activeGrid()[cursorRow][cursorCol] = c
                        cursorCol++
                    }
                }
            }
            i++
        }
    }

    private fun handleCsiCommand(cmd: Char, params: String) {
        val currentGrid = activeGrid()

        when (cmd) {
            'H', 'f' -> {
                // Cursor Position: ESC [ <row> ; <col> H
                val parts = params.split(';')
                val r = parts.getOrNull(0)?.toIntOrNull() ?: 1
                val c = parts.getOrNull(1)?.toIntOrNull() ?: 1
                cursorRow = (r - 1).coerceIn(0, rows - 1)
                cursorCol = (c - 1).coerceIn(0, cols - 1)
            }
            'A' -> {
                // Cursor Up
                val n = params.toIntOrNull() ?: 1
                cursorRow = (cursorRow - n).coerceAtLeast(0)
            }
            'B' -> {
                // Cursor Down
                val n = params.toIntOrNull() ?: 1
                cursorRow = (cursorRow + n).coerceAtMost(rows - 1)
            }
            'C' -> {
                // Cursor Forward
                val n = params.toIntOrNull() ?: 1
                cursorCol = (cursorCol + n).coerceAtMost(cols - 1)
            }
            'D' -> {
                // Cursor Backward
                val n = params.toIntOrNull() ?: 1
                cursorCol = (cursorCol - n).coerceAtLeast(0)
            }
            'J' -> {
                // Erase in Display
                when (params) {
                    "", "0" -> {
                        // Clear from cursor to end of screen
                        for (c in cursorCol until cols) {
                            currentGrid[cursorRow][c] = ' '
                        }
                        for (r in (cursorRow + 1) until rows) {
                            currentGrid[r].fill(' ')
                        }
                    }
                    "1" -> {
                        // Clear from start to cursor
                        for (r in 0 until cursorRow) {
                            currentGrid[r].fill(' ')
                        }
                        for (c in 0..cursorCol.coerceAtMost(cols - 1)) {
                            currentGrid[cursorRow][c] = ' '
                        }
                    }
                    "2", "3" -> {
                        // Clear entire screen
                        for (r in 0 until rows) {
                            currentGrid[r].fill(' ')
                        }
                    }
                }
            }
            'K' -> {
                // Erase in Line
                when (params) {
                    "", "0" -> {
                        // Clear from cursor to end of line
                        for (c in cursorCol until cols) {
                            currentGrid[cursorRow][c] = ' '
                        }
                    }
                    "1" -> {
                        // Clear from start of line to cursor
                        for (c in 0..cursorCol.coerceAtMost(cols - 1)) {
                            currentGrid[cursorRow][c] = ' '
                        }
                    }
                    "2" -> {
                        // Clear entire line
                        currentGrid[cursorRow].fill(' ')
                    }
                }
            }
            'h' -> {
                if (params == "?1049") {
                    // Enter Alternate Screen Buffer (TUI mode)
                    isAltScreen = true
                    for (r in 0 until rows) {
                        altGrid[r].fill(' ')
                    }
                    cursorRow = 0
                    cursorCol = 0
                }
            }
            'l' -> {
                if (params == "?1049") {
                    // Leave Alternate Screen Buffer
                    isAltScreen = false
                }
            }
            'm' -> {
                // SGR color & styling code (can be extended for styled text)
            }
        }
    }

    private fun lineFeed() {
        cursorRow++
        if (cursorRow >= rows) {
            scrollUp()
            cursorRow = rows - 1
        }
    }

    private fun scrollUp() {
        val currentGrid = activeGrid()
        if (!isAltScreen) {
            val topRowText = String(currentGrid[0]).trimEnd()
            if (topRowText.isNotEmpty() || scrollback.isNotEmpty()) {
                if (scrollback.size >= maxScrollback) {
                    scrollback.removeFirst()
                }
                scrollback.addLast(topRowText)
            }
        }

        // Shift rows up by 1
        for (r in 0 until rows - 1) {
            System.arraycopy(currentGrid[r + 1], 0, currentGrid[r], 0, cols)
        }
        currentGrid[rows - 1].fill(' ')
    }

    private fun activeGrid(): Array<CharArray> {
        return if (isAltScreen) altGrid else grid
    }

    /**
     * Renders the current terminal screen as a formatted text string suitable for Compose Text.
     */
    @Synchronized
    fun renderScreen(): String {
        val currentGrid = activeGrid()
        val sb = StringBuilder()

        if (!isAltScreen && scrollback.isNotEmpty()) {
            val recentScrollback = scrollback.takeLast(100)
            for (line in recentScrollback) {
                sb.append(line).append('\n')
            }
        }

        // Find last non-empty row in active grid
        var lastNonEmptyRow = rows - 1
        while (lastNonEmptyRow > cursorRow && String(currentGrid[lastNonEmptyRow]).isBlank()) {
            lastNonEmptyRow--
        }
        lastNonEmptyRow = lastNonEmptyRow.coerceAtLeast(cursorRow)

        for (r in 0..lastNonEmptyRow) {
            val line = String(currentGrid[r]).trimEnd()
            sb.append(line)
            if (r < lastNonEmptyRow) {
                sb.append('\n')
            }
        }

        return sb.toString()
    }

    @Synchronized
    fun clear() {
        for (r in 0 until rows) {
            grid[r].fill(' ')
            altGrid[r].fill(' ')
        }
        scrollback.clear()
        cursorRow = 0
        cursorCol = 0
        isAltScreen = false
    }
}
