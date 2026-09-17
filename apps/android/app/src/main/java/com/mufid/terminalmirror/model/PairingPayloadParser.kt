package com.mufid.terminalmirror.model

import org.json.JSONObject

object PairingPayloadParser {

    /**
     * Parses pairing payload from either standard JSON or compact tm:// URI scheme.
     */
    fun parse(rawStr: String): PairingPayload? {
        return try {
            val trimmed = rawStr.trim()
            if (trimmed.startsWith("tm://")) {
                val withoutScheme = trimmed.removePrefix("tm://")
                val parts = withoutScheme.split("?", limit = 2)
                val hostPath = parts[0]
                val query = if (parts.size > 1) parts[1] else ""

                val relayUrl = if (hostPath.startsWith("127.0.0.1") || hostPath.startsWith("172.") || hostPath.startsWith("192.") || hostPath.startsWith("10.")) {
                    "ws://$hostPath"
                } else {
                    "wss://$hostPath"
                }

                var sessionId = ""
                var hostId = "macbook-pro"
                var psk = ""
                var pubKey = ""
                var pin: String? = null
                var passphraseList: List<String>? = null
                var expiresAt = 0L

                val params = query.split("&")
                for (param in params) {
                    val kv = param.split("=", limit = 2)
                    if (kv.size == 2) {
                        when (kv[0]) {
                            "s" -> sessionId = kv[1]
                            "h" -> hostId = kv[1]
                            "k" -> psk = kv[1]
                            "pub" -> pubKey = kv[1]
                            "pin" -> pin = kv[1]
                            "p" -> passphraseList = kv[1].split("-")
                            "exp" -> expiresAt = kv[1].toLongOrNull() ?: 0L
                        }
                    }
                }

                return PairingPayload(
                    relayUrl = relayUrl,
                    sessionId = sessionId,
                    hostId = hostId,
                    preSharedKey = psk,
                    publicKey = pubKey,
                    pinCode = pin,
                    passphraseWords = passphraseList,
                    expiresAtMs = expiresAt
                )
            }

            val json = JSONObject(trimmed)
            val relayUrl = json.getString("relay_url")
            val sessionId = json.getString("session_id")
            val hostId = json.optString("host_id", "unknown-host")
            val psk = json.optString("pre_shared_key", "")
            val pubKey = json.optString("public_key", "")
            val pin = if (json.has("pin_code") && !json.isNull("pin_code")) json.getString("pin_code") else null

            val passphraseList = mutableListOf<String>()
            if (json.has("passphrase_words") && !json.isNull("passphrase_words")) {
                val arr = json.getJSONArray("passphrase_words")
                for (i in 0 until arr.length()) {
                    passphraseList.add(arr.getString(i))
                }
            }

            val expiresAt = json.optLong("expires_at_ms", 0L)

            PairingPayload(
                relayUrl = relayUrl,
                sessionId = sessionId,
                hostId = hostId,
                preSharedKey = psk,
                publicKey = pubKey,
                pinCode = pin,
                passphraseWords = if (passphraseList.isNotEmpty()) passphraseList else null,
                expiresAtMs = expiresAt
            )
        } catch (e: Exception) {
            null
        }
    }
}
