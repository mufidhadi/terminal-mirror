package com.mufid.terminalmirror.ui

/**
 * Pure, unit-testable helpers for the Android terminal UI.
 *
 * These exist so layout-critical decisions (single-line subtitles, viewport-safe
 * banner lines, keystroke byte mapping, relay URL construction) are verified by
 * JVM unit tests instead of only by manual screenshot inspection.
 */
object TerminalUiHelpers {

    fun sessionSubtitle(hostName: String, shell: String): String {
        val host = hostName.trim()
        val sh = shell.trim()
        if (host.isEmpty() && sh.isEmpty()) return "Unknown host"
        if (host.isEmpty()) return sh
        if (sh.isEmpty()) return host
        return "$host • $sh"
    }

    /**
     * Pre-truncates long workstation names for the tab row so a single tab
     * can never push siblings off-screen. Compose `ellipsis` remains as a
     * second safety net for extreme font-scale settings.
     */
    fun shortHostLabel(hostName: String, maxChars: Int = 28): String {
        val host = hostName.trim()
        if (host.isEmpty()) return "Unknown host"
        require(maxChars >= 4) { "maxChars must leave room for the ellipsis" }
        if (host.length <= maxChars) return host
        return host.take(maxChars - 1).trimEnd() + "…"
    }

    /**
     * Viewport-safe status lines. Deliberately avoids fixed-width ASCII box
     * drawing (which wraps and breaks on ~360dp phone viewports) and never
     * embeds real relay IPs or tokens. Callers join with "\n" into a
     * monospace Text composable.
     */
    fun bannerLines(
        hostName: String?,
        sessionId: String?,
        relayLabel: String,
        isConnected: Boolean,
        isReadOnly: Boolean
    ): List<String> {
        val status = if (isConnected) "CONNECTED" else "CONNECTING"
        val mode = if (isReadOnly) "LOCKED (Read-Only)" else "UNLOCKED (Interactive)"
        return listOf(
            "Terminal Mirror — Android Client",
            "Host: ${hostName?.trim().orEmpty().ifEmpty { "Unknown host" }}",
            "Session: ${sessionId?.trim().orEmpty().ifEmpty { "-" }}",
            "Relay: ${relayLabel.trim().ifEmpty { RelayConfig.PLACEHOLDER_HOST }}",
            "Status: $status",
            "Mode: $mode",
            "",
            if (isConnected) "Streaming live PTY frames…" else "Waiting for PTY frames…"
        )
    }
}

object KeystrokeEncoder {

    /**
     * Maps an accessory-bar label to the exact bytes that must go upstream.
     * Returns null for bare modifiers (CTRL / ALT) which must never be sent
     * as literal text.
     */
    fun encode(label: String): ByteArray? {
        return when (label.trim().uppercase()) {
            "ENTER", "\r", "\n" -> "\r".toByteArray(Charsets.UTF_8)
            "TAB", "\t" -> "\t".toByteArray(Charsets.UTF_8)
            "ESC" -> byteArrayOf(0x1b)
            "CTRL+C" -> byteArrayOf(0x03)
            "CTRL+D" -> byteArrayOf(0x04)
            "CTRL+Z" -> byteArrayOf(0x1A)
            "CTRL", "ALT" -> null
            "UP", "↑" -> "\u001b[A".toByteArray(Charsets.UTF_8)
            "DOWN", "↓" -> "\u001b[B".toByteArray(Charsets.UTF_8)
            "LEFT", "←" -> "\u001b[D".toByteArray(Charsets.UTF_8)
            "RIGHT", "→" -> "\u001b[C".toByteArray(Charsets.UTF_8)
            else -> label.toByteArray(Charsets.UTF_8)
        }
    }
}

object RelayConfig {
    const val PLACEHOLDER_HOST = "relay.example.internal:8888"
    const val PLACEHOLDER_TOKEN = "RELAY_TOKEN_PLACEHOLDER"
    const val PLACEHOLDER_PASSPHRASE = "REPLACE_WITH_PAIRED_PASSPHRASE"

    /**
     * Resolves a configured value (from BuildConfig, sourced from the
     * git-ignored `local.properties` or CI env) to a safe effective value.
     * Blank config falls back to placeholders that never touch production.
     */
    fun resolveHost(configured: String): String =
        configured.trim().ifEmpty { PLACEHOLDER_HOST }

    fun resolveToken(configured: String): String =
        configured.trim().ifEmpty { PLACEHOLDER_TOKEN }

    fun wsUrl(host: String, token: String, sessionId: String): String {
        require(host.isNotBlank()) { "relay host must not be blank" }
        require(token.isNotBlank()) { "relay token must not be blank" }
        require(sessionId.isNotBlank()) { "session id must not be blank" }
        return "ws://$host/ws?token=$token&session_id=$sessionId&role=client"
    }
}
