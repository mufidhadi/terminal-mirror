package com.mufid.terminalmirror.network

import kotlin.math.min
import kotlin.random.Random

/**
 * Explicit connection lifecycle per session (WS7).
 *
 * The UI previously knew only a boolean `isConnected`, so keystrokes typed
 * during transient drops were silently lost. Every state below is observable
 * and drives distinct UI: CONNECTED (input live), CONNECTING/RECONNECTING
 * (countdown + disabled input), DISCONNECTED (explicit, via DISC button).
 */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Reconnecting(val attempt: Int, val delayMs: Long) : ConnectionState
    data class AuthFailed(val message: String) : ConnectionState
}

/**
 * Pure backoff policy: min(30000, 500 * 2^attempt) + jitter(0..1000ms).
 * Extracted for JVM unit tests — no coroutines, no network.
 */
object ReconnectPolicy {
    const val MAX_DELAY_MS = 30000L
    const val BASE_MS = 500L
    const val JITTER_MS = 1000L

    fun baseDelayMs(attempt: Int): Long =
        min(MAX_DELAY_MS, BASE_MS * (1L shl min(attempt, 6)))

    fun nextDelayMs(attempt: Int): Long =
        baseDelayMs(attempt) + Random.nextLong(0, JITTER_MS)
}

/**
 * Bounded per-session outbound queue. Keystrokes produced while the socket
 * is down are held (not dropped) and drained in order on reconnect.
 * Beyond capacity the oldest entries are dropped and counted so the UI can
 * say so honestly instead of pretending delivery.
 */
class OutboundQueue(private val capacity: Int = 200) {
    private val deque = ArrayDeque<ByteArray>()
    var droppedCount: Int = 0
        private set

    val size: Int get() = deque.size

    fun enqueue(bytes: ByteArray) {
        if (deque.size >= capacity) {
            deque.removeFirst()
            droppedCount++
        }
        deque.addLast(bytes)
    }

    fun drain(): List<ByteArray> {
        val out = deque.toList()
        deque.clear()
        return out
    }

    fun clear() {
        deque.clear()
        droppedCount = 0
    }
}
