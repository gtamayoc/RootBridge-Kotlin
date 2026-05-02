package com.gtc.rootbridgekotlin.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gtc.rootbridgekotlin.core.memory.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

// ────────────────────────────────────────────────────────────────────────────
// State definitions
// ────────────────────────────────────────────────────────────────────────────

sealed class ScanState {
    data object Idle : ScanState()

    data class Scanning(
        val pid:            Int = -1,
        val value:          Int = 0,
        val regionsScanned: Int = 0,
        val regionsTotal:   Int = 0
    ) : ScanState()

    /**
     * @param results       Display slice (≤ DISPLAY_LIMIT) shown in the UI.
     * @param totalResults  TRUE total count of matching addresses (kept by C++ in session file).
     */
    data class Results(
        val results:       List<ScanResult>,
        val totalResults:  Int,
        val dataType:      DataType = DataType.DWORD
    ) : ScanState()

    data class Error(val msg: String) : ScanState()
}

sealed class WriteState {
    data object Idle    : WriteState()
    data object Writing : WriteState()
    data object Success : WriteState()
    data class  Error(val msg: String) : WriteState()
}

// ────────────────────────────────────────────────────────────────────────────
// ViewModel
// ────────────────────────────────────────────────────────────────────────────

class MemoryViewModel : ViewModel() {

    private val TAG = "MemoryViewModel"

    /** Maximum addresses to pull from C++ session for UI display. */
    private val DISPLAY_LIMIT = 500

    private val _scanState  = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState:  StateFlow<ScanState>  = _scanState.asStateFlow()

    private val _writeState = MutableStateFlow<WriteState>(WriteState.Idle)
    val writeState: StateFlow<WriteState> = _writeState.asStateFlow()

    /**
     * PID locked at scan time. Refine/write always targets this, even if
     * the foreground app changes afterwards.
     */
    private val _lockedPid   = MutableStateFlow(-1)
    val lockedPid: StateFlow<Int> = _lockedPid.asStateFlow()

    /** DataType used in the current scan session (needed for refresh). */
    private var sessionType: DataType = DataType.DWORD

    private var refreshJob: kotlinx.coroutines.Job? = null

    // ────────────────────────────────────────────────────────────────────────
    // SCAN
    // ────────────────────────────────────────────────────────────────────────

    fun scan(pid: Int, valueStr: String, type: DataType = DataType.DWORD) {
        val value = valueStr.trim().toIntOrNull() ?: run {
            _scanState.value = ScanState.Error("Invalid number: '$valueStr'")
            return
        }

        _lockedPid.value = pid
        sessionType      = type
        _scanState.value = ScanState.Scanning(pid = pid, value = value)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "scan() — pid=$pid value=$value type=$type")

                var lastEmitTime = 0L
                val total = MemoryEngine.scanValue(pid, value, type) { scanned, totalRegions ->
                    val now = System.currentTimeMillis()
                    if (now - lastEmitTime > 50 || scanned == totalRegions) {
                        lastEmitTime = now
                        _scanState.value = ScanState.Scanning(
                            pid = pid, value = value,
                            regionsScanned = scanned, regionsTotal = totalRegions
                        )
                    }
                }

                when {
                    total < 0 -> _scanState.value = ScanState.Error("Scanner binary not available")
                    total == 0 -> _scanState.value = ScanState.Error("No matches for value=$value in PID $pid")
                    else -> {
                        val displayList = MemoryEngine.fetchResults(pid, type, DISPLAY_LIMIT)
                        Log.i(TAG, "scan() — total=$total displaying=${displayList.size}")
                        _scanState.value = ScanState.Results(displayList, total, type)
                        startRefreshJob(pid, type)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "scan() — exception: ${e.message}", e)
                _scanState.value = ScanState.Error(e.message ?: "Unknown scan error")
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // REFINE — Exact value match filter
    // ────────────────────────────────────────────────────────────────────────

    fun refineExact(newValue: Int) {
        val state = _scanState.value as? ScanState.Results ?: return
        val pid   = _lockedPid.value.takeIf { it > 0 } ?: return

        _scanState.value = ScanState.Scanning(pid = pid, value = newValue)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val total = MemoryEngine.filterExact(pid, newValue, state.dataType)
                handleFilterResult(total, pid, state.dataType, "refineExact($newValue)")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "refineExact — ${e.message}", e)
                _scanState.value = ScanState.Error(e.message ?: "Refine error")
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // FILTER CHANGED — Keep only addresses whose value changed from initial scan
    // ────────────────────────────────────────────────────────────────────────

    fun refineChanged() {
        val state = _scanState.value as? ScanState.Results ?: return
        val pid   = _lockedPid.value.takeIf { it > 0 } ?: return

        _scanState.value = ScanState.Scanning(pid = pid, value = 0)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val total = MemoryEngine.filterChanged(pid)
                handleFilterResult(total, pid, state.dataType, "refineChanged")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "refineChanged — ${e.message}", e)
                _scanState.value = ScanState.Error(e.message ?: "Filter error")
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // FILTER UNCHANGED — Keep only addresses whose value stayed the same
    // ────────────────────────────────────────────────────────────────────────

    fun refineUnchanged() {
        val state = _scanState.value as? ScanState.Results ?: return
        val pid   = _lockedPid.value.takeIf { it > 0 } ?: return

        _scanState.value = ScanState.Scanning(pid = pid, value = 0)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val total = MemoryEngine.filterUnchanged(pid)
                handleFilterResult(total, pid, state.dataType, "refineUnchanged")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "refineUnchanged — ${e.message}", e)
                _scanState.value = ScanState.Error(e.message ?: "Filter error")
            }
        }
    }

    // Shared post-filter logic
    private suspend fun handleFilterResult(
        total:    Int,
        pid:      Int,
        type:     DataType,
        label:    String
    ) {
        when {
            total < 0 -> _scanState.value = ScanState.Error("Filter failed")
            total == 0 -> _scanState.value = ScanState.Error("No addresses remained after $label")
            else -> {
                val displayList = MemoryEngine.fetchResults(pid, type, DISPLAY_LIMIT)
                Log.i(TAG, "$label — total=$total displaying=${displayList.size}")
                _scanState.value = ScanState.Results(displayList, total, type)
                startRefreshJob(pid, type)
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // WRITE
    // ────────────────────────────────────────────────────────────────────────

    fun writeValue(pid: Int, address: Long, value: Int, type: DataType = DataType.DWORD) {
        val targetPid = _lockedPid.value.takeIf { it > 0 } ?: pid
        _writeState.value = WriteState.Writing

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ok = MemoryEngine.writeValue(targetPid, address, value, type)
                _writeState.value = if (ok) WriteState.Success else WriteState.Error("Write failed")
                if (ok) {
                    kotlinx.coroutines.delay(2000)
                    _writeState.value = WriteState.Idle
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "writeValue — ${e.message}", e)
                _writeState.value = WriteState.Error(e.message ?: "Write error")
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // WRITE ALL
    // ────────────────────────────────────────────────────────────────────────

    fun writeAllValue(pid: Int, value: Int) {
        val state = _scanState.value as? ScanState.Results ?: return
        val type  = state.dataType
        val targetPid = _lockedPid.value.takeIf { it > 0 } ?: pid
        _writeState.value = WriteState.Writing

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = MemoryEngine.intToLEBytes(value, type.byteSize)
                val successCount = MemoryEngine.writeAllBytes(targetPid, bytes)
                
                if (successCount > 0) {
                    _writeState.value = WriteState.Success
                    kotlinx.coroutines.delay(2000)
                    _writeState.value = WriteState.Idle
                    refreshImmediate()
                } else {
                    _writeState.value = WriteState.Error("No addresses written")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _writeState.value = WriteState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun refreshImmediate() {
        val state = _scanState.value as? ScanState.Results ?: return
        val pid   = _lockedPid.value.takeIf { it > 0 } ?: return
        
        viewModelScope.launch(Dispatchers.IO) {
            val displayList = MemoryEngine.fetchResults(pid, state.dataType, DISPLAY_LIMIT)
            _scanState.update { currentState ->
                if (currentState is ScanState.Results) {
                    currentState.copy(results = displayList)
                } else {
                    currentState
                }
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // RESET
    // ────────────────────────────────────────────────────────────────────────

    fun reset() {
        refreshJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            MemoryEngine.clearSession()
        }
        _scanState.value  = ScanState.Idle
        _writeState.value = WriteState.Idle
        _lockedPid.value  = -1
    }

    // ────────────────────────────────────────────────────────────────────────
    // BACKGROUND REFRESH JOB  (live-updates the visible 25 entries)
    // ────────────────────────────────────────────────────────────────────────

    private fun startRefreshJob(pid: Int, type: DataType) {
        refreshJob?.cancel()
        if (pid <= 0) return

        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                kotlinx.coroutines.delay(2000L)

                val state = _scanState.value as? ScanState.Results ?: continue
                if (state.results.isEmpty()) continue

                // Only refresh the visible portion to save root-shell bandwidth
                val topSlice    = state.results.take(25)
                val refreshed   = MemoryEngine.refreshAddresses(pid, topSlice)
                val merged      = refreshed + state.results.drop(25)

                _scanState.update { currentState ->
                    if (currentState is ScanState.Results) {
                        currentState.copy(results = merged)
                    } else {
                        currentState
                    }
                }
            }
        }
    }
}
