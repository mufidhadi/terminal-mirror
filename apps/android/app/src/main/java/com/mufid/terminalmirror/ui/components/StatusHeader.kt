package com.mufid.terminalmirror.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mufid.terminalmirror.model.TerminalSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusHeader(
    activeSession: TerminalSession?,
    isReadOnly: Boolean,
    onToggleReadOnly: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Terminal Mirror",
                    fontSize = 17.sp,
                    color = Color.White
                )
                if (activeSession != null) {
                    Text(
                        text = "${activeSession.hostName} • ${activeSession.shell}",
                        fontSize = 11.sp,
                        color = Color(0xFFAAAAAA)
                    )
                }
            }
        },
        actions = {
            // Live Status Indicator Chip
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
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
