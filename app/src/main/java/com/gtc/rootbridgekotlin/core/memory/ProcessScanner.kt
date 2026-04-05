package com.gtc.rootbridgekotlin.core.memory

import android.util.Log
import com.gtc.rootbridgekotlin.core.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ProcessScanner {

    private const val TAG = "ProcessScanner"

    /**
     * Detects the current foreground app using multiple fallback strategies.
     * Returns the first strategy that successfully resolves both a package name AND a PID.
     *
     * Compatible with Android 9–14+.
     */
    suspend fun getForegroundProcess(): ProcessInfo? = withContext(Dispatchers.IO) {
        // Try each strategy, return the first that works
        tryWithCmdActivityFocus()
            ?: tryWithDumpsysWindows()
            ?: tryWithDumpsysActivity()
            ?: tryWithProcNet()
    }

    /**
     * Fetches a list of all currently running applications that look like user packages.
     * Excludes kernel threads, zygotes, and system background services.
     */
    suspend fun getRunningApplications(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        // List numeric proc dirs and print pid + cmdline
        val script = "for d in /proc/[0-9]*/; do p=\${d%/}; p=\${p##*/}; c=\$(cat \$d/cmdline 2>/dev/null | tr '\\0' ' '); [ -n \"\$c\" ] && echo \"\$p \$c\"; done"
        val result = RootShell.exec(script)
        if (result.exitCode != 0 || result.stdout.isBlank()) return@withContext emptyList()

        val systemProcessPrefixes = listOf(
            "zygote", "system_server", "surfaceflinger", "mediaserver", "netd", "logd",
            "storaged", "healthd", "lmkd", "adbd", "vold", "init", "kthreadd",
            "android.hardware.", "vendor.", "com.android.bluetooth", "com.android.phone",
            "com.android.systemui", "com.android.nfc"
        )

        val apps = mutableListOf<ProcessInfo>()
        for (line in result.stdout.lines()) {
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size < 2) continue
            
            val pid = parts[0].toIntOrNull() ?: continue
            val pkgName = parts[1].trim().split(" ").firstOrNull() ?: continue
            
            if (!pkgName.contains('.')) continue
            
            val isSystem = systemProcessPrefixes.any { pkgName.startsWith(it) }
            val isSelf = pkgName.startsWith("com.gtc.rootbridgekotlin")
            if (isSystem || isSelf) continue

            apps.add(ProcessInfo(pid, pkgName, pkgName, false))
        }

        // Distinct by package name (keep highest PID if there are multiple processes, or just first).
        apps.distinctBy { it.packageName }.sortedBy { it.packageName }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Strategy 1: `cmd activity get-current-focus` (Android 12+)
    // Output variant A: "Currently focused window: Window{hash u0 com.pkg/com.pkg.Activity}"
    // Output variant B: "mCurrentFocus=Window{hash u0 com.pkg/com.pkg.Activity}"
    // ──────────────────────────────────────────────────────────────────────────
    private suspend fun tryWithCmdActivityFocus(): ProcessInfo? {
        val result = RootShell.exec("cmd activity get-current-focus")
        Log.d(TAG, "[strat1] exit=${result.exitCode} out='${result.stdout}'")
        if (result.exitCode != 0 || result.stdout.isBlank()) return null

        val pkg = extractPackage(result.stdout) ?: return null
        return resolvePid(pkg, "strat1")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Strategy 2: `dumpsys window` grep
    // ──────────────────────────────────────────────────────────────────────────
    private suspend fun tryWithDumpsysWindows(): ProcessInfo? {
        val result = RootShell.exec(
            "dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp' | head -3"
        )
        Log.d(TAG, "[strat2] exit=${result.exitCode} out='${result.stdout}'")
        if (result.exitCode != 0 || result.stdout.isBlank()) return null

        val pkg = extractPackage(result.stdout) ?: return null
        return resolvePid(pkg, "strat2")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Strategy 3: `dumpsys activity activities` (Android <12 fallback)
    // ──────────────────────────────────────────────────────────────────────────
    private suspend fun tryWithDumpsysActivity(): ProcessInfo? {
        val result = RootShell.exec(
            "dumpsys activity activities | grep -E 'mResumedActivity|topActivity' | head -3"
        )
        Log.d(TAG, "[strat3] exit=${result.exitCode} out='${result.stdout}'")
        if (result.exitCode != 0 || result.stdout.isBlank()) return null

        val pkg = extractPackage(result.stdout) ?: return null
        return resolvePid(pkg, "strat3")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Strategy 4: Scan /proc for foreground process via OOM score
    // ──────────────────────────────────────────────────────────────────────────
    private suspend fun tryWithProcNet(): ProcessInfo? {
        // List all processes with oom_adj = -17 or low oom_score_adj (foreground)
        val result = RootShell.exec(
            "for d in /proc/[0-9]*/; do p=\${d%/}; p=\${p##*/}; s=\$(cat \$d/oom_score_adj 2>/dev/null); [ \"\$s\" = \"0\" ] && echo \$p \$(cat \$d/cmdline 2>/dev/null | tr '\\0' ' '); done | head -5"
        )
        Log.d(TAG, "[strat4] exit=${result.exitCode} out='${result.stdout}'")
        if (result.exitCode != 0 || result.stdout.isBlank()) return null

        for (line in result.stdout.lines()) {
            val parts = line.trim().split(Regex("\\s+"), 2)
            if (parts.size < 2) continue
            val pid = parts[0].toIntOrNull() ?: continue
            val cmdline = parts[1].trim().trimEnd('\u0000')
            if (cmdline.isBlank() || !cmdline.contains(".")) continue

            // Skip kernel threads and system processes
            val pkg = cmdline.split(" ").firstOrNull()?.trimEnd() ?: continue
            if (pkg.startsWith("zygote") || pkg == "system_server") continue

            Log.i(TAG, "[strat4] ✓ $pkg PID=$pid")
            return ProcessInfo(pid, pkg, pkg, true)
        }
        return null
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PID resolution
    // ──────────────────────────────────────────────────────────────────────────

    private suspend fun resolvePid(packageName: String, source: String): ProcessInfo? {
        // Method 1: pidof
        var pidResult = RootShell.exec("pidof $packageName 2>/dev/null")
        Log.d(TAG, "[$source] pidof '$packageName' exit=${pidResult.exitCode} out='${pidResult.stdout}'")

        // Method 2: ps with awk
        if (pidResult.exitCode != 0 || pidResult.stdout.isBlank()) {
            pidResult = RootShell.exec(
                "ps -A -o PID,NAME 2>/dev/null | grep -w '$packageName' | awk '{print \$1}' | head -1"
            )
            Log.d(TAG, "[$source] ps fallback exit=${pidResult.exitCode} out='${pidResult.stdout}'")
        }

        // Method 3: Scan /proc/<pid>/cmdline directly
        if (pidResult.exitCode != 0 || pidResult.stdout.isBlank()) {
            pidResult = RootShell.exec(
                "grep -rl '$packageName' /proc/[0-9]*/cmdline 2>/dev/null | head -1 | grep -o '[0-9]*'"
            )
            Log.d(TAG, "[$source] /proc cmdline grep exit=${pidResult.exitCode} out='${pidResult.stdout}'")
        }

        val pid = pidResult.stdout.trim().split(Regex("\\s+")).firstOrNull()?.toIntOrNull()
        if (pid == null || pid <= 0) {
            Log.w(TAG, "[$source] Could not resolve PID for '$packageName'")
            return null
        }

        Log.i(TAG, "[$source] ✓ $packageName PID=$pid")
        return ProcessInfo(pid, packageName, packageName, true)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Memory maps
    // ──────────────────────────────────────────────────────────────────────────

    suspend fun getMemoryMaps(pid: Int): List<MemoryRegion> = withContext(Dispatchers.IO) {
        if (pid <= 0) return@withContext emptyList()

        val result = RootShell.exec("cat /proc/$pid/maps 2>/dev/null")
        if (result.exitCode != 0 || result.stdout.isBlank()) {
            Log.e(TAG, "getMemoryMaps — failed for pid=$pid exit=${result.exitCode}")
            return@withContext emptyList()
        }

        val regions = mutableListOf<MemoryRegion>()
        for (line in result.stdout.lines()) {
            if (line.isBlank()) continue
            // Only include regions with rw-p (read-write private) — the interesting ones
            if (!line.contains("rw-p")) continue

            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) continue

            val addressRange = parts[0].split("-")
            if (addressRange.size != 2) continue

            try {
                val start = addressRange[0].toLong(16)
                val end = addressRange[1].toLong(16)
                if (end <= start) continue

                val perms = parts.getOrNull(1) ?: continue
                val label = if (parts.size >= 6) {
                    parts.subList(5, parts.size).joinToString(" ")
                } else {
                    "[anon]"
                }
                regions.add(MemoryRegion(start, end, perms, label))
            } catch (_: Exception) {
                // Skip malformed lines silently
            }
        }

        Log.d(TAG, "getMemoryMaps — pid=$pid found ${regions.size} rw-p regions")
        regions
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Package extraction helper
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Extracts a package name from dumpsys/activity command output.
     * Handles formats like:
     *  - "u0 com.example.app/com.example.app.MainActivity"
     *  - "u0 com.example.app}"
     *  - "topActivity=ComponentInfo{com.example.app/...}"
     */
    private fun extractPackage(output: String): String? {
        // Pattern 1: user-slot followed by package/class
        Regex("u\\d+\\s+([a-zA-Z][a-zA-Z0-9_.]+)/").find(output)
            ?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let { return it }

        // Pattern 2: ComponentInfo{package/class}
        Regex("ComponentInfo\\{([a-zA-Z][a-zA-Z0-9_.]+)/").find(output)
            ?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let { return it }

        // Pattern 3: "package=" keyword
        Regex("package=([a-zA-Z][a-zA-Z0-9_.]+)").find(output)
            ?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let { return it }

        return null
    }
}
