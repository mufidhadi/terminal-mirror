package com.mufid.terminalmirror.network

import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

open class RelayClient(
    private val relayUrl: String,
    protected val listener: RelayListener
) {
    interface RelayListener {
        fun onConnected()
        fun onDisconnected(code: Int, reason: String)
        fun onBinaryMessage(bytes: ByteArray)
        fun onError(t: Throwable)
        fun onAuthError(message: String) {
            onError(SecurityException(message))
        }
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    /**
     * Epoch guard (residual race from Report 030): callbacks from a socket
     * superseded by a newer connect()/disconnect() on this same client are
     * dropped, so a stale failure can never flip liveness after a fresh open.
     */
    private var connectEpoch = 0L

    open fun connect() {
        val myEpoch = ++connectEpoch
        fun isCurrent(): Boolean = myEpoch == connectEpoch

        val request = Request.Builder()
            .url(relayUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                if (!isCurrent()) return
                listener.onConnected()
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                if (!isCurrent()) return
                listener.onBinaryMessage(bytes.toByteArray())
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                if (!isCurrent()) return
                ws.close(code, reason)
                listener.onDisconnected(code, reason)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (!isCurrent()) return
                val isAuthError = response?.code == 401 || t.message?.contains("401") == true
                if (isAuthError) {
                    listener.onAuthError("Relay authentication failed (HTTP 401 Unauthorized)")
                } else {
                    listener.onError(t)
                }
            }
        })
    }

    fun sendBinary(bytes: ByteArray) {
        webSocket?.send(ByteString.of(*bytes))
    }

    open fun disconnect() {
        connectEpoch++
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }
}
