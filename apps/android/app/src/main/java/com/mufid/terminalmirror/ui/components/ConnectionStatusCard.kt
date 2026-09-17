package com.mufid.terminalmirror.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mufid.terminalmirror.ui.theme.TerminalColors
import com.mufid.terminalmirror.ui.RelayConfig
import com.mufid.terminalmirror.ui.TerminalUiHelpers

/**
 * Structured empty-state for the terminal viewport.
 *
 * Replaces the old fixed-width ASCII box (which wrapped and broke on ~360dp
 * phone viewports). Every row is a fluid label+value pair with ellipsis, so
 * nothing can overflow regardless of host-name length or font scale.
 */
@Composable
fun ConnectionStatusCard(
    hostName: String?,
    sessionId: String?,
    relayLabel: String,
    isConnected: Boolean,
    isReadOnly: Boolean,
    isHostOnline: Boolean = true,
    modifier: Modifier = Modifier
) {
    val statusColor = when {
        !isConnected -> TerminalColors.Warning
        !isHostOnline -> TerminalColors.Warning
        else -> TerminalColors.LiveBright
    }
    val relay = relayLabel.trim().ifEmpty { RelayConfig.PLACEHOLDER_HOST }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TerminalColors.CardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Terminal Mirror — Android Client",
                    color = TerminalColors.PrimaryText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            StatusRow(label = "Host", value = hostName?.trim().orEmpty().ifEmpty { "Unknown host" })
            StatusRow(label = "Session", value = sessionId?.trim().orEmpty().ifEmpty { "-" })
            StatusRow(label = "Relay", value = relay)
            StatusRow(
                label = "Status",
                value = TerminalUiHelpers.statusLabel(isConnected, isHostOnline),
                valueColor = statusColor
            )
            StatusRow(label = "Mode", value = TerminalUiHelpers.modeLabel(isReadOnly))

            Text(
                text = when {
                    !isConnected -> "Waiting for PTY frames…"
                    !isHostOnline -> "Relay connected. Waiting for host agent to start…"
                    else -> "Streaming live PTY frames…"
                },
                color = TerminalColors.MutedText,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    valueColor: Color = TerminalColors.TerminalText
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = TerminalColors.MutedText,
            fontSize = 11.sp,
            modifier = Modifier.width(64.dp),
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
        Text(
            text = value,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
