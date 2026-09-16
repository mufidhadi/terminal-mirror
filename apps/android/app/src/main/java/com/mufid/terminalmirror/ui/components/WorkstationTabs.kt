package com.mufid.terminalmirror.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.LaptopWindows
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mufid.terminalmirror.model.OsType
import com.mufid.terminalmirror.model.TerminalSession

@Composable
fun WorkstationTabs(
    sessions: List<TerminalSession>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    TabRow(
        selectedTabIndex = selectedTabIndex,
        containerColor = Color(0xFF202020),
        contentColor = Color.White,
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
                            text = session.hostName,
                            fontSize = 12.sp,
                            maxLines = 1,
                            color = if (selectedTabIndex == index) Color.White else Color(0xFFAAAAAA)
                        )
                    }
                }
            )
        }
    }
}
