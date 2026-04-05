package com.gtc.rootbridgekotlin.domain.foreground

import android.util.Log
import com.gtc.rootbridgekotlin.data.foreground.RootForegroundAppDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

/**
 * Converts the cold [ForegroundAppDataSource] flow into a hot [StateFlow] suitable
 * for consumption by the UI layer (Overlay, ViewModel).
 *
 * Usage:
 * ```kotlin
 * val repo = ForegroundAppRepository(lifecycleScope)
 * val pkg by repo.foregroundApp.collectAsState()
 * ```
 *
 * The repository is lifecycle-aware: pass a [CoroutineScope] tied to the desired
 * lifetime (e.g. `lifecycleScope` in [OverlayService], `viewModelScope` in a ViewModel).
 *
 * @param scope Coroutine scope that owns the sharing coroutine.
 * @param dataSource The underlying data source. Defaults to [RootForegroundAppDataSource].
 */
class ForegroundAppRepository(
    scope: CoroutineScope,
    private val dataSource: ForegroundAppDataSource = RootForegroundAppDataSource()
) {
    private val TAG = "ForegroundAppRepo"

    /**
     * Hot [StateFlow] that always holds the latest foreground package name.
     * Initial value is [ForegroundAppDataSource.UNKNOWN] until the first emission.
     *
     * Uses [SharingStarted.WhileSubscribed] with a 5-second timeout so the upstream
     * polling loop stops when no collector is active (e.g. overlay minimised to icon).
     */
    val foregroundApp: StateFlow<String> = dataSource
        .getForegroundAppFlow()
        .catch { e ->
            Log.e(TAG, "foregroundApp flow error: ${e.message}", e)
            emit(ForegroundAppDataSource.UNKNOWN)
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = ForegroundAppDataSource.UNKNOWN
        )
}
