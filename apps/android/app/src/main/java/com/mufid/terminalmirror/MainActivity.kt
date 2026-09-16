package com.mufid.terminalmirror

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mufid.terminalmirror.model.OsType
import com.mufid.terminalmirror.model.TerminalSession
import com.mufid.terminalmirror.service.TerminalMirrorService
import com.mufid.terminalmirror.ui.components.AccessoryBar
import com.mufid.terminalmirror.ui.components.StatusHeader
import com.mufid.terminalmirror.ui.components.WorkstationTabs

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
                onEmergencyKill = { session ->
                    Toast.makeText(
                        this,
                        "EMERGENCY KILL DISPATCHED to ${session.hostName} (Ctrl+Shift+Q)",
                        Toast.LENGTH_LONG
                    ).show()
                },
                onSendKey = { session, key ->
                    Toast.makeText(
                        this,
                        "Key '$key' dispatched to ${session.hostName}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }
}

@Composable
fun TerminalMirrorApp(
    onEmergencyKill: (TerminalSession) -> Unit,
    onSendKey: (TerminalSession, String) -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isReadOnly by remember { mutableStateOf(true) }

    val sessions = remember {
        mutableStateListOf(
            TerminalSession(
                sessionId = "mac-primary",
                hostId = "macbook-pro",
                hostName = "MacBook Pro (zsh)",
                osType = OsType.MACOS,
                shell = "/bin/zsh",
                isConnected = true,
                isReadOnly = true
            ),
            TerminalSession(
                sessionId = "win-primary",
                hostId = "thinkpad-x1",
                hostName = "ThinkPad Win (pwsh)",
                osType = OsType.WINDOWS,
                shell = "powershell.exe",
                isConnected = true,
                isReadOnly = true
            )
        )
    }

    val activeSession = sessions.getOrNull(selectedTabIndex)

    Scaffold(
        topBar = {
            StatusHeader(
                activeSession = activeSession,
                isReadOnly = isReadOnly,
                onToggleReadOnly = { isReadOnly = !isReadOnly }
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

            // Hardware Terminal SurfaceView viewport area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(6.dp)
                    .background(Color(0xFF000000))
            ) {
                if (activeSession != null) {
                    Text(
                        text = buildTerminalPreview(activeSession, isReadOnly),
                        color = Color(0xFF00FF66),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Programmer Keyboard Accessory Bar (Visible only when unlocked)
            if (!isReadOnly && activeSession != null) {
                AccessoryBar(
                    onKeyPress = { key -> onSendKey(activeSession, key) },
                    onEmergencyKill = { onEmergencyKill(activeSession) }
                )
            }
        }
    }
}

private fun buildTerminalPreview(session: TerminalSession, isReadOnly: Boolean): String {
    val lockState = if (isReadOnly) "LOCKED (Read-Only Safety Guard)" else "UNLOCKED (Interactive Keystrokes Active)"
    return """
        ┌────────────────────────────────────────────────────────┐
        │  Terminal Mirror - Mobile Viewer (Termux Engine)       │
        │  Target Host : ${session.hostName.padEnd(41)}│
        │  Session ID  : ${session.sessionId.padEnd(41)}│
        │  Shell Type  : ${session.shell.padEnd(41)}│
        │  Status Mode : ${lockState.padEnd(41)}│
        └────────────────────────────────────────────────────────┘
        
        $ git status
        On branch feature/architecture-spec-and-submodules
        Your branch is up to date with 'origin/feature/architecture-spec-and-submodules'.
        
        $ cargo test --workspace
        test result: ok. 15 passed; 0 failed; 0 ignored; finished in 0.35s
        
        $ _
    """.trimIndent()
}
