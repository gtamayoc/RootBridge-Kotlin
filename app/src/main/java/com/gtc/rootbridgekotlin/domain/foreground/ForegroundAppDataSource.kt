package com.gtc.rootbridgekotlin.domain.foreground

import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for streaming the foreground application package name.
 *
 * Implementations MUST:
 *  - Emit a new value only when the package name changes ([distinctUntilChanged]).
 *  - Retain and re-emit the last known valid value on transient failures.
 *  - Never complete or throw; errors should be logged and swallowed internally.
 */
interface ForegroundAppDataSource {
    /**
     * A cold [Flow] that emits the package name of the currently-visible application.
     * Emits [UNKNOWN] as the initial sentinel until the first successful detection.
     */
    fun getForegroundAppFlow(): Flow<String>

    companion object {
        /** Sentinel emitted when no package name can be resolved. */
        const val UNKNOWN = "UNKNOWN"
    }
}
