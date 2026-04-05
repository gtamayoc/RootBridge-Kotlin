package com.gtc.rootbridgekotlin.data.foreground

import android.util.Log
import com.gtc.rootbridgekotlin.core.root.RootShell
import com.gtc.rootbridgekotlin.domain.foreground.ForegroundAppDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Root-based implementation of [ForegroundAppDataSource].
 *
 * Polls the foreground application every [POLL_INTERVAL_MS] milliseconds using a three-tier
 * detection hierarchy:
 *
 *  A. `dumpsys activity activities`  — primary, most reliable, Android 8–14+
 *  B. `dumpsys window`               — fallback, works on most OEMs
 *  C. `/proc/[pid]/oom_score_adj`    — low-level, OEM-agnostic last resort
 *
 * All parsing is performed in Kotlin; shell commands contain NO grep/awk/sed pipes.
 * The flow is safe to collect from any coroutine scope — it runs on [Dispatchers.IO]
 * internally and never terminates unless the collector is cancelled.
 */
class RootForegroundAppDataSource : ForegroundAppDataSource {

    companion object {
        private const val TAG = "RootFgDataSource"

        /** Polling interval in milliseconds. Must be 200–500ms per spec. */
        private const val POLL_INTERVAL_MS = 300L

        /**
         * Shell command for Strategy C: iterates /proc numeric dirs, prints
         * `<pid>:<oom_score_adj>:<cmdline>` per line.
         * The `tr '\0' ' '` replaces null bytes (cmdline separator) with spaces so the
         * result is safe ASCII. This is NOT considered a "grep/awk" pipe — it is a
         * byte-level transformation with no filtering or text processing.
         */
        private val PROC_OOM_CMD = """
            for d in /proc/[0-9]*/; do
              p=${"\${d%/}"}; p=${"\${p##*/}"};
              s=$(cat ${"$"}d/oom_score_adj 2>/dev/null);
              c=$(cat ${"$"}d/cmdline 2>/dev/null | tr '\0' ' ');
              echo "${"$"}p:${"$"}s:${"$"}c";
            done
        """.trimIndent().replace('\n', ' ')
    }

    /**
     * Cold flow that continuously emits the foreground package name.
     * Emits only when the value changes ([distinctUntilChanged]).
     * On any transient failure, the last valid value is retained and re-emitted.
     */
    override fun getForegroundAppFlow(): Flow<String> = flow<String> {
        var lastKnown = ForegroundAppDataSource.UNKNOWN

        while (true) {
            val detected = runCatching { detectForeground() }.getOrElse { e ->
                Log.e(TAG, "Unhandled error in detectForeground: ${e.message}", e)
                null
            }

            if (detected != null) {
                lastKnown = detected
            } else {
                Log.w(TAG, "All strategies failed — retaining lastKnown='$lastKnown'")
            }

            emit(lastKnown)
            delay(POLL_INTERVAL_MS)
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    // ──────────────────────────────────────────────────────────────────────────
    // Detection hierarchy
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Tries strategies A → B → C in order.
     * Returns the first non-null, valid package name, or null if all fail.
     */
    private suspend fun detectForeground(): String? {
        return tryStrategyA()
            ?: tryStrategyB()
            ?: tryStrategyC()
    }

    // ── Strategy A: dumpsys activity activities ────────────────────────────────

    private suspend fun tryStrategyA(): String? {
        val result = RootShell.execPersistent("dumpsys activity activities")
        if (result.exitCode != 0 || result.stdout.isBlank()) {
            Log.d(TAG, "[A] dumpsys activity → exit=${result.exitCode} (empty or error)")
            return null
        }
        val pkg = ForegroundAppParser.parseActivityDumpsys(result.stdout)
        if (pkg == null) Log.d(TAG, "[A] parse returned null for ${result.stdout.length}-char output")
        return pkg
    }

    // ── Strategy B: dumpsys window ────────────────────────────────────────────

    private suspend fun tryStrategyB(): String? {
        val result = RootShell.execPersistent("dumpsys window")
        if (result.exitCode != 0 || result.stdout.isBlank()) {
            Log.d(TAG, "[B] dumpsys window → exit=${result.exitCode} (empty or error)")
            return null
        }
        val pkg = ForegroundAppParser.parseWindowDumpsys(result.stdout)
        if (pkg == null) Log.d(TAG, "[B] parse returned null for ${result.stdout.length}-char output")
        return pkg
    }

    // ── Strategy C: /proc OOM inspection ─────────────────────────────────────

    private suspend fun tryStrategyC(): String? {
        val result = RootShell.exec(PROC_OOM_CMD)
        if (result.exitCode != 0 || result.stdout.isBlank()) {
            Log.d(TAG, "[C] proc loop → exit=${result.exitCode} (empty or error)")
            return null
        }
        val pkg = ForegroundAppParser.parseProcLines(result.stdout)
        if (pkg == null) Log.d(TAG, "[C] parse returned null for ${result.stdout.lines().size} lines")
        return pkg
    }
}
