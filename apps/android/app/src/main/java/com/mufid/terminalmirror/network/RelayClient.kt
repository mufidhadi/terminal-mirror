package com.mufid.terminalmirror.network

import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

class RelayClient(
    private val relayUrl: String,
    private val listener: RelayListener
) {
    interface RelayListener {
        fun onConnected()
        fun onDisconnected(code: Int, reason: String)
        fun onBinaryMessage(bytes: ByteArray)
        fun onError(t: Throwable)
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    fun connect() {
        val request = Request.Builder()
            .url(relayUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                listener.onConnected()
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                listener.onBinaryMessage(bytes.toByteArray())
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(code, reason)
                listener.onDisconnected(code, reason)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                listener.onError(t)
            }
        })
    }

    fun sendBinary(bytes: ByteArray) {
        webSocket?.send(ByteString.of(*bytes))
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }
}
