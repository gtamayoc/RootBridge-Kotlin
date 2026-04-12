package com.gtc.rootbridgekotlin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.gtc.rootbridgekotlin.core.root.RootAccessState
import com.gtc.rootbridgekotlin.overlay.OverlayPermissionManager
import com.gtc.rootbridgekotlin.ui.theme.DeepVoid
import com.gtc.rootbridgekotlin.ui.theme.RootBridgeKotlinTheme
import com.gtc.rootbridgekotlin.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        com.gtc.rootbridgekotlin.core.memory.MemoryEngine.nativeLibDir = applicationInfo.nativeLibraryDir
        
        viewModel.checkRoot()

        setContent {
            RootBridgeKotlinTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DeepVoid
                ) {
                    AppNavHost(
                        viewModel = viewModel,
                        activity = this@MainActivity
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateOverlayStatus(OverlayPermissionManager.canDrawOverlays(this))
        
        val state = viewModel.uiState.value
        if (state.rootState is RootAccessState.Authorized && state.overlayGranted) {
            viewModel.scanActiveProcess()
        }
    }
}