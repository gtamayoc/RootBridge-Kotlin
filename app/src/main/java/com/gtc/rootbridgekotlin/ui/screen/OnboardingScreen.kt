package com.gtc.rootbridgekotlin.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gtc.rootbridgekotlin.core.root.RootAccessState
import com.gtc.rootbridgekotlin.ui.viewmodel.UiState

@Composable
fun OnboardingScreen(
    state: UiState,
    onCheckRoot: () -> Unit,
    onRequestOverlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SYSTEM INITIALIZATION",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        StatusNode(
            title = "Superuser Access",
            description = "Required to read/write memory across processes.",
            state = when (state.rootState) {
                is RootAccessState.Idle -> NodeState.WAITING
                is RootAccessState.Checking -> NodeState.PROCESSING
                is RootAccessState.Authorized -> NodeState.SUCCESS
                is RootAccessState.Denied, is RootAccessState.MissingBinary -> NodeState.FAILED
            },
            onAction = onCheckRoot,
            actionText = "Verify"
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        StatusNode(
            title = "Display Overlays",
            description = "Required to show the floating engine panel.",
            state = if (state.overlayGranted) NodeState.SUCCESS else {
                if (state.rootState is RootAccessState.Authorized) NodeState.WAITING else NodeState.WAITING
            },
            onAction = onRequestOverlay,
            actionText = "Grant"
        )
        
        if (state.rootState is RootAccessState.Denied) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = (state.rootState as RootAccessState.Denied).reason,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

enum class NodeState { WAITING, PROCESSING, SUCCESS, FAILED }

@Composable
fun StatusNode(
    title: String,
    description: String,
    state: NodeState,
    onAction: () -> Unit,
    actionText: String
) {
    val color = when (state) {
        NodeState.WAITING -> MaterialTheme.colorScheme.onSurfaceVariant
        NodeState.PROCESSING -> MaterialTheme.colorScheme.secondary
        NodeState.SUCCESS -> MaterialTheme.colorScheme.primary
        NodeState.FAILED -> MaterialTheme.colorScheme.error
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (state == NodeState.SUCCESS) color else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (state != NodeState.SUCCESS) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawCircle(color = color, style = Stroke(width = 2.dp.toPx()))
                }
            } else {
                Text("✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = color, style = MaterialTheme.typography.bodyLarge)
            Text(text = description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        
        if (state == NodeState.FAILED || state == NodeState.WAITING) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = color
                ),
                shape = CircleShape
            ) {
                Text(actionText, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
