package com.mufid.terminalmirror.network

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class ConnectionManager(
    private val scope: CoroutineScope,
    private val onSessionPayload: (sessionId: String, bytes: ByteArray) -> Unit,
    private val onSessionStatusChanged: (sessionId: String, isConnected: Boolean) -> Unit,
    private val onConnectionState: (sessionId: String, state: ConnectionState) -> Unit = { _, _ -> },
    private val onQueueChanged: (sessionId: String, queued: Int, dropped: Int) -> Unit = { _, _, _ -> },
    private val queueCapacity: Int = 200
) {
    private val activeClients = ConcurrentHashMap<String, RelayClient>()
    private val retryAttempts = ConcurrentHashMap<String, Int>()
    private val socketLive = ConcurrentHashMap<String, Boolean>()
    private val states = ConcurrentHashMap<String, ConnectionState>()
    private val pendingQueues = ConcurrentHashMap<String, OutboundQueue>()
    private val generations = ConcurrentHashMap<String, Long>()

    fun stateOf(sessionId: String): ConnectionState =
        states[sessionId] ?: ConnectionState.Disconnected

    fun queuedCount(sessionId: String): Int =
        pendingQueues[sessionId]?.size ?: 0

    fun droppedCount(sessionId: String): Int =
        pendingQueues[sessionId]?.droppedCount ?: 0

    private fun setState(sessionId: String, state: ConnectionState) {
        states[sessionId] = state
        onConnectionState(sessionId, state)
        onSessionStatusChanged(sessionId, state is ConnectionState.Connected)
    }

    fun connectSession(sessionId: String, relayUrl: String) {
        // Silent teardown: no Disconnected flicker on the way to Connecting.
        removeClient(sessionId)
        // Generation guard: a late callback from a replaced client must not
        // trigger reconnects or state changes for the new client.
        val generation = (generations[sessionId] ?: 0L) + 1
        generations[sessionId] = generation
        fun isCurrent(): Boolean = generations[sessionId] == generation

        val client = RelayClient(relayUrl, object : RelayClient.RelayListener {
            override fun onConnected() {
                if (!isCurrent()) return
                retryAttempts[sessionId] = 0
                socketLive[sessionId] = true
                drainQueue(sessionId)
                setState(sessionId, ConnectionState.Connected)
            }

            override fun onDisconnected(code: Int, reason: String) {
                if (!isCurrent()) return
                socketLive[sessionId] = false
                // Manual DISC removed the client: stay Disconnected, no retry.
                if (!activeClients.containsKey(sessionId)) {
                    setState(sessionId, ConnectionState.Disconnected)
                    return
                }
                scheduleReconnect(sessionId, generation)
            }

            override fun onBinaryMessage(bytes: ByteArray) {
                if (!isCurrent()) return
                onSessionPayload(sessionId, bytes)
            }

            override fun onError(t: Throwable) {
                if (!isCurrent()) return
                socketLive[sessionId] = false
                if (!activeClients.containsKey(sessionId)) {
                    setState(sessionId, ConnectionState.Disconnected)
                    return
                }
                scheduleReconnect(sessionId, generation)
            }
        })

        activeClients[sessionId] = client
        pendingQueues.getOrPut(sessionId) { OutboundQueue(queueCapacity) }
        setState(sessionId, ConnectionState.Connecting)
        client.connect()
    }

    private fun scheduleReconnect(sessionId: String, generation: Long) {
        val attempt = retryAttempts.getOrDefault(sessionId, 0) + 1
        retryAttempts[sessionId] = attempt
        val delayMs = ReconnectPolicy.nextDelayMs(attempt)
        setState(sessionId, ConnectionState.Reconnecting(attempt, delayMs))

        scope.launch {
            delay(delayMs)
            if (generations[sessionId] == generation && activeClients.containsKey(sessionId)) {
                activeClients[sessionId]?.connect()
            }
        }
    }

    private fun notifyQueueChanged(sessionId: String) {
        onQueueChanged(sessionId, queuedCount(sessionId), droppedCount(sessionId))
    }

    private fun drainQueue(sessionId: String) {
        val client = activeClients[sessionId] ?: return
        pendingQueues[sessionId]?.drain()?.forEach { client.sendBinary(it) }
        notifyQueueChanged(sessionId)
    }

    fun sendToSession(sessionId: String, bytes: ByteArray) {
        val client = activeClients[sessionId]
        // Socket liveness is tracked locally: RelayClient.sendBinary() drops
        // silently on a dead socket, so never hand it bytes unless open.
        if (client != null && socketLive.getOrDefault(sessionId, false)) {
            client.sendBinary(bytes)
        } else {
            pendingQueues.getOrPut(sessionId) { OutboundQueue(queueCapacity) }.enqueue(bytes)
            notifyQueueChanged(sessionId)
        }
    }

    private fun removeClient(sessionId: String) {
        activeClients.remove(sessionId)?.disconnect()
        retryAttempts.remove(sessionId)
        socketLive.remove(sessionId)
        pendingQueues.remove(sessionId)
        notifyQueueChanged(sessionId)
        // Bumping the generation silences late callbacks AND pending retry
        // loops from the replaced client.
        generations[sessionId] = (generations[sessionId] ?: 0L) + 1
    }

    fun disconnectSession(sessionId: String) {
        removeClient(sessionId)
        setState(sessionId, ConnectionState.Disconnected)
    }

    fun disconnectAll() {
        for (sessionId in activeClients.keys) {
            disconnectSession(sessionId)
        }
    }
}
