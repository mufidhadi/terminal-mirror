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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mufid.terminalmirror.model.TerminalSession
import com.mufid.terminalmirror.network.ConnectionState
import com.mufid.terminalmirror.ui.TerminalUiHelpers
import com.mufid.terminalmirror.ui.theme.TerminalColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusHeader(
    activeSession: TerminalSession?,
    connectionState: ConnectionState = ConnectionState.Disconnected,
    isReadOnly: Boolean,
    onToggleReadOnly: () -> Unit,
    onOpenScanner: () -> Unit,
    onReconnect: () -> Unit = {},
    isHostOnline: Boolean = true,
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
                    color = TerminalColors.PrimaryText,
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
                        color = TerminalColors.SubtitleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            // Connection State Chip: every lifecycle state is visible, with a
            // live retry countdown while reconnecting.
            var tickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
            val reconnecting = connectionState as? ConnectionState.Reconnecting
            val deadlineMs = remember(reconnecting) {
                System.currentTimeMillis() + (reconnecting?.delayMs ?: 0L)
            }
            LaunchedEffect(reconnecting) {
                if (reconnecting != null) {
                    while (true) {
                        delay(500)
                        tickMs = System.currentTimeMillis()
                    }
                }
            }
            val (chipColor, chipText) = when (connectionState) {
                is ConnectionState.Connected -> {
                    if (isHostOnline) {
                        TerminalColors.Live to "● LIVE"
                    } else {
                        TerminalColors.Warning to "○ HOST OFF"
                    }
                }
                is ConnectionState.Connecting -> TerminalColors.Warning to "… CONN"
                is ConnectionState.Reconnecting -> {
                    val remainS = maxOf(0L, (deadlineMs - tickMs + 999) / 1000)
                    TerminalColors.Warning to "… R${connectionState.attempt} ${remainS}s"
                }
                is ConnectionState.Disconnected -> TerminalColors.Offline to "○ OFF"
                is ConnectionState.AuthFailed -> TerminalColors.Error to "✕ 401 AUTH"
            }
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .background(color = chipColor, shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = chipText,
                    fontSize = 10.sp,
                    color = TerminalColors.PrimaryText,
                    maxLines = 1
                )
            }

            // QR Code Scanner Action Button
            IconButton(onClick = onOpenScanner) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan Terminal QR Code",
                    tint = TerminalColors.Accent
                )
            }

            // Manual Reconnect / Refresh Action Button
            IconButton(onClick = onReconnect) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reconnect Terminal Session",
                    tint = TerminalColors.AccentLight
                )
            }

            // Safety Mode Lock/Unlock Toggle Button
            IconButton(onClick = onToggleReadOnly) {
                Icon(
                    imageVector = if (isReadOnly) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (isReadOnly) "Read-Only Active" else "Input Enabled",
                    tint = if (isReadOnly) TerminalColors.LiveBright else TerminalColors.Unlocked
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = TerminalColors.TopBarBackground,
            titleContentColor = TerminalColors.PrimaryText
        ),
        modifier = modifier
    )
}
