package com.gtc.rootbridgekotlin.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.gtc.rootbridgekotlin.MainActivity
import com.gtc.rootbridgekotlin.core.memory.ProcessScanner
import com.gtc.rootbridgekotlin.domain.foreground.ForegroundAppDataSource
import com.gtc.rootbridgekotlin.domain.foreground.ForegroundAppRepository
import com.gtc.rootbridgekotlin.overlay.ui.OverlayContent
import com.gtc.rootbridgekotlin.ui.theme.RootBridgeKotlinTheme
import com.gtc.rootbridgekotlin.ui.viewmodel.MemoryViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    private val memoryViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(
            this,
            androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[MemoryViewModel::class.java]
    }
    
    private lateinit var layoutParams: WindowManager.LayoutParams
    private val isExpanded = MutableStateFlow(false)
    private val overlayAlpha = MutableStateFlow(1f)
    private val isInteractable = MutableStateFlow(true)
    private val currentAppPackage = MutableStateFlow(ForegroundAppDataSource.UNKNOWN)
    private val currentAppPid = MutableStateFlow(-1)
    
    private val manualPidOverride = MutableStateFlow<Int?>(null)

    /** Repository wired to this service's lifecycle scope. */
    private val foregroundRepo by lazy { ForegroundAppRepository(lifecycleScope) }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupOverlay()
        startForeground(1, createNotification())

        startForegroundMonitor()
    }

    private fun isLauncherPackage(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfos = packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfos.any { it.activityInfo.packageName == packageName }
    }

    private fun isSystemApp(packageName: String): Boolean {
        if (packageName == this.packageName) return true
        if (packageName == "com.android.systemui" || packageName == "android") return true
        
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Collects the [ForegroundAppRepository.foregroundApp] StateFlow.
     * For every new package name detected (and if no manual override is active):
     *  1. Updates [currentAppPackage].
     *  2. Resolves the PID via [ProcessScanner.resolvePidForPackage].
     *  3. Adjusts overlay interactability based on launcher/system status.
     */
    private fun startForegroundMonitor() {
        lifecycleScope.launch {
            foregroundRepo.foregroundApp.collectLatest { pkg ->
                if (manualPidOverride.value != null) return@collectLatest
                if (pkg == ForegroundAppDataSource.UNKNOWN) return@collectLatest

                val prevPkg = currentAppPackage.value
                currentAppPackage.value = pkg

                if (prevPkg != pkg) {
                    Log.i("OverlayService", "Foreground → $pkg")
                    resolvePidForPackage(pkg)
                }

                val isLauncher = isLauncherPackage(pkg)
                val isSystem = isSystemApp(pkg)
                val isInvalid = isLauncher || isSystem

                if (isInvalid) {
                    if (isInteractable.value) {
                        isInteractable.value = false
                        overlayAlpha.value = 0.3f
                        if (isExpanded.value) setExpanded(false)
                        updateWindowFlags()
                    }
                } else {
                    if (!isInteractable.value) {
                        isInteractable.value = true
                        overlayAlpha.value = 1.0f
                        updateWindowFlags()
                    }
                }
            }
        }
    }

    /** Resolves PID asynchronously so the UI does not block on the shell call. */
    private fun resolvePidForPackage(pkg: String) {
        lifecycleScope.launch {
            // Utilizamos resolvePid directamente con el paquete que ya sabemos que está en foreground
            val process = ProcessScanner.resolvePid(pkg, "OverlayService") 
                ?: ProcessScanner.getForegroundProcess()
            
            if (process != null && process.packageName == pkg) {
                currentAppPid.value = process.pid
                Log.i("OverlayService", "PID resolved → $pkg PID=${process.pid}")
            } else if (process != null) {
                // If it found something else but we need something, just accept it
                currentAppPid.value = process.pid
                currentAppPackage.value = process.packageName
                Log.i("OverlayService", "PID fallback resolved → ${process.packageName} PID=${process.pid}")
            } else {
                Log.w("OverlayService", "PID resolution mismatch or null for '$pkg'")
            }
        }
    }

    private fun setExpanded(expanded: Boolean) {
        if (!isInteractable.value && expanded) return // Prevent expanding if not interactable
        isExpanded.value = expanded
        updateWindowFlags()
    }
    
    private fun updateWindowFlags() {
        if (!::layoutParams.isInitialized || !::composeView.isInitialized) return
        
        if (isExpanded.value) {
            // Allow keyboard input: remove NOT_FOCUSABLE. Add NOT_TOUCH_MODAL to not block background completely
            layoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            // Icon mode: block focus to not show keyboard
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
        
        if (!isInteractable.value) {
            // If in invalid app context, block touches entirely
            layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        
        try {
            windowManager.updateViewLayout(composeView, layoutParams)
        } catch (e: Exception) {
            // Ignore if view not attached
        }
    }

    private fun updatePosition(dx: Float, dy: Float) {
        if (!::layoutParams.isInitialized) return
        layoutParams.x += dx.toInt()
        layoutParams.y += dy.toInt()
        try {
            windowManager.updateViewLayout(composeView, layoutParams)
        } catch (e: Exception) {
        }
    }

    private fun setupOverlay() {
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                RootBridgeKotlinTheme {
                    OverlayContent(
                        isExpandedFlow = isExpanded,
                        alphaFlow = overlayAlpha,
                        interactableFlow = isInteractable,
                        pidFlow = currentAppPid,
                        packageFlow = currentAppPackage,
                        scanStateFlow = memoryViewModel.scanState,
                        writeStateFlow = memoryViewModel.writeState,
                        onExpandedChange = { setExpanded(it) },
                        onMove = { dx, dy -> updatePosition(dx, dy) },
                        onClose = { stopSelf() },
                        onScan = { pid, valueStr -> memoryViewModel.scan(pid, valueStr) },
                        onRefineExact = { value -> memoryViewModel.refineExact(value) },
                        onRefineChanged = { memoryViewModel.refineChanged() },
                        onRefineUnchanged = { memoryViewModel.refineUnchanged() },
                        onWrite = { pid, address, value -> memoryViewModel.writeValue(pid, address, value) },
                        onReset = { memoryViewModel.reset() },
                        onGetRunningApps = { ProcessScanner.getRunningApplications() },  // suspend lambda — called from LaunchedEffect
                        onOverrideTarget = { pid, pkg -> 
                            manualPidOverride.value = pid
                            currentAppPid.value = pid
                            currentAppPackage.value = pkg
                            isInteractable.value = true
                            overlayAlpha.value = 1f
                            updateWindowFlags()
                        }
                    )
                }
            }
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        windowManager.addView(composeView, layoutParams)
        updateWindowFlags()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::composeView.isInitialized) {
            try { windowManager.removeView(composeView) } catch (e: Exception) {}
            composeView.disposeComposition()
        }
        store.clear()
    }

    private fun createNotification(): Notification {
        val channelId = "overlay_service_channel"
        val channel = NotificationChannel(
            channelId,
            "Memory Scanner Overlay",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, channelId)
            .setContentTitle("Memory Scanner")
            .setContentText("Overlay is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
    }
}
