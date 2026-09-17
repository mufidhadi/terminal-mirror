package com.mufid.terminalmirror.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.LaptopWindows
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mufid.terminalmirror.model.OsType
import com.mufid.terminalmirror.model.TerminalSession
import com.mufid.terminalmirror.ui.TerminalUiHelpers

@Composable
fun WorkstationTabs(
    sessions: List<TerminalSession>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    ScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        containerColor = Color(0xFF202020),
        contentColor = Color.White,
        edgePadding = 16.dp,
        modifier = modifier
    ) {
        sessions.forEachIndexed { index, session ->
            Tab(
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val icon = when (session.osType) {
                            OsType.MACOS -> Icons.Default.Computer
                            OsType.WINDOWS -> Icons.Default.LaptopWindows
                            else -> Icons.Default.Terminal
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTabIndex == index) Color(0xFF64B5F6) else Color(0xFF888888)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = TerminalUiHelpers.shortHostLabel(session.hostName),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (selectedTabIndex == index) Color.White else Color(0xFFAAAAAA)
                        )
                    }
                }
            )
        }
    }
}
