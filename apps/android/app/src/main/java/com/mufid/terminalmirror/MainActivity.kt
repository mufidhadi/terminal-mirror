package com.mufid.terminalmirror

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mufid.terminalmirror.model.OsType
import com.mufid.terminalmirror.model.TerminalSession

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TerminalMirrorApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalMirrorApp() {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isReadOnly by remember { mutableStateOf(true) }

    val sessions = remember {
        listOf(
            TerminalSession(
                sessionId = "mac-primary",
                hostId = "macbook-pro",
                hostName = "MacBook Pro (zsh)",
                osType = OsType.MACOS,
                shell = "/bin/zsh",
                isConnected = true,
                isReadOnly = isReadOnly
            ),
            TerminalSession(
                sessionId = "win-primary",
                hostId = "windows-laptop",
                hostName = "ThinkPad Windows (pwsh)",
                osType = OsType.WINDOWS,
                shell = "powershell.exe",
                isConnected = true,
                isReadOnly = isReadOnly
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminal Mirror", fontSize = 18.sp) },
                actions = {
                    IconButton(onClick = { isReadOnly = !isReadOnly }) {
                        Icon(
                            imageVector = if (isReadOnly) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Toggle Read Only",
                            tint = if (isReadOnly) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF121212))
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color(0xFF1E1E1E),
                contentColor = Color.White
            ) {
                sessions.forEachIndexed { index, session ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (session.osType == OsType.MACOS) Icons.Default.Computer else Icons.Default.LaptopWindows,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(session.hostName, fontSize = 13.sp)
                            }
                        }
                    )
                }
            }

            // Terminal canvas placeholder (integrated with termux-view)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(Color(0xFF000000))
            ) {
                Text(
                    text = "[Terminal Canvas: ${sessions[selectedTabIndex].hostName}]\n" +
                            "Session ID: ${sessions[selectedTabIndex].sessionId}\n" +
                            "Status: Connected (Real-time Stream)\n" +
                            "Safety Mode: ${if (isReadOnly) "LOCKED (Read-Only)" else "UNLOCKED (Input Active)"}\n" +
                            "--------------------------------------------------\n" +
                            "$ neofetch\n" +
                            "OS: ${sessions[selectedTabIndex].osType}\n" +
                            "Uptime: 4 days, 12 hours\n" +
                            "Relay: Connected via Secure Tunnel\n",
                    color = Color(0xFF00FF66),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }

            // Virtual Keyboard Accessory Bar
            if (!isReadOnly) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2D2D2D))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val keys = listOf("ESC", "TAB", "CTRL", "ALT", "↑", "↓", "←", "→")
                    keys.forEach { key ->
                        Button(
                            onClick = { /* Send key sequence */ },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3E3E3E))
                        ) {
                            Text(key, fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
