package com.gtc.rootbridgekotlin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gtc.rootbridgekotlin.core.root.RootAccessState
import com.gtc.rootbridgekotlin.core.root.RootChecker
import com.gtc.rootbridgekotlin.core.memory.ProcessInfo
import com.gtc.rootbridgekotlin.core.memory.ProcessScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val rootState: RootAccessState = RootAccessState.Idle,
    val overlayGranted: Boolean = false,
    val activeProcess: ProcessInfo? = null,
    val isCheckingProcess: Boolean = false
)

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun checkRoot() {
        _uiState.value = _uiState.value.copy(rootState = RootAccessState.Checking)
        viewModelScope.launch {
            val state = RootChecker.checkRootAccess()
            _uiState.value = _uiState.value.copy(rootState = state)
            if (state is RootAccessState.Authorized) {
                scanActiveProcess()
            }
        }
    }

    fun updateOverlayStatus(granted: Boolean) {
        _uiState.value = _uiState.value.copy(overlayGranted = granted)
    }

    fun scanActiveProcess() {
        _uiState.value = _uiState.value.copy(isCheckingProcess = true)
        viewModelScope.launch {
            val process = ProcessScanner.getForegroundProcess()
            _uiState.value = _uiState.value.copy(activeProcess = process, isCheckingProcess = false)
        }
    }
}
