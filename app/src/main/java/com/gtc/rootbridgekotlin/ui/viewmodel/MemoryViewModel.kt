package com.gtc.rootbridgekotlin.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gtc.rootbridgekotlin.core.memory.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ScanState {
    data object Idle : ScanState()
    /** Active scan in progress. [regionsScanned]/[regionsTotal] shows region-level progress (0 = unknown). */
    data class Scanning(
        val pid: Int = -1,
        val value: Int = 0,
        val regionsScanned: Int = 0,
        val regionsTotal: Int = 0
    ) : ScanState()
    data class Results(val results: List<ScanResult>) : ScanState()
    data class Error(val msg: String) : ScanState()
}

sealed class WriteState {
    data object Idle : WriteState()
    data object Writing : WriteState()
    data object Success : WriteState()
    data class Error(val msg: String) : WriteState()
}

class MemoryViewModel : ViewModel() {

    private val TAG = "MemoryViewModel"

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _writeState = MutableStateFlow<WriteState>(WriteState.Idle)
    val writeState: StateFlow<WriteState> = _writeState.asStateFlow()

    /**
     * The PID that was active when the last scan was started.
     * This is preserved so that refine / write always operate on the same target
     * even if the foreground app changes afterwards.
     */
    private val _lockedPid = MutableStateFlow(-1)
    val lockedPid: StateFlow<Int> = _lockedPid.asStateFlow()

    // ──────────────────────────────────────────────────────────────────────────
    // SCAN
    // ──────────────────────────────────────────────────────────────────────────

    fun scan(pid: Int, valueStr: String, type: DataType = DataType.DWORD) {
        Log.d(TAG, "scan() — pid=$pid valueStr='$valueStr'")

        val value = valueStr.trim().toIntOrNull()
        if (value == null) {
            Log.e(TAG, "scan() — invalid number: '$valueStr'")
            _scanState.value = ScanState.Error("Invalid number: '$valueStr'")
            return
        }

        // Lock the PID for this scan session
        _lockedPid.value = pid
        _scanState.value = ScanState.Scanning(pid = pid, value = value)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "scan() — starting real scan: pid=$pid value=$value type=$type")
                val results = MemoryEngine.scanValue(pid, value, type) { scanned, total ->
                    // Emit progress update on each region completion
                    _scanState.value = ScanState.Scanning(
                        pid = pid,
                        value = value,
                        regionsScanned = scanned,
                        regionsTotal = total
                    )
                }
                Log.i(TAG, "scan() — completed: ${results.size} results for pid=$pid")

                _scanState.value = if (results.isEmpty()) {
                    ScanState.Error("No matches found for value=$value in PID $pid")
                } else {
                    ScanState.Results(results)
                }
            } catch (e: Exception) {
                Log.e(TAG, "scan() — exception: ${e.message}", e)
                _scanState.value = ScanState.Error(e.message ?: "Unknown scan error")
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // REFINE
    // ──────────────────────────────────────────────────────────────────────────

    fun refine(pid: Int, newValue: Int) {
        val currentState = _scanState.value
        if (currentState !is ScanState.Results) {
            Log.w(TAG, "refine() — called but state is not Results, ignoring")
            return
        }

        // Always use the locked PID from the original scan, not the current foreground PID
        val targetPid = if (_lockedPid.value > 0) _lockedPid.value else pid
        Log.d(TAG, "refine() — targetPid=$targetPid newValue=$newValue")
        _scanState.value = ScanState.Scanning(pid = targetPid, value = newValue)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newResults = MemoryEngine.filterResults(targetPid, currentState.results, newValue)
                Log.i(TAG, "refine() — ${newResults.size} addresses still match $newValue")
                _scanState.value = if (newResults.isEmpty()) {
                    ScanState.Error("No addresses match $newValue after refinement")
                } else {
                    ScanState.Results(newResults)
                }
            } catch (e: Exception) {
                Log.e(TAG, "refine() — exception: ${e.message}", e)
                _scanState.value = ScanState.Error(e.message ?: "Refine error")
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // WRITE
    // ──────────────────────────────────────────────────────────────────────────

    fun writeValue(pid: Int, address: Long, value: Int, type: DataType = DataType.DWORD) {
        // Always prefer the locked PID from the original scan
        val targetPid = if (_lockedPid.value > 0) _lockedPid.value else pid
        Log.d(TAG, "writeValue() — targetPid=$targetPid addr=0x${address.toString(16)} value=$value")

        _writeState.value = WriteState.Writing
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = MemoryEngine.writeValue(targetPid, address, value, type)
                if (success) {
                    Log.i(TAG, "writeValue() — SUCCESS at 0x${address.toString(16)}")
                    _writeState.value = WriteState.Success
                    kotlinx.coroutines.delay(2000)
                    _writeState.value = WriteState.Idle
                } else {
                    Log.e(TAG, "writeValue() — FAILED at 0x${address.toString(16)}")
                    _writeState.value = WriteState.Error("dd/python write failed at 0x${address.toString(16)}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "writeValue() — exception: ${e.message}", e)
                _writeState.value = WriteState.Error(e.message ?: "Write error")
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // RESET
    // ──────────────────────────────────────────────────────────────────────────

    fun reset() {
        Log.d(TAG, "reset() — clearing scan and write state")
        _scanState.value = ScanState.Idle
        _writeState.value = WriteState.Idle
        _lockedPid.value = -1
    }
}
