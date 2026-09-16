package com.mufid.terminalmirror

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mufid.terminalmirror.crypto.E2eeManager
import com.mufid.terminalmirror.model.OsType
import com.mufid.terminalmirror.model.PairingPayload
import com.mufid.terminalmirror.model.TerminalSession
import com.mufid.terminalmirror.network.ConnectionManager
import com.mufid.terminalmirror.network.DecodedPayload
import com.mufid.terminalmirror.network.ProtocolCodec
import com.mufid.terminalmirror.service.TerminalMirrorService
import com.mufid.terminalmirror.terminal.TerminalBufferProcessor
import com.mufid.terminalmirror.ui.components.AccessoryBar
import com.mufid.terminalmirror.ui.components.QrScannerDialog
import com.mufid.terminalmirror.ui.components.StatusHeader
import com.mufid.terminalmirror.ui.components.WorkstationTabs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start Foreground Service to keep streaming alive through Doze Mode
        val serviceIntent = Intent(this, TerminalMirrorService::class.java).apply {
            action = TerminalMirrorService.ACTION_START
        }
        startService(serviceIntent)

        setContent {
            TerminalMirrorApp(
                onShowToast = { msg ->
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun TerminalMirrorApp(
    onShowToast: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isReadOnly by remember { mutableStateOf(false) } // Default to interactive mode for AVD
    var showScannerDialog by remember { mutableStateOf(false) }
    var commandInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val sequenceCounter = remember { AtomicLong(1000) }

    // Live terminal buffers keyed by sessionId
    val terminalBuffers = remember { mutableStateMapOf<String, String>() }

    // E2EE manager and protocol codec (defaults to standard passphrase)
    val e2eeCipher = remember {
        mutableStateOf(E2eeManager.fromSecret("batu-merah-kuda-terbang"))
    }
    val codec = remember {
        derivedStateOf { ProtocolCodec(e2eeCipher.value) }
    }

    val sessions = remember {
        mutableStateListOf(
            TerminalSession(
                sessionId = "mac-live-session",
                hostId = "macbook-pro",
                hostName = "MacBook Pro (Darwin zsh)",
                osType = OsType.MACOS,
                shell = "/bin/zsh",
                isConnected = false,
                isReadOnly = false
            ),
            TerminalSession(
                sessionId = "win-live-session",
                hostId = "thinkpad-x1",
                hostName = "ThinkPad Win (pwsh)",
                osType = OsType.WINDOWS,
                shell = "powershell.exe",
                isConnected = false,
                isReadOnly = true
            )
        )
    }

    val activeSession = sessions.getOrNull(selectedTabIndex)

    // ConnectionManager instance
    val connectionManager = remember {
        ConnectionManager(
            scope = coroutineScope,
            onSessionPayload = { sessionId, bytes ->
                val decoded = codec.value.decodePacket(bytes)
                if (decoded is DecodedPayload.TerminalOutput) {
                    val current = terminalBuffers.getOrDefault(sessionId, "")
                    val updated = TerminalBufferProcessor.processChunk(current, decoded.text)
                    terminalBuffers[sessionId] = updated
                }
            },
            onSessionStatusChanged = { sessionId, isConnected ->
                val idx = sessions.indexOfFirst { it.sessionId == sessionId }
                if (idx >= 0) {
                    sessions[idx] = sessions[idx].copy(isConnected = isConnected)
                }
            }
        )
    }

    // Auto-connect to default live session on launch
    LaunchedEffect(Unit) {
        val defaultRelayUrl = "ws://172.23.127.184:8888/ws?token=masmufid_super_secret_relay_2026&session_id=mac-live-session&role=client"
        connectionManager.connectSession("mac-live-session", defaultRelayUrl)
    }

    // Helper to send keystroke upstream
    val sendKeystroke: (String) -> Unit = { rawString ->
        if (activeSession != null) {
            val bytes = when (rawString) {
                "ENTER", "\r", "\n" -> "\r".toByteArray(Charsets.UTF_8)
                "TAB", "\t" -> "\t".toByteArray(Charsets.UTF_8)
                "ESC" -> "\u001b".toByteArray(Charsets.UTF_8)
                "CTRL+C" -> byteArrayOf(0x03)
                "CTRL+D" -> byteArrayOf(0x04)
                "CTRL+Z" -> byteArrayOf(0x1A)
                "UP" -> "\u001b[A".toByteArray(Charsets.UTF_8)
                "DOWN" -> "\u001b[B".toByteArray(Charsets.UTF_8)
                "LEFT" -> "\u001b[D".toByteArray(Charsets.UTF_8)
                "RIGHT" -> "\u001b[C".toByteArray(Charsets.UTF_8)
                else -> rawString.toByteArray(Charsets.UTF_8)
            }
            val seq = sequenceCounter.incrementAndGet()
            val packet = codec.value.encodeKeystroke(activeSession.sessionId, seq, bytes)
            connectionManager.sendToSession(activeSession.sessionId, packet)
        }
    }

    if (showScannerDialog) {
        QrScannerDialog(
            onDismiss = { showScannerDialog = false },
            onPayloadScanned = { payload ->
                showScannerDialog = false
                val secret = payload.getFormattedPassphrase() ?: payload.preSharedKey
                if (secret.isNotBlank()) {
                    e2eeCipher.value = E2eeManager.fromSecret(secret)
                }

                val newSession = TerminalSession(
                    sessionId = payload.sessionId,
                    hostId = payload.hostId,
                    hostName = "${payload.hostId} (paired)",
                    osType = if (payload.hostId.contains("win", ignoreCase = true)) OsType.WINDOWS else OsType.MACOS,
                    shell = "remote-pty",
                    isConnected = false,
                    isReadOnly = false
                )
                val existingIndex = sessions.indexOfFirst { it.sessionId == payload.sessionId }
                if (existingIndex >= 0) {
                    sessions[existingIndex] = newSession
                    selectedTabIndex = existingIndex
                } else {
                    sessions.add(newSession)
                    selectedTabIndex = sessions.size - 1
                }

                val separator = if (payload.relayUrl.contains('?')) "&" else "?"
                val fullUrl = "${payload.relayUrl}${separator}token=${payload.preSharedKey}&session_id=${payload.sessionId}&role=client"
                connectionManager.connectSession(payload.sessionId, fullUrl)
                onShowToast("Paired with ${payload.hostId} (${payload.sessionId})")
            }
        )
    }

    Scaffold(
        topBar = {
            StatusHeader(
                activeSession = activeSession,
                isReadOnly = isReadOnly,
                onToggleReadOnly = { isReadOnly = !isReadOnly },
                onOpenScanner = { showScannerDialog = true },
                onReconnect = {
                    activeSession?.let { session ->
                        val defaultRelayUrl = "ws://172.23.127.184:8888/ws?token=masmufid_super_secret_relay_2026&session_id=${session.sessionId}&role=client"
                        connectionManager.connectSession(session.sessionId, defaultRelayUrl)
                        onShowToast("Menyambung ulang ${session.hostId}...")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF121212))
        ) {
            // Workstation Tabs (Mac / Windows / Linux)
            WorkstationTabs(
                sessions = sessions,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it }
            )

            // Terminal Viewport Area
            val scrollState = rememberScrollState()
            LaunchedEffect(terminalBuffers[activeSession?.sessionId]) {
                scrollState.scrollTo(scrollState.maxValue)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(6.dp)
                    .background(Color(0xFF0D1117))
                    .verticalScroll(scrollState)
            ) {
                val currentText = activeSession?.let { terminalBuffers[it.sessionId] } ?: ""
                val displayText = if (currentText.isBlank()) {
                    buildInitialBanner(activeSession, isReadOnly)
                } else {
                    currentText
                }

                Text(
                    text = displayText,
                    color = Color(0xFF58A6FF),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }

            // Quick Command Input Field
            if (!isReadOnly && activeSession != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        placeholder = { Text("Type command (e.g. ls -la, uname -a)", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1F242C),
                            unfocusedContainerColor = Color(0xFF1F242C),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (commandInput.isNotBlank()) {
                                sendKeystroke("$commandInput\r")
                                commandInput = ""
                                keyboardController?.hide()
                            }
                        })
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (commandInput.isNotBlank()) {
                                sendKeystroke("$commandInput\r")
                                commandInput = ""
                                keyboardController?.hide()
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF238636))
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                    }
                }

                // Programmer Keyboard Accessory Bar
                AccessoryBar(
                    onKeyPress = { key -> sendKeystroke(key) },
                    onEmergencyKill = {
                        sendKeystroke("CTRL+C")
                        onShowToast("Emergency Kill dispatched (Ctrl+C)")
                    }
                )
            }
        }
    }
}

private fun buildInitialBanner(session: TerminalSession?, isReadOnly: Boolean): String {
    val statusText = if (session?.isConnected == true) "CONNECTED (Realtime Stream Active)" else "CONNECTING to VPS Relay (172.23.127.184:8888)..."
    val lockText = if (isReadOnly) "LOCKED (Read-Only Mode)" else "UNLOCKED (Interactive Remote Keystrokes Active)"
    return """
        ┌────────────────────────────────────────────────────────┐
        │  Terminal Mirror - Android Client (API 35)             │
        │  Target Host  : ${session?.hostName?.padEnd(39)}│
        │  Session ID   : ${session?.sessionId?.padEnd(39)}│
        │  Relay VPS    : 172.23.127.184:8888                    │
        │  Status       : ${statusText.padEnd(39)}│
        │  Mode         : ${lockText.padEnd(39)}│
        └────────────────────────────────────────────────────────┘
        
        Waiting for Darwin PTY shell frames...
    """.trimIndent()
}

/**
 * Strips standard ANSI / VT100 control sequences for clean rendering in Compose Text.
 */
private fun stripAnsiCodes(input: String): String {
    return TerminalBufferProcessor.stripAnsiCodes(input)
}
