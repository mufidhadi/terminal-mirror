package com.mufid.terminalmirror

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mufid.terminalmirror.ui.theme.TerminalColors
import com.mufid.terminalmirror.ui.theme.TerminalMirrorTheme
import com.mufid.terminalmirror.crypto.E2eeManager
import com.mufid.terminalmirror.model.OsType
import com.mufid.terminalmirror.model.PairingPayload
import com.mufid.terminalmirror.model.PairingPayloadParser
import com.mufid.terminalmirror.model.TerminalSession
import com.mufid.terminalmirror.network.ConnectionManager
import com.mufid.terminalmirror.network.ConnectionState
import com.mufid.terminalmirror.network.DecodedPayload
import com.mufid.terminalmirror.network.ProtocolCodec
import com.mufid.terminalmirror.service.TerminalMirrorService
import com.mufid.terminalmirror.terminal.TerminalBufferProcessor
import com.mufid.terminalmirror.terminal.TerminalScreenBuffer
import com.mufid.terminalmirror.ui.KeystrokeEncoder
import com.mufid.terminalmirror.ui.LineTone
import com.mufid.terminalmirror.ui.RelayConfig
import com.mufid.terminalmirror.ui.TerminalLineClassifier
import com.mufid.terminalmirror.ui.components.AccessoryBar
import com.mufid.terminalmirror.ui.components.ConnectionStatusCard
import com.mufid.terminalmirror.ui.components.QrScannerDialog
import com.mufid.terminalmirror.ui.components.StatusHeader
import com.mufid.terminalmirror.ui.components.WorkstationTabs
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val pendingPairingUri = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        pendingPairingUri.value = intent?.dataString

        // Start Foreground Service to keep streaming alive through Doze Mode
        val serviceIntent = Intent(this, TerminalMirrorService::class.java).apply {
            action = TerminalMirrorService.ACTION_START
        }
        startService(serviceIntent)

        setContent {
            TerminalMirrorTheme {
                TerminalMirrorApp(
                    pendingPairingUri = pendingPairingUri,
                    onShowToast = { msg ->
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                )
            }
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

    // E2EE manager and protocol codec. The default passphrase is a placeholder:
    // real pairing always replaces it via QR/deep-link before streaming.
    val e2eeCipher = remember {
        mutableStateOf(E2eeManager.fromSecret(RelayConfig.PLACEHOLDER_PASSPHRASE))
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

    // Observable connection lifecycle per session (WS7). The legacy boolean
    // callback is kept to drive the session list dot; states drive the UI.
    val connectionStates = remember { mutableStateMapOf<String, ConnectionState>() }
    val queueInfo = remember { mutableStateMapOf<String, Pair<Int, Int>>() }

    // ConnectionManager instance
    var managerRef: ConnectionManager? by remember { mutableStateOf(null) }
    val connectionManager = remember {
        ConnectionManager(
            scope = coroutineScope,
            onSessionPayload = { sessionId, bytes ->
                when (val decoded = codec.value.decodePacket(bytes)) {
                    is DecodedPayload.TerminalOutput -> {
                        val screenBuffer = screenBuffers.getOrPut(sessionId) {
                            TerminalScreenBuffer(cols = 80, rows = 24)
                        }
                        screenBuffer.processChunk(decoded.text)
                        terminalBuffers[sessionId] = screenBuffer.renderScreen()
                    }
                    is DecodedPayload.ScreenSync -> {
                        val screenBuffer = screenBuffers.getOrPut(sessionId) {
                            TerminalScreenBuffer(cols = 80, rows = 24)
                        }
                        screenBuffer.replaceWithSnapshot(decoded.lines, decoded.cursorX, decoded.cursorY)
                        terminalBuffers[sessionId] = screenBuffer.renderScreen()
                    }
                    is DecodedPayload.SessionRevoked -> {
                        managerRef?.disconnectSession(sessionId)
                        onShowToast("Session revoked: ${decoded.reason}")
                    }
                    is DecodedPayload.ProtocolError -> {
                        onShowToast("Relay error ${decoded.code}: ${decoded.message}")
                    }
                    else -> Unit
                }
            },
            onSessionStatusChanged = { sessionId, isConnected ->
                val idx = sessions.indexOfFirst { it.sessionId == sessionId }
                if (idx >= 0) {
                    sessions[idx] = sessions[idx].copy(isConnected = isConnected)
                }
            },
            onConnectionState = { sessionId, state ->
                connectionStates[sessionId] = state
            },
            onQueueChanged = { sessionId, queued, dropped ->
                queueInfo[sessionId] = queued to dropped
            }
        ).also { managerRef = it }
    }

    val activeState: ConnectionState =
        activeSession?.let { connectionStates[it.sessionId] }
            ?: ConnectionState.Disconnected
    val isLive = activeState is ConnectionState.Connected

    // Auto-connect to default live session on launch.
    // Real relay host/token come from BuildConfig (git-ignored local.properties
    // or CI env) — never hardcoded. Blanks resolve to safe placeholders.
    val configuredRelayHost = remember { RelayConfig.resolveHost(BuildConfig.RELAY_HOST) }
    val configuredRelayToken = remember { RelayConfig.resolveToken(BuildConfig.RELAY_TOKEN) }
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
                connectionState = activeState,
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
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .background(TerminalColors.ScreenBackground)
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
                    .background(TerminalColors.ViewportBackground)
                    .verticalScroll(scrollState)
            ) {
                val currentText = activeSession?.let { terminalBuffers[it.sessionId] } ?: ""
                if (currentText.isBlank()) {
                    // Structured empty-state: fluid rows, no fixed-width ASCII
                    // box, nothing to wrap or overflow on narrow viewports.
                    ConnectionStatusCard(
                        hostName = activeSession?.hostName,
                        sessionId = activeSession?.sessionId,
                        relayLabel = RelayConfig.PLACEHOLDER_HOST,
                        isConnected = isLive,
                        isReadOnly = isReadOnly
                    )
                } else {
                    // Semantic coloring: errors red, healthy lines green,
                    // prompts muted — classified per line, never one color.
                    val annotated = buildAnnotatedString {
                        TerminalLineClassifier.spanLines(currentText).forEachIndexed { i, span ->
                            if (i > 0) append("\n")
                            val color = when (span.tone) {
                                LineTone.ERROR -> TerminalColors.Error
                                LineTone.SUCCESS -> TerminalColors.Success
                                LineTone.MUTED -> TerminalColors.MutedText
                                LineTone.NORMAL -> TerminalColors.TerminalText
                            }
                            withStyle(SpanStyle(color = color)) { append(span.line) }
                        }
                    }
                    Text(
                        text = annotated,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Quick Command Input Field: live only. While offline the input
            // is replaced by an honest status line — keystrokes can never be
            // typed into a dead socket and silently lost (temuan 9).
            if (!isReadOnly && activeSession != null && isLive) {
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
                            focusedContainerColor = TerminalColors.InputBackground,
                            unfocusedContainerColor = TerminalColors.InputBackground,
                            focusedTextColor = TerminalColors.PrimaryText,
                            unfocusedTextColor = TerminalColors.PrimaryText
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
                        colors = IconButtonDefaults.iconButtonColors(containerColor = TerminalColors.Send)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = TerminalColors.PrimaryText)
                    }
                }

                // Programmer Keyboard Accessory Bar
                AccessoryBar(
                    onKeyPress = { key -> sendKeystroke(key) },
                    onEmergencyKill = {
                        sendKeystroke("CTRL+C")
                        onShowToast("Emergency Kill dispatched (Ctrl+C)")
                    },
                    onDisconnect = {
                        connectionManager.disconnectSession(activeSession.sessionId)
                        onShowToast("Session disconnected — tap refresh to reconnect")
                    }
                )
            }

            if (!isReadOnly && activeSession != null && !isLive) {
                val session = activeSession
                val (queued, dropped) = queueInfo[session.sessionId] ?: (0 to 0)
                val stateText = when (val s = activeState) {
                    is ConnectionState.Reconnecting -> "Reconnecting (attempt ${s.attempt})"
                    is ConnectionState.Connecting -> "Connecting"
                    else -> "Disconnected"
                }
                val queueText = if (queued > 0 || dropped > 0) {
                    " · $queued queued" + (if (dropped > 0) " ($dropped dropped)" else "")
                } else ""
                Text(
                    text = "$stateText — input paused$queueText. Tap refresh to reconnect.",
                    color = TerminalColors.MutedText,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/**
 * Strips standard ANSI / VT100 control sequences for clean rendering in Compose Text.
 */
private fun stripAnsiCodes(input: String): String {
    return TerminalBufferProcessor.stripAnsiCodes(input)
}
