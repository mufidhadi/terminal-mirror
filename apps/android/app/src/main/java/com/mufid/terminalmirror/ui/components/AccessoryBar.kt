package com.mufid.terminalmirror.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AccessoryBar(
    onKeyPress: (String) -> Unit,
    onEmergencyKill: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Horizontally scrollable: every key keeps its full label and a 48dp
    // touch target instead of being squeezed into unreadable "ES/TA/CT".
    val standardKeys = listOf("ESC", "TAB", "CTRL", "ALT", "|", "~", "↑", "↓", "←", "→")

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF242424))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(standardKeys, key = { it }) { key ->
            Button(
                onClick = { onKeyPress(key) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF383838),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .widthIn(min = 56.dp)
                    .height(48.dp)
            ) {
                Text(
                    text = key,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Visible
                )
            }
        }

        item(key = "KILL") {
            // Emergency Kill Switch Button: SIGINT to the foreground process.
            Button(
                onClick = onEmergencyKill,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB71C1C),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .widthIn(min = 72.dp)
                    .height(48.dp)
            ) {
                Text(
                    text = "KILL",
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Visible
                )
            }
        }

        item(key = "DISC") {
            // Session Disconnect Button: closes the WebSocket stream without
            // touching the remote process. Deliberately NOT red: disconnect
            // is reversible (tap refresh to reconnect), KILL is not.
            Button(
                onClick = onDisconnect,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3A3325),
                    contentColor = Color(0xFFFFB74D)
                ),
                modifier = Modifier
                    .widthIn(min = 72.dp)
                    .height(48.dp)
            ) {
                Text(
                    text = "DISC",
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Visible
                )
            }
        }
    }
}
