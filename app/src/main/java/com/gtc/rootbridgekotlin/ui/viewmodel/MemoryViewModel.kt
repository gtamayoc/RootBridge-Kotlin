package com.gtc.rootbridgekotlin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gtc.rootbridgekotlin.core.memory.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ScanState {
    data object Idle : ScanState()
    data object Scanning : ScanState()
    data class Results(val results: List<ScanResult>) : ScanState()
    data class Error(val msg: String) : ScanState()
}

class MemoryViewModel : ViewModel() {
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    
    fun scan(pid: Int, value: Int) {
        _scanState.value = ScanState.Scanning
        viewModelScope.launch {
            try {
                val results = MemoryEngine.scanValue(pid, value, DataType.DWORD)
                _scanState.value = ScanState.Results(results)
            } catch (e: Exception) {
                _scanState.value = ScanState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
