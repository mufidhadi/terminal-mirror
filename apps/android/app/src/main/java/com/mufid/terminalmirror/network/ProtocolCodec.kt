package com.mufid.terminalmirror.network

import com.fasterxml.jackson.databind.ObjectMapper
import com.mufid.terminalmirror.crypto.E2eeManager
import org.msgpack.jackson.dataformat.MessagePackFactory
import java.util.UUID

sealed class DecodedPayload {
    data class TerminalOutput(val text: String, val bytes: ByteArray, val sequence: Long = 0L) : DecodedPayload()
    data class ScreenSync(
        val lines: List<String>,
        val cursorX: Int = 0,
        val cursorY: Int = 0,
        val cols: Int = 80,
        val rows: Int = 24,
        val sequence: Long = 0L
    ) : DecodedPayload()
    data class SessionRevoked(val reason: String, val sequence: Long = 0L) : DecodedPayload()
    data class ProtocolError(val code: Int, val message: String, val sequence: Long = 0L) : DecodedPayload()
    data class HostPresence(
        val sessionId: String,
        val online: Boolean,
        val hostName: String?,
        val shell: String?,
        val timestampMs: Long = 0L
    ) : DecodedPayload()
    data class Unhandled(val type: String) : DecodedPayload()
}

class ProtocolCodec(private val e2eeManager: E2eeManager?) {
    private val mapper = ObjectMapper(MessagePackFactory())

    /**
     * Decodes incoming MessagePack binary payload received from the Relay Hub.
     */
    @Suppress("UNCHECKED_CAST")
    fun decodePacket(bytes: ByteArray): DecodedPayload? {
        return try {
            val root = mapper.readValue(bytes, Map::class.java) as Map<*, *>
            val payload = root["payload"] as? Map<*, *> ?: return null
            val type = payload["type"] as? String ?: return null
            val data = payload["data"] as? Map<*, *> ?: return null
            val sequence = (root["sequence"] as? Number)?.toLong() ?: 0L
            val timestampMs = (root["timestamp_ms"] as? Number)?.toLong() ?: 0L

            when (type) {
                "HostPresence" -> {
                    val sId = data["session_id"] as? String ?: (root["session_id"] as? String) ?: ""
                    val online = data["online"] as? Boolean ?: false
                    val hostName = data["host_name"] as? String
                    val shell = data["shell"] as? String
                    DecodedPayload.HostPresence(sId, online, hostName, shell, timestampMs)
                }
                "EncryptedBlob" -> {
                    val nonce = (data["nonce"] as? Number)?.toLong() ?: 0L
                    val ciphertext = when (val raw = data["ciphertext"]) {
                        is ByteArray -> raw
                        is List<*> -> (raw as List<Number>).map { it.toByte() }.toByteArray()
                        else -> return null
                    }
                    if (e2eeManager != null) {
                        val decrypted = e2eeManager.decrypt(nonce, ciphertext)
                        val text = String(decrypted, Charsets.UTF_8)
                        DecodedPayload.TerminalOutput(text, decrypted, sequence)
                    } else {
                        DecodedPayload.Unhandled("EncryptedBlob (no cipher configured)")
                    }
                }
                "TerminalOutput" -> {
                    val rawBytes = when (val raw = data["bytes"]) {
                        is ByteArray -> raw
                        is List<*> -> (raw as List<Number>).map { it.toByte() }.toByteArray()
                        else -> return null
                    }
                    val text = String(rawBytes, Charsets.UTF_8)
                    DecodedPayload.TerminalOutput(text, rawBytes, sequence)
                }
                "ScreenStateSync" -> {
                    val snap = data["snapshot"] as? Map<*, *> ?: return null
                    val lines = when (val raw = snap["lines"]) {
                        is List<*> -> raw.map { it.toString() }
                        else -> emptyList()
                    }
                    DecodedPayload.ScreenSync(
                        lines = lines,
                        cursorX = (snap["cursor_x"] as? Number)?.toInt() ?: 0,
                        cursorY = (snap["cursor_y"] as? Number)?.toInt() ?: 0,
                        cols = (snap["cols"] as? Number)?.toInt() ?: 80,
                        rows = (snap["rows"] as? Number)?.toInt() ?: 24,
                        sequence = sequence
                    )
                }
                "SessionRevoked" -> {
                    val reason = data["reason"] as? String ?: "revoked"
                    DecodedPayload.SessionRevoked(reason, sequence)
                }
                "Error" -> {
                    val code = (data["code"] as? Number)?.toInt() ?: 0
                    val message = data["message"] as? String ?: ""
                    DecodedPayload.ProtocolError(code, message, sequence)
                }
                else -> DecodedPayload.Unhandled(type)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Encodes remote keystroke into an encrypted MessagePack packet.
     */
    fun encodeKeystroke(sessionId: String, sequence: Long, keystrokeBytes: ByteArray): ByteArray {
        val payloadMap = if (e2eeManager != null) {
            val ciphertext = e2eeManager.encrypt(sequence, keystrokeBytes)
            mapOf(
                "type" to "EncryptedBlob",
                "data" to mapOf(
                    "nonce" to sequence,
                    "ciphertext" to ciphertext
                )
            )
        } else {
            mapOf(
                "type" to "TerminalInput",
                "data" to mapOf(
                    "bytes" to keystrokeBytes
                )
            )
        }

        val packet = mapOf(
            "version" to 1,
            "trace_id" to UUID.randomUUID().toString(),
            "session_id" to sessionId,
            "sequence" to sequence,
            "timestamp_ms" to System.currentTimeMillis(),
            "payload" to payloadMap
        )

        return mapper.writeValueAsBytes(packet)
    }
}
