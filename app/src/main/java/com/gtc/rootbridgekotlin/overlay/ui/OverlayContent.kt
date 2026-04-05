package com.gtc.rootbridgekotlin.overlay.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gtc.rootbridgekotlin.core.memory.ScanResult
import com.gtc.rootbridgekotlin.ui.theme.*
import com.gtc.rootbridgekotlin.ui.viewmodel.ScanState
import com.gtc.rootbridgekotlin.ui.viewmodel.WriteState
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sqrt

@Composable
fun OverlayContent(
    isExpandedFlow: StateFlow<Boolean>,
    alphaFlow: StateFlow<Float>,
    interactableFlow: StateFlow<Boolean>,
    pidFlow: StateFlow<Int>,
    packageFlow: StateFlow<String>,
    scanStateFlow: StateFlow<ScanState>,
    writeStateFlow: StateFlow<WriteState>,
    onExpandedChange: (Boolean) -> Unit,
    onMove: (Float, Float) -> Unit,
    onClose: () -> Unit,
    onScan: (Int, String) -> Unit,
    onRefine: (Int, Int) -> Unit,
    onWrite: (Int, Long, Int) -> Unit,
    onReset: () -> Unit,
    onGetRunningApps: suspend () -> List<com.gtc.rootbridgekotlin.core.memory.ProcessInfo>,
    onOverrideTarget: (Int, String) -> Unit
) {
    val isExpanded by isExpandedFlow.collectAsState()
    val alpha by alphaFlow.collectAsState()
    val isInteractable by interactableFlow.collectAsState()
    val currentPid by pidFlow.collectAsState()
    val currentPackage by packageFlow.collectAsState()

    val scanState by scanStateFlow.collectAsState()
    val writeState by writeStateFlow.collectAsState()

    Box(
        modifier = Modifier
            .alpha(alpha)
            .padding(8.dp)
    ) {
        if (isExpanded) {
            ExpandedMenu(
                currentPid = currentPid,
                currentPackage = currentPackage,
                scanState = scanState,
                writeState = writeState,
                onScan = onScan,
                onRefine = onRefine,
                onWrite = onWrite,
                onReset = onReset,
                onGetRunningApps = onGetRunningApps,
                onOverrideTarget = onOverrideTarget,
                onClose = { onExpandedChange(false) },
                onStopService = onClose
            )
        } else {
            FloatingIcon(
                isInteractable = isInteractable,
                onClick = { onExpandedChange(true) },
                onMove = onMove
            )
        }
    }
}

@Composable
fun FloatingIcon(
    isInteractable: Boolean,
    onClick: () -> Unit,
    onMove: (Float, Float) -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(DeepElevated, CircleShape)
            .pointerInput(isInteractable) {
                if (!isInteractable) return@pointerInput
                detectTapGestures(
                    onTap = { onClick() }
                )
            }
            .pointerInput(isInteractable) {
                if (!isInteractable) return@pointerInput
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onMove(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text("RB", style = Typography.titleMedium, color = AccentPlasma)
    }
}

@Composable
fun ExpandedMenu(
    currentPid: Int,
    currentPackage: String,
    scanState: ScanState,
    writeState: WriteState,
    onScan: (Int, String) -> Unit,
    onRefine: (Int, Int) -> Unit,
    onWrite: (Int, Long, Int) -> Unit,
    onReset: () -> Unit,
    onGetRunningApps: suspend () -> List<com.gtc.rootbridgekotlin.core.memory.ProcessInfo>,
    onOverrideTarget: (Int, String) -> Unit,
    onClose: () -> Unit,
    onStopService: () -> Unit
) {
    var isEditingPid by remember { mutableStateOf(false) }
    var runningApps by remember { mutableStateOf<List<com.gtc.rootbridgekotlin.core.memory.ProcessInfo>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(false) }

    LaunchedEffect(isEditingPid) {
        if (isEditingPid) {
            isLoadingApps = true
            runningApps = onGetRunningApps()
            isLoadingApps = false
        }
    }

    Card(
        modifier = Modifier
            .width(360.dp)
            .heightIn(max = 500.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DeepElevated)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Memory Engine", style = Typography.titleMedium, color = TextPrimary)
                    if (isEditingPid) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Select Target Application", style = Typography.labelSmall, color = TextSecondary, modifier = Modifier.weight(1f))
                            TextButton(onClick = { isEditingPid = false }) {
                                Text("CANCEL", color = AccentError, style = Typography.labelSmall)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (currentPid > 0) "Target: $currentPackage (PID $currentPid)"
                                else "⚠ No target — Click SELECT APP",
                                style = Typography.labelSmall,
                                color = if (currentPid > 0) AccentPlasma else AccentError,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { isEditingPid = true }) {
                                Text("SELECT APP", color = AccentSignal, style = Typography.labelSmall)
                            }
                        }
                    }
                }
                Row {
                    IconButton(onClick = onClose) {
                        Text("-", color = TextSecondary, style = Typography.titleLarge)
                    }
                    IconButton(onClick = onStopService) {
                        Text("X", color = AccentError, style = Typography.titleLarge)
                    }
                }
            }
            
            if (currentPid <= 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentError.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        "PID sin detectar. Root activo y app en foreground son necesarios para escanear memoria real. Se usará PID=0 (modo demo).",
                        style = Typography.labelSmall,
                        color = AccentError
                    )
                }
            }
            
            if (isEditingPid) {
                Spacer(modifier = Modifier.height(8.dp))
                if (isLoadingApps) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentPlasma, modifier = Modifier.size(32.dp))
                    }
                } else if (runningApps.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No apps detected.", color = TextSecondary, style = Typography.labelSmall)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(runningApps) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DeepSurface, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                                    .padding(end = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.packageName, style = Typography.labelSmall, color = TextPrimary)
                                    Text("PID: ${app.pid}", style = Typography.bodySmall, color = TextSecondary)
                                }
                                Button(
                                    onClick = { 
                                        onOverrideTarget(app.pid, app.packageName)
                                        isEditingPid = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentPlasma),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("HOOK", color = DeepVoid)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    when (val state = scanState) {
                        is ScanState.Idle    -> SearchPhase(currentPid, onScan)
                        is ScanState.Scanning -> ScanningPanel(
                            scanState = state,
                            onCancel  = onReset
                        )
                        is ScanState.Error  -> ErrorPanel(
                            message = state.msg,
                            onRetry = onReset
                        )
                        is ScanState.Results -> ResultsPhase(
                            pid       = currentPid,
                            results   = state.results,
                            writeState = writeState,
                            onRefine  = onRefine,
                            onWrite   = onWrite,
                            onReset   = onReset
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ScanningPanel: live progress during a memory scan
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ScanningPanel(scanState: ScanState.Scanning, onCancel: () -> Unit) {
    val progress = if (scanState.regionsTotal > 0) {
        scanState.regionsScanned.toFloat() / scanState.regionsTotal.toFloat()
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "scanProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Target info row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (scanState.pid > 0) "PID ${scanState.pid}" else "Demo mode",
                style = Typography.labelSmall,
                color = AccentPlasma
            )
            Text(
                text = "Value: ${scanState.value}",
                style = Typography.labelSmall,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Animated progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(DeepSurface)
        ) {
            if (animatedProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .background(AccentPlasma)
                )
            } else {
                // Indeterminate: full-width pulsing bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(AccentPlasma.copy(alpha = 0.4f))
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Region counter
        Text(
            text = if (scanState.regionsTotal > 0) {
                "Region ${scanState.regionsScanned} / ${scanState.regionsTotal}"
            } else {
                "Scanning memory regions…"
            },
            style = Typography.labelSmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            border = androidx.compose.foundation.BorderStroke(1.dp, AccentError.copy(alpha = 0.6f))
        ) {
            Text("CANCEL", color = AccentError, style = Typography.labelMedium)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ErrorPanel: scan/write error with retry
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AccentError.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "⚠ Scan failed",
            style = Typography.labelMedium,
            color = AccentError
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = Typography.bodySmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = DeepSurface)
        ) {
            Text("NEW SCAN", color = AccentPlasma)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SearchPhase(pid: Int, onScan: (Int, String) -> Unit) {
    var query by remember { mutableStateOf("") }
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Enter value to search", color = TextSecondary) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
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
        Button(
            onClick = { onScan(pid, query) },
            enabled = query.trim().isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPlasma)
        ) {
            Text(if (pid > 0) "SCAN (PID $pid)" else "SCAN (demo)", color = DeepVoid)
        }
    }
}

@Composable
fun ResultsPhase(
    pid: Int,
    results: List<ScanResult>,
    writeState: WriteState,
    onRefine: (Int, Int) -> Unit,
    onWrite: (Int, Long, Int) -> Unit,
    onReset: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedAddress by remember { mutableStateOf<Long?>(null) }
    
    Column(modifier = Modifier.fillMaxHeight()) {
        Text("Found: ${results.size} matches", style = Typography.labelMedium, color = AccentPlasma)
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(results) { result ->
                val isSelected = selectedAddress == result.address
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) DeepSurface else Color.Transparent)
                        .padding(8.dp)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hexAddr = "0x" + result.address.toString(16).uppercase()
                    Text(hexAddr, style = Typography.bodySmall, color = TextPrimary)
                    Text("Value: ${result.getIntValue()}", style = Typography.bodySmall, color = TextSecondary)
                    TextButton(onClick = { selectedAddress = result.address }) {
                        Text("Edit", color = AccentSignal)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (selectedAddress != null) {
            Text("Edit Value for ${"0x" + selectedAddress!!.toString(16).uppercase()}", style = Typography.labelSmall, color = TextSecondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DeepSurface,
                        unfocusedContainerColor = DeepSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentPlasma,
                        focusedIndicatorColor = AccentPlasma
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { 
                        val v = query.trim().toIntOrNull()
                        if (v != null) onWrite(pid, selectedAddress!!, v)
                    },
                    enabled = query.trim().isNotEmpty() && writeState !is WriteState.Writing,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentSignal)
                ) {
                    Text("WRITE", color = DeepVoid)
                }
            }
            if (writeState is WriteState.Success) {
                Text("Write successful!", color = AccentSignal, style = Typography.labelSmall)
            } else if (writeState is WriteState.Error) {
                Text("Write failed", color = AccentError, style = Typography.labelSmall)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("New value", color = TextSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DeepSurface,
                        unfocusedContainerColor = DeepSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { 
                        query.toIntOrNull()?.let { onRefine(pid, it) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPlasma)
                ) {
                    Text("REFINE", color = DeepVoid)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("NEW SCAN", color = TextSecondary)
        }
    }
}
