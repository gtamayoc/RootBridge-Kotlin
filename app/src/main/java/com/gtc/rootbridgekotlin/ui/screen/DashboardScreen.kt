package com.gtc.rootbridgekotlin.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gtc.rootbridgekotlin.ui.theme.*
import com.gtc.rootbridgekotlin.ui.viewmodel.UiState

@Composable
fun DashboardScreen(
    state: UiState,
    onStartOverlay: () -> Unit,
    onRefreshProcess: () -> Unit
) {
    Scaffold(
        containerColor = DeepVoid,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onStartOverlay,
                containerColor = AccentPlasma,
                contentColor = DeepVoid,
                text = { Text("START ENGINE", style = Typography.labelMedium) },
                icon = { }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "SYSTEM DASHBOARD",
                style = Typography.titleLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = DeepSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ACTIVE TARGET", color = TextSecondary, style = Typography.labelSmall)
                        TextButton(onClick = onRefreshProcess) {
                            Text("REFRESH", color = AccentSignal, style = Typography.labelSmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (state.isCheckingProcess) {
                        Text("Detecting process...", color = TextPrimary)
                    } else if (state.activeProcess != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).background(AccentPlasma, RoundedCornerShape(6.dp)))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(state.activeProcess.packageName, color = TextPrimary, style = Typography.bodyLarge)
                                Text("PID: ${state.activeProcess.pid}", color = AccentSignal, style = Typography.labelSmall)
                            }
                        }
                    } else {
                        Text("No active foreground process detected", color = TextSecondary)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("ENGINE STATUS", color = TextSecondary, style = Typography.labelSmall)
            Spacer(modifier = Modifier.height(16.dp))
            
            StatusRow("Root Access", "Granted", AccentPlasma)
            Spacer(modifier = Modifier.height(12.dp))
            StatusRow("Overlay Permission", if (state.overlayGranted) "Granted" else "Missing", if (state.overlayGranted) AccentPlasma else AccentWarning)
        }
    }
}

@Composable
fun StatusRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth().background(DeepSurface, RoundedCornerShape(8.dp)).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextPrimary)
        Text(value, color = valueColor, style = Typography.labelSmall)
    }
}
