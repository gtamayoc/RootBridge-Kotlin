package com.gtc.rootbridgekotlin.data.foreground

import android.util.Log

/**
 * Pure-Kotlin parser for foreground app detection.
 *
 * Processes raw shell output entirely in Kotlin — no grep/awk/sed pipes needed in the shell.
 * Designed to be position-independent and OEM-agnostic via pattern matching only.
 *
 * Parsing hierarchy (highest priority first):
 *  - [parseActivityDumpsys]  → `dumpsys activity activities` (Strategy A)
 *  - [parseWindowDumpsys]    → `dumpsys window`              (Strategy B)
 *  - [parseProcLines]        → `/proc` OOM inspection         (Strategy C)
 */
internal object ForegroundAppParser {

    private const val TAG = "ForegroundAppParser"

    // ──────────────────────────────────────────────────────────────────────────
    // Strategy A — dumpsys activity activities
    // Supports Android 8–14+ and common OEM variants (MIUI, OneUI, ColorOS…)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Parses the full output of `dumpsys activity activities`.
     *
     * Tries four regex patterns in priority order; returns the first match.
     * All patterns capture the package name as group 1.
     */
    fun parseActivityDumpsys(output: String): String? {
        if (output.isBlank()) return null

        val patterns = listOf(
            // Android 10+: topResumedActivity
            Regex("""topResumedActivity=ActivityRecord\{[^ ]+ [^ ]+ ([a-zA-Z][a-zA-Z0-9_.]+)/"""),
            // Android 9/10: mResumedActivity (with colon separator)
            Regex("""mResumedActivity:\s*ActivityRecord\{[^ ]+ [^ ]+ ([a-zA-Z][a-zA-Z0-9_.]+)/"""),
            // MIUI / some OEMs: ResumedActivity (without 'm' prefix)
            Regex("""ResumedActivity:\s*ActivityRecord\{[^ ]+ [^ ]+ ([a-zA-Z][a-zA-Z0-9_.]+)/"""),
            // Older or minimal ROM: topActivity=
            Regex("""topActivity=ActivityRecord\{[^ ]+ [^ ]+ ([a-zA-Z][a-zA-Z0-9_.]+)/"""),
            // Generic ComponentInfo fallback inside activities output
            Regex("""ComponentInfo\{([a-zA-Z][a-zA-Z0-9_.]+)/"""),
            // User-slot format: "u0 com.pkg/Activity"
            Regex("""u\d+\s+([a-zA-Z][a-zA-Z0-9_.]+)/"""),
        )

        for (pattern in patterns) {
            val pkg = pattern.find(output)?.groupValues?.get(1)
            if (!pkg.isNullOrBlank() && pkg.contains('.')) {
                Log.d(TAG, "[A] matched pkg=$pkg via ${pattern.pattern.take(40)}")
                return pkg
            }
        }

        Log.d(TAG, "[A] no match in ${output.length}-char output")
        return null
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Strategy B — dumpsys window
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Parses the full output of `dumpsys window` (or `dumpsys window windows`).
     *
     * Checks mCurrentFocus and mFocusedApp. The full output is searched so this
     * works even on ROMs that format output differently.
     */
    fun parseWindowDumpsys(output: String): String? {
        if (output.isBlank()) return null

        val patterns = listOf(
            // Standard: mCurrentFocus=Window{... u0 com.pkg/Activity}
            Regex("""mCurrentFocus=Window\{[^ ]+ [^ ]+ ([a-zA-Z][a-zA-Z0-9_.]+)/"""),
            // Older: mCurrentFocus=Window{... com.pkg/Activity} (no user slot)
            Regex("""mCurrentFocus=Window\{[^ ]+ ([a-zA-Z][a-zA-Z0-9_.]+)/"""),
            // AppWindowToken format
            Regex("""mFocusedApp=AppWindowToken\{[^ ]+ [^ ]+ ([a-zA-Z][a-zA-Z0-9_.]+)/"""),
            // Android 12+ ActivityRecord inside window dump
            Regex("""mFocusedApp.*?ActivityRecord\{[^ ]+ [^ ]+ ([a-zA-Z][a-zA-Z0-9_.]+)/"""),
            // Catch-all user-slot
            Regex("""u\d+\s+([a-zA-Z][a-zA-Z0-9_.]+)/"""),
        )

        for (pattern in patterns) {
            val pkg = pattern.find(output)?.groupValues?.get(1)
            if (!pkg.isNullOrBlank() && pkg.contains('.')) {
                Log.d(TAG, "[B] matched pkg=$pkg via ${pattern.pattern.take(40)}")
                return pkg
            }
        }

        Log.d(TAG, "[B] no match in ${output.length}-char output")
        return null
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Strategy C — /proc OOM score inspection
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Parses the output of the multi-line /proc inspector loop.
     *
     * Each line has the format: `<pid>:<oom_score_adj>:<cmdline>`
     * Produced by the shell command in [RootForegroundAppDataSource].
     *
     * A line is a foreground candidate when:
     *  1. oom_score_adj == "0" (exact)
     *  2. cmdline is non-empty and contains a '.' (valid package-like name)
     *  3. Not a known system/kernel process
     */
    fun parseProcLines(output: String): String? {
        if (output.isBlank()) return null

        val systemProcessPrefixes = listOf(
            "zygote", "zygote64", "system_server", "surfaceflinger",
            "mediaserver", "netd", "logd", "storaged", "healthd", "lmkd",
            "adbd", "vold", "init", "kthreadd"
        )

        for (raw in output.lines()) {
            val line = raw.trim()
            if (line.isBlank()) continue

            // Format: pid:oom_score_adj:cmdline
            val parts = line.split(':', limit = 3)
            if (parts.size < 3) continue

            val pid = parts[0].trim().toIntOrNull() ?: continue
            val oom = parts[1].trim()
            val cmdline = parts[2].trim().trimEnd('\u0000')

            // Only oom_score_adj == 0 means foreground (exact match, not -800 or 100)
            if (oom != "0") continue
            if (cmdline.isBlank() || !cmdline.contains('.')) continue

            // Extract package name: take the first token (before any space/colon)
            val pkg = cmdline.split(' ', ':', '\t').firstOrNull()?.trim() ?: continue
            if (pkg.isBlank()) continue

            // Skip kernel threads and system infrastructure
            if (systemProcessPrefixes.any { pkg.startsWith(it) }) continue

            // Skip our own overlay process
            if (pkg.startsWith("com.gtc.rootbridgekotlin")) continue

            Log.d(TAG, "[C] ✓ pid=$pid oom=$oom pkg=$pkg")
            return pkg
        }

        Log.d(TAG, "[C] no foreground candidate found in ${output.lines().size} lines")
        return null
    }
}
