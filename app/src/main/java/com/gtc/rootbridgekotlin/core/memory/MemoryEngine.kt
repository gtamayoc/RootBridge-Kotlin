package com.gtc.rootbridgekotlin.core.memory

import android.util.Log
import com.gtc.rootbridgekotlin.core.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MemoryEngine {

    private const val TAG = "MemoryEngine"

    /** Maximum addresses returned from a single scan to avoid OOM. */
    const val MAX_RESULTS = 500

    // Path to the native libs for the application
    var nativeLibDir: String = ""

    private var binaryReady = false

    private suspend fun prepareBinary(): String? {
        val tmpDest = "/data/local/tmp/mem_scanner"
        if (!binaryReady) {
            val source = "$nativeLibDir/libscanner.so"
            // We use busybox cp or standard cp. Also fallback to cat if cp fails on some weird roms.
            val copyCmd = "cp $source $tmpDest || cat $source > $tmpDest; chmod +x $tmpDest"
            val res = RootShell.exec(copyCmd)
            
            // Validate if the binary is actually there and executable
            val checkCmd = "ls -l $tmpDest"
            val checkRes = RootShell.exec(checkCmd)
            if (checkRes.stdout.contains("mem_scanner")) {
                binaryReady = true
            } else {
                Log.e(TAG, "Failed to prepare binary. Code=${res.exitCode}, out=${res.stdout}. Check=${checkRes.stdout}")
                return null
            }
        }
        return tmpDest
    }

    // ────────────────────────────────────────────────────────────────────────────
    // SESSION STATE CACHE
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * Stores the current active scan results per PID.
     */
    private val activeSessions = java.util.concurrent.ConcurrentHashMap<Int, List<ScanResult>>()

    fun getSession(pid: Int): List<ScanResult>? = activeSessions[pid]

    fun setSession(pid: Int, results: List<ScanResult>) {
        activeSessions[pid] = results.take(MAX_RESULTS * 2) 
    }

    fun clearSession(pid: Int) {
        activeSessions.remove(pid)
    }

    /**
     * Reads the REAL-TIME values of the provided [ScanResult] addresses.
     */
    suspend fun refreshAddresses(
        pid: Int,
        results: List<ScanResult>
    ): List<ScanResult> = withContext(Dispatchers.IO) {
        if (pid <= 0) return@withContext results
        
        // This can be optimized using NDK batch-read later. 
        // For now, we reuse NDK `filter` logic but without actual filtering.
        // Or keep the old logic for refresh. Here we just return results if refresh logic not updated to NDK yet,
        // or we simply fallback to `dd`. Let's use the simplest loop for now:
        val refreshed = mutableListOf<ScanResult>()
        for (sr in results) {
            val liveBytes = readBytes(pid, sr.address, sr.dataType.byteSize)
            if (liveBytes != null) {
                refreshed.add(sr.copy(currentValue = liveBytes))
            } else {
                refreshed.add(sr)
            }
        }
        refreshed
    }


    // ────────────────────────────────────────────────────────────────────────────
    // SCAN NDK
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * Scans the rw-p memory regions of [pid] for [value].
     *
     * Strategy NDK:
     *  Execute the compiled C++ binary (libscanner.so) via root.
     *  It sweeps [anon] and [heap] exclusively via direct `pread64` minimizing CPU.
     */
    suspend fun scanValue(
        pid: Int,
        value: Int,
        type: DataType,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<ScanResult> = withContext(Dispatchers.IO) {
        onProgress(50, 100) // Fast progress for NDK
        if (pid <= 0) {
            onProgress(100, 100)
            return@withContext mockResults(value, type)
        }

        val needle = intToLEBytes(value, type.byteSize)
        val needleHex = needle.toHexString()
        
        val binaryPath = prepareBinary() ?: return@withContext emptyList()
        
        val cmd = "$binaryPath scan $pid $needleHex"
        val result = RootShell.execPersistent(cmd)
        
        val results = mutableListOf<ScanResult>()
        if (result.exitCode == 0 && result.stdout.isNotBlank()) {
            val lines = result.stdout.lines()
            for (line in lines) {
                if (line.isBlank()) continue
                try {
                    val addr = line.toULong(10).toLong()
                    results.add(ScanResult(addr, needle.copyOf(), type))
                    if (results.size >= MAX_RESULTS) break
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing address line: $line", e)
                }
            }
        } else {
            Log.e(TAG, "NDK Scan failed. Output: ${result.stdout}")
        }

        onProgress(100, 100)
        Log.i(TAG, "scanValue NDK — done. ${results.size} total matches")
        results
    }

    /**
     * Re-reads addresses using NDK filter binary.
     */
    suspend fun filterResults(
        pid: Int,
        results: List<ScanResult>,
        newValue: Int
    ): List<ScanResult> = withContext(Dispatchers.IO) {
        if (pid <= 0) {
            val bytes = intToLEBytes(newValue, results.firstOrNull()?.dataType?.byteSize ?: 4)
            return@withContext listOf(results.first().copy(currentValue = bytes))
        }
        
        val dataType = results.firstOrNull()?.dataType ?: DataType.DWORD
        val needle = intToLEBytes(newValue, dataType.byteSize)
        val needleHex = needle.toHexString()
        
        val addresses = results.joinToString("\n") { it.address.toULong().toString() }
        
        val binaryPath = prepareBinary() ?: return@withContext emptyList()
        val cmd = "echo '$addresses' | $binaryPath filter $pid $needleHex"
        
        val result = RootShell.execPersistent(cmd)
        
        val filtered = mutableListOf<ScanResult>()
        
        if (result.exitCode == 0 && result.stdout.isNotBlank()) {
            val lines = result.stdout.lines()
            for (line in lines) {
                if (line.isBlank()) continue
                try {
                    val addr = line.toULong(10).toLong()
                    filtered.add(ScanResult(addr, needle.copyOf(), dataType))
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing address: $line", e)
                }
            }
        }
        
        Log.i(TAG, "filterResults NDK — ${filtered.size}/${results.size} addresses still match $newValue")
        filtered
    }

    // ────────────────────────────────────────────────────────────────────────────
    // WRITE
    // ────────────────────────────────────────────────────────────────────────────

    suspend fun writeValue(
        pid: Int,
        address: Long,
        value: Int,
        type: DataType = DataType.DWORD
    ): Boolean = withContext(Dispatchers.IO) {
        if (pid <= 0) return@withContext true

        val bytes = intToLEBytes(value, type.byteSize)
        val bytesEscaped = bytes.joinToString("") { "\\x%02x".format(it.toInt() and 0xFF) }

        val ddCmd = "printf '$bytesEscaped' | dd of=/proc/$pid/mem bs=${type.byteSize} seek=$address oflag=seek_bytes conv=notrunc 2>&1"
        val ddResult = RootShell.exec(ddCmd)
        if (ddResult.exitCode == 0) return@withContext true

        val bbCmd = "printf '$bytesEscaped' | busybox dd of=/proc/$pid/mem bs=${type.byteSize} seek=$address oflag=seek_bytes conv=notrunc 2>&1"
        RootShell.exec(bbCmd).exitCode == 0
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Utility
    // ────────────────────────────────────────────────────────────────────────────

    private suspend fun readBytes(pid: Int, address: Long, byteCount: Int): ByteArray? {
        val strategy = "xxd -p | tr -d '\\n'"
        val cmd = "dd if=/proc/$pid/mem iflag=skip_bytes bs=$byteCount skip=$address count=1 2>/dev/null | $strategy"
        val result = RootShell.execPersistent(cmd)
        if (result.exitCode == 0 && result.stdout.isNotBlank()) {
            try {
                return hexStringToByteArray(result.stdout.trim().lowercase().take(byteCount * 2))
            } catch (e: Exception) {}
        }
        return null
    }

    private fun intToLEBytes(value: Int, byteCount: Int): ByteArray {
        val b = ByteArray(byteCount)
        for (i in 0 until byteCount) b[i] = ((value shr (i * 8)) and 0xFF).toByte()
        return b
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val clean = hex.replace("\\s".toRegex(), "")
        val data = ByteArray(clean.length / 2)
        for (i in data.indices) {
            data[i] = ((Character.digit(clean[i * 2], 16) shl 4) +
                    Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
        return data
    }

    private fun mockResults(value: Int, type: DataType): List<ScanResult> {
        val base = 0x7FFF0000L
        val bytes = intToLEBytes(value, type.byteSize)
        return (0..4).map { i -> ScanResult(base + i * 0x1000L, bytes.copyOf(), type) }
    }
}
