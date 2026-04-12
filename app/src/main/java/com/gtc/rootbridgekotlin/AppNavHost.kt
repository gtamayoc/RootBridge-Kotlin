package com.gtc.rootbridgekotlin

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.gtc.rootbridgekotlin.core.root.RootAccessState
import com.gtc.rootbridgekotlin.overlay.OverlayPermissionManager
import com.gtc.rootbridgekotlin.overlay.OverlayService
import com.gtc.rootbridgekotlin.ui.screen.DashboardScreen
import com.gtc.rootbridgekotlin.ui.screen.OnboardingScreen
import com.gtc.rootbridgekotlin.ui.viewmodel.MainViewModel

@Composable
fun AppNavHost(
    viewModel: MainViewModel,
    activity: ComponentActivity
) {
    val state by viewModel.uiState.collectAsState()
    val isDashboard = state.rootState is RootAccessState.Authorized && state.overlayGranted
    
    AnimatedContent(targetState = isDashboard, label = "navigation") { showDashboard ->
        if (showDashboard) {
            DashboardScreen(
                state = state,
                onStartOverlay = {
                    activity.startService(Intent(activity, OverlayService::class.java))
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
                    OverlayPermissionManager.requestOverlayPermission(activity)
                }
            )
        }
    }
}
