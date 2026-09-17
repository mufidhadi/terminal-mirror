package com.mufid.terminalmirror

import com.mufid.terminalmirror.network.ConnectionManager
import com.mufid.terminalmirror.network.ConnectionState
import com.mufid.terminalmirror.network.RelayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionManagerTest {

    @Test
    fun `auth error transitions to AuthFailed state and stops reconnect`() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        var lastReportedState: ConnectionState = ConnectionState.Disconnected
        var capturedListener: RelayClient.RelayListener? = null

        val fakeFactory = { url: String, listener: RelayClient.RelayListener ->
            capturedListener = listener
            object : RelayClient(url, listener) {
                override fun connect() {}
                override fun disconnect() {}
            }
        }

        val manager = ConnectionManager(
            scope = scope,
            onSessionPayload = { _, _ -> },
            onSessionStatusChanged = { _, _ -> },
            onConnectionState = { _, state -> lastReportedState = state },
            clientFactory = fakeFactory
        )

        manager.connectSession("mac-live-session", "ws://127.0.0.1:8888/ws")
        assertEquals(ConnectionState.Connecting, lastReportedState)

        // Simulate 401 Auth error
        capturedListener?.onAuthError("Relay authentication failed (HTTP 401 Unauthorized)")

        assertTrue(lastReportedState is ConnectionState.AuthFailed)
        assertEquals("Relay authentication failed (HTTP 401 Unauthorized)", (lastReportedState as ConnectionState.AuthFailed).message)
        assertEquals(lastReportedState, manager.stateOf("mac-live-session"))
    }
}
