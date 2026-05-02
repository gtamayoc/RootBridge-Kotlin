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
    onRefineExact: (Int) -> Unit,
    onRefineChanged: () -> Unit,
    onRefineUnchanged: () -> Unit,
    onWrite: (Int, Long, Int) -> Unit,
    onWriteAll: (Int, Int) -> Unit,
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
                onRefineExact = onRefineExact,
                onRefineChanged = onRefineChanged,
                onRefineUnchanged = onRefineUnchanged,
                onWrite = onWrite,
                onWriteAll = onWriteAll,
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
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
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
        Text("RB", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ExpandedMenu(
    currentPid: Int,
    currentPackage: String,
    scanState: ScanState,
    writeState: WriteState,
    onScan: (Int, String) -> Unit,
    onRefineExact: (Int) -> Unit,
    onRefineChanged: () -> Unit,
    onRefineUnchanged: () -> Unit,
    onWrite: (Int, Long, Int) -> Unit,
    onWriteAll: (Int, Int) -> Unit,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Memory Engine", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (isEditingPid) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Select Target Application", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = { isEditingPid = false }) {
                                Text("CANCEL", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (currentPid > 0) "Target: $currentPackage (PID $currentPid)"
                                else "⚠ No target — Click SELECT APP",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (currentPid > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { isEditingPid = true }) {
                                Text("SELECT APP", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Row {
                    IconButton(onClick = onClose) {
                        Text("-", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = onStopService) {
                        Text("X", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            
            if (currentPid <= 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        "PID sin detectar. Root activo y app en foreground son necesarios para escanear memoria real. Se usará PID=0 (modo demo).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            if (isEditingPid) {
                Spacer(modifier = Modifier.height(8.dp))
                if (isLoadingApps) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    }
                } else if (runningApps.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No apps detected.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(runningApps) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                                    .padding(end = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                    Text("PID: ${app.pid}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Button(
                                    onClick = { 
                                        onOverrideTarget(app.pid, app.packageName)
                                        isEditingPid = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("HOOK", color = MaterialTheme.colorScheme.background)
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
                            pid              = currentPid,
                            results          = state.results,
                            totalResults     = state.totalResults,
                            writeState       = writeState,
                            onRefineExact    = onRefineExact,
                            onRefineChanged  = onRefineChanged,
                            onRefineUnchanged = onRefineUnchanged,
                            onWrite          = onWrite,
                            onWriteAll       = onWriteAll,
                            onReset          = onReset
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
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Value: ${scanState.value}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Animated progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (animatedProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            } else {
                // Indeterminate: full-width pulsing bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
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
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
        ) {
            Text("CANCEL", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
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
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "⚠ Scan failed",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Text("NEW SCAN", color = MaterialTheme.colorScheme.primary)
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
            placeholder = { Text("Enter value to search", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onScan(pid, query) },
            enabled = query.trim().isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(if (pid > 0) "SCAN (PID $pid)" else "SCAN (demo)", color = MaterialTheme.colorScheme.background)
        }
    }
}

@Composable
fun ResultsPhase(
    pid: Int,
    results: List<ScanResult>,
    totalResults: Int,
    writeState: WriteState,
    onRefineExact: (Int) -> Unit,
    onRefineChanged: () -> Unit,
    onRefineUnchanged: () -> Unit,
    onWrite: (Int, Long, Int) -> Unit,
    onWriteAll: (Int, Int) -> Unit,
    onReset: () -> Unit
) {
    var exactQuery by remember { mutableStateOf("") }
    var writeAllQuery by remember { mutableStateOf("") }
    var isWriteAllMode by remember { mutableStateOf(false) }
    var selectedAddress by remember { mutableStateOf<Long?>(null) }
    var editQuery by remember { mutableStateOf("") }

    // If results change after a filter and the selected address is no longer present, clear selection
    LaunchedEffect(results) {
        if (selectedAddress != null && results.none { it.address == selectedAddress }) {
            selectedAddress = null
            editQuery = ""
        }
    }

    val isEditing = selectedAddress != null

    Column(modifier = Modifier.fillMaxHeight()) {

        // ── Result count banner ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Total: $totalResults addresses",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (results.size < totalResults) {
                Text(
                    text = "Showing ${results.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Address list ──
        LazyColumn(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()) {
            items(results) { result ->
                val isSelected = selectedAddress == result.address
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "0x" + result.address.toString(16).uppercase(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Value: ${result.getIntValue()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = {
                        if (isSelected) {
                            selectedAddress = null
                            editQuery = ""
                        } else {
                            selectedAddress = result.address
                            editQuery = result.getIntValue().toString() // pre-fill current value
                        }
                    }) {
                        Text(
                            if (isSelected) "◄ BACK" else "EDIT",
                            color = if (isSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── EXCLUSIVE bottom panel: only ONE active at a time ──
        if (isEditing) {

            // ────── WRITE PANEL ──────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                // Header with back button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✏  0x${selectedAddress!!.toString(16).uppercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    TextButton(onClick = {
                        selectedAddress = null
                        editQuery = ""
                    }) {
                        Text("◄ BACK", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = editQuery,
                        onValueChange = { editQuery = it },
                        placeholder = { Text("New value", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.secondary,
                            focusedIndicatorColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            editQuery.trim().toIntOrNull()?.let { v ->
                                onWrite(pid, selectedAddress!!, v)
                            }
                        },
                        enabled = editQuery.trim().isNotEmpty() && writeState !is WriteState.Writing,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        if (writeState is WriteState.Writing)
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.background,
                                strokeWidth = 2.dp
                            )
                        else
                            Text("WRITE", color = MaterialTheme.colorScheme.background)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                when (writeState) {
                    is WriteState.Success -> Text(
                        "✓ Write successful",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    is WriteState.Error -> Text(
                        "✗ ${writeState.msg}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                    else -> {}
                }
            }

        } else if (isWriteAllMode) {
            // ────── WRITE ALL PANEL (Bulk) ──────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🚀  Write All ($totalResults addresses)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = { isWriteAllMode = false }) {
                        Text("◄ BACK", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = writeAllQuery,
                        onValueChange = { writeAllQuery = it },
                        placeholder = { Text("Value for ALL", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            writeAllQuery.trim().toIntOrNull()?.let { v ->
                                onWriteAll(pid, v)
                                isWriteAllMode = false // Close after trigger
                            }
                        },
                        enabled = writeAllQuery.trim().isNotEmpty() && writeState !is WriteState.Writing,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (writeState is WriteState.Writing)
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.background,
                                strokeWidth = 2.dp
                            )
                        else
                            Text("ALL", color = MaterialTheme.colorScheme.background)
                    }
                }
            }

        } else {

            // ────── REFINE PANEL ──────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text("Refine Results", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))

                // Exact value filter
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = exactQuery,
                        onValueChange = { exactQuery = it },
                        placeholder = { Text("Filter by exact value…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { exactQuery.trim().toIntOrNull()?.let { onRefineExact(it) } },
                        enabled = exactQuery.trim().isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("EXACT", color = MaterialTheme.colorScheme.background, style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Dynamic change filter buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRefineChanged,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f)
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("CHANGED", color = MaterialTheme.colorScheme.background, style = MaterialTheme.typography.labelSmall)
                            Text("values differ", color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Button(
                        onClick = onRefineUnchanged,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("UNCHANGED", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall)
                            Text("values same", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // WRITE ALL TRIGGER BUTTON
                Button(
                    onClick = { isWriteAllMode = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Text("BULK MODIFY ALL ($totalResults)", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("NEW SCAN", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}

