package com.gtc.rootbridgekotlin.core.memory

import com.gtc.rootbridgekotlin.core.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ProcessScanner {

    suspend fun getForegroundProcess(): ProcessInfo? = withContext(Dispatchers.IO) {
        val dumpResult = RootShell.exec("dumpsys activity activities | grep mResumedActivity")
        if (dumpResult.exitCode != 0 || dumpResult.stdout.isBlank()) return@withContext null
        
        val line = dumpResult.stdout.lines().firstOrNull() ?: return@withContext null
        val packageMatch = Regex(" u0 ([a-zA-Z0-9_.]+)/").find(line)
        val packageName = packageMatch?.groupValues?.get(1) ?: return@withContext null

        val pidResult = RootShell.exec("pidof $packageName")
        if (pidResult.exitCode != 0 || pidResult.stdout.isBlank()) return@withContext null
        
        val pids = pidResult.stdout.trim().split(" ")
        val pid = pids.firstOrNull()?.toIntOrNull() ?: return@withContext null
        
        ProcessInfo(pid, packageName, packageName, true)
    }

    suspend fun getMemoryMaps(pid: Int): List<MemoryRegion> = withContext(Dispatchers.IO) {
        val result = RootShell.exec("cat /proc/$pid/maps")
        if (result.exitCode != 0) return@withContext emptyList()
        
        val regions = mutableListOf<MemoryRegion>()
        for (line in result.stdout.lines()) {
            if (line.isBlank() || !line.contains("rw-p")) continue // Only readable/writable private regions
            
            val parts = line.split(Regex("\\s+"))
            if (parts.size >= 2) {
                val addressRange = parts[0].split("-")
                if (addressRange.size == 2) {
                    try {
                        val start = addressRange[0].toLong(16)
                        val end = addressRange[1].toLong(16)
                        val perms = parts[1]
                        val label = if (parts.size >= 6) parts.subList(5, parts.size).joinToString(" ") else "[anon]"
                        regions.add(MemoryRegion(start, end, perms, label))
                    } catch (e: Exception) {
                        // Ignore parse errors for specific lines
                    }
                }
            }
        }
        regions
    }
}
