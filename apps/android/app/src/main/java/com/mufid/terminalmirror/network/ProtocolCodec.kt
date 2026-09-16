package com.mufid.terminalmirror.network

import com.fasterxml.jackson.databind.ObjectMapper
import com.mufid.terminalmirror.crypto.E2eeManager
import org.msgpack.jackson.dataformat.MessagePackFactory
import java.util.UUID

sealed class DecodedPayload {
    data class TerminalOutput(val text: String, val bytes: ByteArray) : DecodedPayload()
    data class Unhandled(val type: String) : DecodedPayload()
}

class ProtocolCodec(private val e2eeManager: E2eeManager?) {
    private val mapper = ObjectMapper(MessagePackFactory())

    /**
     * Decodes incoming MessagePack binary payload received from the Relay Hub.
     */
    fun decodePacket(bytes: ByteArray): DecodedPayload? {
        return try {
            val root = mapper.readValue(bytes, Map::class.java) as? Map<*, *> ?: return null
            val payload = root["payload"] as? Map<*, *> ?: return null
            val type = payload["type"] as? String ?: return null
            val data = payload["data"] as? Map<*, *> ?: return null

            when (type) {
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
                        DecodedPayload.TerminalOutput(text, decrypted)
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
                    DecodedPayload.TerminalOutput(text, rawBytes)
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
