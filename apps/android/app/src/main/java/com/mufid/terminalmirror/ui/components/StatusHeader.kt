package com.mufid.terminalmirror.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mufid.terminalmirror.model.TerminalSession
import com.mufid.terminalmirror.ui.TerminalUiHelpers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusHeader(
    activeSession: TerminalSession?,
    isReadOnly: Boolean,
    onToggleReadOnly: () -> Unit,
    onOpenScanner: () -> Unit,
    onReconnect: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            // No width constraint: TopAppBar gives the title slot all space
            // left by actions; truncating it manually re-creates the bug.
            Column {
                Text(
                    text = "Terminal Mirror",
                    fontSize = 17.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (activeSession != null) {
                    Text(
                        text = TerminalUiHelpers.sessionSubtitle(
                            activeSession.hostName,
                            activeSession.shell
                        ),
                        fontSize = 11.sp,
                        color = Color(0xFFAAAAAA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            // Live Status Indicator Chip
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .background(
                        color = if (activeSession?.isConnected == true) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (activeSession?.isConnected == true) "● LIVE" else "○ OFFLINE",
                    fontSize = 10.sp,
                    color = Color.White
                )
            }

            // QR Code Scanner Action Button
            IconButton(onClick = onOpenScanner) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan Terminal QR Code",
                    tint = Color(0xFF58A6FF)
                )
            }

            // Manual Reconnect / Refresh Action Button
            IconButton(onClick = onReconnect) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reconnect Terminal Session",
                    tint = Color(0xFF64B5F6)
                )
            }

            // Safety Mode Lock/Unlock Toggle Button
            IconButton(onClick = onToggleReadOnly) {
                Icon(
                    imageVector = if (isReadOnly) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (isReadOnly) "Read-Only Active" else "Input Enabled",
                    tint = if (isReadOnly) Color(0xFF4CAF50) else Color(0xFFFF5722)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF181818),
            titleContentColor = Color.White
        ),
        modifier = modifier
    )
}
