package com.mufid.terminalmirror.terminal

object TerminalBufferProcessor {

    /**
     * Strips standard ANSI and VT100 control sequences.
     */
    fun stripAnsiCodes(input: String): String {
        return input
            .replace(Regex("\u001B\\[[;?0-9]*[a-zA-Z]"), "")
            .replace(Regex("\u001B\\([a-zA-Z]"), "")
            .replace(Regex("\u001B\\][0-9];[^\u0007]*\u0007"), "")
            .replace(Regex("\u001B[=>]"), "")
    }

    /**
     * Incrementally applies an incoming terminal stream chunk to the accumulated buffer,
     * correctly interpreting backspaces (\b, \u0008) to prevent Zsh ZLE duplicate characters,
     * and handling CRLF line endings.
     */
    fun processChunk(currentBuffer: String, incomingChunk: String, maxBufferLength: Int = 10000): String {
        val cleanChunk = stripAnsiCodes(incomingChunk)
        val sb = StringBuilder(currentBuffer)

        var i = 0
        while (i < cleanChunk.length) {
            val c = cleanChunk[i]
            when (c) {
                '\b', '\u007F' -> {
                    // Backspace or DEL: delete the previous character if available and not newline
                    if (sb.isNotEmpty() && sb.last() != '\n') {
                        sb.deleteCharAt(sb.length - 1)
                    }
                }
                '\r' -> {
                    // Consume any consecutive \r
                    while (i + 1 < cleanChunk.length && cleanChunk[i + 1] == '\r') {
                        i++
                    }
                    // Check if it's followed by \n (CRLF)
                    if (i + 1 < cleanChunk.length && cleanChunk[i + 1] == '\n') {
                        sb.append('\n')
                        i++ // Skip '\n'
                    } else {
                        // Bare carriage return: if followed by another chunk, standard terminal returns cursor
                    }
                }
                else -> {
                    sb.append(c)
                }
            }
            i++
        }

        return if (sb.length > maxBufferLength) {
            sb.substring(sb.length - maxBufferLength)
        } else {
            sb.toString()
        }
    }
}
