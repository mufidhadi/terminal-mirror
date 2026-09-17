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
import com.mufid.terminalmirror.model.PairingPayloadParser
import com.mufid.terminalmirror.model.TerminalSession
import com.mufid.terminalmirror.network.ConnectionManager
import com.mufid.terminalmirror.network.DecodedPayload
import com.mufid.terminalmirror.network.ProtocolCodec
import com.mufid.terminalmirror.service.TerminalMirrorService
import com.mufid.terminalmirror.terminal.TerminalBufferProcessor
import com.mufid.terminalmirror.terminal.TerminalScreenBuffer
import com.mufid.terminalmirror.ui.KeystrokeEncoder
import com.mufid.terminalmirror.ui.RelayConfig
import com.mufid.terminalmirror.ui.TerminalUiHelpers
import com.mufid.terminalmirror.ui.components.AccessoryBar
import com.mufid.terminalmirror.ui.components.QrScannerDialog
import com.mufid.terminalmirror.ui.components.StatusHeader
import com.mufid.terminalmirror.ui.components.WorkstationTabs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class MainActivity : ComponentActivity() {

    private val pendingPairingUri = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pendingPairingUri.value = intent?.dataString

        // Start Foreground Service to keep streaming alive through Doze Mode
        val serviceIntent = Intent(this, TerminalMirrorService::class.java).apply {
            action = TerminalMirrorService.ACTION_START
        }
        startService(serviceIntent)

        setContent {
            TerminalMirrorApp(
                pendingPairingUri = pendingPairingUri,
                onShowToast = { msg ->
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingPairingUri.value = intent.dataString
    }
}

@Composable
fun TerminalMirrorApp(
    pendingPairingUri: State<String?>,
    onShowToast: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isReadOnly by remember { mutableStateOf(false) } // Default to interactive mode for AVD
    var showScannerDialog by remember { mutableStateOf(false) }
    var commandInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val sequenceCounter = remember { AtomicLong(1000) }

    val isEmulator = remember {
        android.os.Build.FINGERPRINT.contains("generic") ||
        android.os.Build.FINGERPRINT.contains("sdk_gphone") ||
        android.os.Build.HARDWARE.contains("goldfish") ||
        android.os.Build.HARDWARE.contains("ranchu")
    }

    // Terminal 2D Screen Matrix buffers keyed by sessionId
    val screenBuffers = remember { mutableMapOf<String, TerminalScreenBuffer>() }
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
                    val screenBuffer = screenBuffers.getOrPut(sessionId) {
                        TerminalScreenBuffer(cols = 80, rows = 24)
                    }
                    screenBuffer.processChunk(decoded.text)
                    terminalBuffers[sessionId] = screenBuffer.renderScreen()
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

    // Auto-connect to default live session on launch.
    // Real relay host/token must come from a local (uncommitted) config or
    // pairing QR — never hardcoded. Placeholders keep the public repo clean.
    val configuredRelayHost = remember { RelayConfig.PLACEHOLDER_HOST }
    val configuredRelayToken = remember { RelayConfig.PLACEHOLDER_TOKEN }
    LaunchedEffect(Unit) {
        val relayHost = if (isEmulator) "127.0.0.1:8888" else configuredRelayHost
        val defaultRelayUrl = RelayConfig.wsUrl(relayHost, configuredRelayToken, "mac-live-session")
        connectionManager.connectSession("mac-live-session", defaultRelayUrl)
    }

    // Helper to connect a parsed PairingPayload
    val connectPairingPayload: (PairingPayload) -> Unit = { payload ->
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

        val baseRelay = if (isEmulator) {
            payload.relayUrl
                .replace(RelayConfig.PLACEHOLDER_HOST, "127.0.0.1:8888")
                .replace("10.0.2.2:8888", "127.0.0.1:8888")
        } else {
            payload.relayUrl
        }
        val separator = if (baseRelay.contains('?')) "&" else "?"
        val fullUrl = "${baseRelay}${separator}token=${payload.preSharedKey}&session_id=${payload.sessionId}&role=client"
        connectionManager.connectSession(payload.sessionId, fullUrl)
        onShowToast("Paired with ${payload.hostId} (${payload.sessionId})")
    }

    // Handle deep-link / intent delivered pairing URI
    LaunchedEffect(pendingPairingUri.value) {
        val uri = pendingPairingUri.value ?: return@LaunchedEffect
        val payload = PairingPayloadParser.parse(uri)
        if (payload != null) {
            connectPairingPayload(payload)
        }
    }

    // Helper to send keystroke upstream. Bare CTRL/ALT are modifiers and
    // must never be sent as literal text (returns null -> ignored + hint).
    val sendKeystroke: (String) -> Unit = { rawString ->
        if (activeSession != null) {
            val bytes = KeystrokeEncoder.encode(rawString)
            if (bytes == null) {
                onShowToast("Hold CTRL/ALT with another key (modifier only)")
            } else {
                val seq = sequenceCounter.incrementAndGet()
                val packet = codec.value.encodeKeystroke(activeSession.sessionId, seq, bytes)
                connectionManager.sendToSession(activeSession.sessionId, packet)
            }
        }
    }

    if (showScannerDialog) {
        QrScannerDialog(
            onDismiss = { showScannerDialog = false },
            onPayloadScanned = { payload ->
                showScannerDialog = false
                connectPairingPayload(payload)
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
                        screenBuffers[session.sessionId]?.clear()
                        terminalBuffers[session.sessionId] = ""
                        val relayHost = if (isEmulator) "127.0.0.1:8888" else configuredRelayHost
                        val defaultRelayUrl =
                            RelayConfig.wsUrl(relayHost, configuredRelayToken, session.sessionId)
                        connectionManager.connectSession(session.sessionId, defaultRelayUrl)
                        onShowToast("Reconnecting ${session.hostId}...")
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
    return TerminalUiHelpers.bannerLines(
        hostName = session?.hostName,
        sessionId = session?.sessionId,
        relayLabel = RelayConfig.PLACEHOLDER_HOST,
        isConnected = session?.isConnected == true,
        isReadOnly = isReadOnly
    ).joinToString("\n")
}

/**
 * Strips standard ANSI / VT100 control sequences for clean rendering in Compose Text.
 */
private fun stripAnsiCodes(input: String): String {
    return TerminalBufferProcessor.stripAnsiCodes(input)
}
