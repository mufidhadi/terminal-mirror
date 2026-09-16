package com.mufid.terminalmirror.network

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.random.Random

class ConnectionManager(
    private val scope: CoroutineScope,
    private val onSessionPayload: (sessionId: String, bytes: ByteArray) -> Unit,
    private val onSessionStatusChanged: (sessionId: String, isConnected: Boolean) -> Unit
) {
    private val activeClients = ConcurrentHashMap<String, RelayClient>()
    private val retryAttempts = ConcurrentHashMap<String, Int>()

    fun connectSession(sessionId: String, relayUrl: String) {
        disconnectSession(sessionId)

        val client = RelayClient(relayUrl, object : RelayClient.RelayListener {
            override fun onConnected() {
                retryAttempts[sessionId] = 0
                onSessionStatusChanged(sessionId, true)
            }

            override fun onDisconnected(code: Int, reason: String) {
                onSessionStatusChanged(sessionId, false)
                scheduleReconnect(sessionId, relayUrl)
            }

            override fun onBinaryMessage(bytes: ByteArray) {
                onSessionPayload(sessionId, bytes)
            }

            override fun onError(t: Throwable) {
                onSessionStatusChanged(sessionId, false)
                scheduleReconnect(sessionId, relayUrl)
            }
        })

        activeClients[sessionId] = client
        client.connect()
    }

    private fun scheduleReconnect(sessionId: String, relayUrl: String) {
        val attempt = retryAttempts.getOrDefault(sessionId, 0) + 1
        retryAttempts[sessionId] = attempt

        // Exponential backoff with randomized jitter: min(30000, 500 * 2^attempt) + random(0..1000)
        val baseDelay = min(30000L, 500L * (1L shl min(attempt, 6)))
        val jitter = Random.nextLong(0, 1000L)
        val totalDelay = baseDelay + jitter

        scope.launch {
            delay(totalDelay)
            if (activeClients.containsKey(sessionId)) {
                activeClients[sessionId]?.connect()
            }
        }
    }

    fun sendToSession(sessionId: String, bytes: ByteArray) {
        activeClients[sessionId]?.sendBinary(bytes)
    }

    fun disconnectSession(sessionId: String) {
        activeClients.remove(sessionId)?.disconnect()
        retryAttempts.remove(sessionId)
        onSessionStatusChanged(sessionId, false)
    }

    fun disconnectAll() {
        for (sessionId in activeClients.keys) {
            disconnectSession(sessionId)
        }
    }
}
