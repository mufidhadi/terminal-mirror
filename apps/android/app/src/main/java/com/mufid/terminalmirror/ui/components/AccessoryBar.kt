package com.mufid.terminalmirror.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AccessoryBar(
    onKeyPress: (String) -> Unit,
    onEmergencyKill: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF242424))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val standardKeys = listOf("ESC", "TAB", "CTRL", "ALT", "|", "~", "↑", "↓", "←", "→")

        standardKeys.forEach { key ->
            Button(
                onClick = { onKeyPress(key) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF383838),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
            ) {
                Text(
                    text = key,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }

        // Emergency Kill Switch Button
        Button(
            onClick = onEmergencyKill,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFB71C1C),
                contentColor = Color.White
            ),
            modifier = Modifier
                .weight(1.3f)
                .height(34.dp)
        ) {
            Text(
                text = "KILL (Q)",
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}
