package com.gtc.rootbridgekotlin.overlay.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gtc.rootbridgekotlin.ui.theme.*

@Composable
fun OverlayContent(onClose: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    if (expanded) {
        Card(
            modifier = Modifier.width(320.dp).padding(8.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = DeepElevated)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Memory Engine", style = Typography.titleLarge, color = TextPrimary)
                Spacer(modifier = Modifier.height(16.dp))
                var query by remember { mutableStateOf("") }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search value...", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DeepSurface,
                        unfocusedContainerColor = DeepSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentPlasma,
                        focusedIndicatorColor = AccentPlasma
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { expanded = false }) {
                        Text("Minimize", color = TextSecondary)
                    }
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentError, contentColor = TextPrimary)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    } else {
        FloatingActionButton(
            onClick = { expanded = true },
            modifier = Modifier.size(56.dp).padding(4.dp),
            containerColor = DeepElevated,
            contentColor = AccentPlasma
        ) {
            Text("RB", style = Typography.labelMedium, color = AccentPlasma)
        }
    }
}
