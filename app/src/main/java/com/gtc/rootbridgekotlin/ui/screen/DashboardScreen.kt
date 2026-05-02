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
import com.gtc.rootbridgekotlin.ui.viewmodel.UiState

@Composable
fun DashboardScreen(
    state: UiState,
    onStartOverlay: () -> Unit,
    onRefreshProcess: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onStartOverlay,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                text = { Text("START ENGINE", style = MaterialTheme.typography.labelMedium) },
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
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ACTIVE TARGET", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = onRefreshProcess) {
                            Text("REFRESH", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (state.isCheckingProcess) {
                        Text("Detecting process...", color = MaterialTheme.colorScheme.onSurface)
                    } else if (state.activeProcess != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(state.activeProcess.packageName, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                                Text("PID: ${state.activeProcess.pid}", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        Text("No active foreground process detected", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("ENGINE STATUS", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(16.dp))
            
            StatusRow("Root Access", "Granted", MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            StatusRow(
                "Overlay Permission", 
                if (state.overlayGranted) "Granted" else "Missing", 
                if (state.overlayGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
fun StatusRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface)
        Text(value, color = valueColor, style = MaterialTheme.typography.labelSmall)
    }
}
