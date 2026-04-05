package com.gtc.rootbridgekotlin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.gtc.rootbridgekotlin.core.root.RootAccessState
import com.gtc.rootbridgekotlin.overlay.OverlayPermissionManager
import com.gtc.rootbridgekotlin.overlay.OverlayService
import com.gtc.rootbridgekotlin.ui.screen.DashboardScreen
import com.gtc.rootbridgekotlin.ui.screen.OnboardingScreen
import com.gtc.rootbridgekotlin.ui.theme.DeepVoid
import com.gtc.rootbridgekotlin.ui.theme.RootBridgeKotlinTheme
import com.gtc.rootbridgekotlin.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        viewModel.checkRoot()

        setContent {
            RootBridgeKotlinTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DeepVoid
                ) {
                    val state by viewModel.uiState.collectAsState()
                    
                    val isDashboard = state.rootState is RootAccessState.Authorized && state.overlayGranted
                    
                    AnimatedContent(targetState = isDashboard, label = "navigation") { showDashboard ->
                        if (showDashboard) {
                            DashboardScreen(
                                state = state,
                                onStartOverlay = {
                                    startService(Intent(this@MainActivity, OverlayService::class.java))
                                },
                                onRefreshProcess = {
                                    viewModel.scanActiveProcess()
                                }
                            )
                        } else {
                            OnboardingScreen(
                                state = state,
                                onCheckRoot = { viewModel.checkRoot() },
                                onRequestOverlay = {
                                    OverlayPermissionManager.requestOverlayPermission(this@MainActivity)
                                }
                            )
                        }
                    }
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