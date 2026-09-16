package com.mufid.terminalmirror.model

enum class OsType {
    MACOS,
    WINDOWS,
    LINUX,
    ANDROID
}

data class TerminalSession(
    val sessionId: String,
    val hostId: String,
    val hostName: String,
    val osType: OsType,
    val shell: String,
    val isConnected: Boolean = false,
    val isReadOnly: Boolean = true // Safety default: prevent accidental mobile touch typing
)

data class TerminalPacket(
    val version: Int = 1,
    val traceId: String,
    val sessionId: String,
    val sequence: Long,
    val timestampMs: Long,
    val payloadType: String,
    val payloadBytes: ByteArray
)

data class PairingPayload(
    val relayUrl: String,
    val sessionId: String,
    val hostId: String,
    val preSharedKey: String,
    val publicKey: String = "",
    val pinCode: String? = null,
    val passphraseWords: List<String>? = null,
    val expiresAtMs: Long = 0L
) {
    fun getFormattedPassphrase(): String? {
        return passphraseWords?.joinToString("-")
    }
}
