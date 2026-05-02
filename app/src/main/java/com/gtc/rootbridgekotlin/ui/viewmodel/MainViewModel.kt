package com.gtc.rootbridgekotlin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gtc.rootbridgekotlin.core.root.RootAccessState
import com.gtc.rootbridgekotlin.core.root.RootChecker
import com.gtc.rootbridgekotlin.core.memory.ProcessInfo
import com.gtc.rootbridgekotlin.core.memory.ProcessScanner
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
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
        _uiState.update { it.copy(rootState = RootAccessState.Checking) }
        viewModelScope.launch {
            val state = RootChecker.checkRootAccess()
            _uiState.update { it.copy(rootState = state) }
            if (state is RootAccessState.Authorized) {
                scanActiveProcess()
            }
        }
    }

    fun updateOverlayStatus(granted: Boolean) {
        _uiState.update { it.copy(overlayGranted = granted) }
    }

    fun scanActiveProcess() {
        _uiState.update { it.copy(isCheckingProcess = true) }
        viewModelScope.launch {
            val process = ProcessScanner.getForegroundProcess()
            _uiState.update { it.copy(activeProcess = process, isCheckingProcess = false) }
        }
    }
}
