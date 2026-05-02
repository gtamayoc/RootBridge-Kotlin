package com.gtc.rootbridgekotlin.core.memory

import android.util.Log
import com.gtc.rootbridgekotlin.core.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

object MemoryEngine {

    private const val TAG = "MemoryEngine"

    /** Binary to deploy - compiled from scanner.cpp */
    var nativeLibDir: String = ""

    private var binaryReady = false
    private val binaryPath  = "/data/local/tmp/mem_scanner"

    // ────────────────────────────────────────────────────────────────────────
    // BINARY SETUP
    // ────────────────────────────────────────────────────────────────────────

    private suspend fun prepareBinary(): Boolean {
        if (binaryReady) return true

        val source  = "$nativeLibDir/libscanner.so"
        val copyCmd = "cp '$source' $binaryPath || cat '$source' > $binaryPath; chmod +x $binaryPath"
        RootShell.exec(copyCmd)

        val check = RootShell.exec("ls -l $binaryPath 2>/dev/null")
        binaryReady = check.stdout.contains("mem_scanner")
        if (!binaryReady) Log.e(TAG, "Binary not ready. libscanner.so missing at $source")
        return binaryReady
    }

    // ────────────────────────────────────────────────────────────────────────
    // SCAN  — Full first scan; state lives in C++ session file
    // Returns the TOTAL count of matches (not limited).
    // Call fetchResults() afterwards to get a displayable subset.
    // ────────────────────────────────────────────────────────────────────────

    suspend fun scanValue(
        pid: Int,
        value: Int,
        type: DataType,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        onProgress(50, 100)

        if (pid <= 0) {
            // Demo mode: fabricate a tiny "session" showing mock addresses
            onProgress(100, 100)
            return@withContext 5 // Mock total
        }

        if (!prepareBinary()) {
            onProgress(100, 100)
            return@withContext -1 // Signal error
        }

        val needle    = intToLEBytes(value, type.byteSize)
        val needleHex = needle.toHexString()

        // Clear existing session before scan
        RootShell.execPersistent("$binaryPath clear_session 2>/dev/null")

        val res = RootShell.execPersistent("$binaryPath scan $pid $needleHex")
        onProgress(100, 100)

        return@withContext parseTotalFound(res.stdout, pid)
    }

    // ────────────────────────────────────────────────────────────────────────
    // FETCH RESULTS  — Retrieve a displayable page from C++ session file
    // ────────────────────────────────────────────────────────────────────────

    suspend fun fetchResults(
        pid: Int,
        type: DataType,
        limit: Int = 500
    ): List<ScanResult> = withContext(Dispatchers.IO) {
        if (pid <= 0) return@withContext mockResults(0, type)

        if (!prepareBinary()) return@withContext emptyList()

        val res = RootShell.execPersistent("$binaryPath print_results $limit")
        parseResultLines(res.stdout, type)
    }

    // ────────────────────────────────────────────────────────────────────────
    // FILTER EXACT  — Keep only addresses matching a specific value now
    // ────────────────────────────────────────────────────────────────────────

    suspend fun filterExact(
        pid: Int,
        value: Int,
        type: DataType
    ): Int = withContext(Dispatchers.IO) {
        if (pid <= 0) return@withContext 1

        if (!prepareBinary()) return@withContext -1

        val needle    = intToLEBytes(value, type.byteSize)
        val needleHex = needle.toHexString()

        val res = RootShell.execPersistent("$binaryPath filter_exact $pid $needleHex")
        parseTotalFound(res.stdout, pid)
    }

    // ────────────────────────────────────────────────────────────────────────
    // FILTER CHANGED  — Keep only addresses whose value CHANGED since scan
    // ────────────────────────────────────────────────────────────────────────

    suspend fun filterChanged(pid: Int): Int = withContext(Dispatchers.IO) {
        if (pid <= 0) return@withContext 2

        if (!prepareBinary()) return@withContext -1

        val res = RootShell.execPersistent("$binaryPath filter_changed $pid")
        parseTotalFound(res.stdout, pid)
    }

    // ────────────────────────────────────────────────────────────────────────
    // FILTER UNCHANGED  — Keep only addresses whose value STAYED the same
    // ────────────────────────────────────────────────────────────────────────

    suspend fun filterUnchanged(pid: Int): Int = withContext(Dispatchers.IO) {
        if (pid <= 0) return@withContext 3

        if (!prepareBinary()) return@withContext -1

        val res = RootShell.execPersistent("$binaryPath filter_unchanged $pid")
        parseTotalFound(res.stdout, pid)
    }

    // ────────────────────────────────────────────────────────────────────────
    // WRITE
    // ────────────────────────────────────────────────────────────────────────

    suspend fun writeValue(
        pid: Int,
        address: Long,
        value: Int,
        type: DataType = DataType.DWORD
    ): Boolean = withContext(Dispatchers.IO) {
        if (pid <= 0) return@withContext true
        if (!prepareBinary()) return@withContext false

        val bytes        = intToLEBytes(value, type.byteSize)
        val hexValue     = bytes.toHexString()

        val cmd = "$binaryPath write $pid $address $hexValue 2>&1"
        val res = RootShell.exec(cmd)
        
        return@withContext res.stdout.contains("WRITE_SUCCESS")
    }

    // ────────────────────────────────────────────────────────────────────────
    // WRITE ALL (Bulk Write via NDK)
    // ────────────────────────────────────────────────────────────────────────

    suspend fun writeAllBytes(pid: Int, bytes: ByteArray): Int = withContext(Dispatchers.IO) {
        if (!prepareBinary()) return@withContext 0
        if (pid <= 0) return@withContext 0

        val hexValue = bytes.toHexString()
        val cmd = "$binaryPath write_all $pid $hexValue 2>&1"
        Log.i(TAG, "writeAllBytes -> EXEC: $cmd")

        val result = RootShell.exec(cmd)
        Log.i(TAG, "writeAllBytes -> STDOUT: \n${result.stdout}")

        // Expected output: "WRITE_SUCCESS:<count>"
        val line = result.stdout.lines().firstOrNull { it.startsWith("WRITE_SUCCESS:") }
        return@withContext line?.removePrefix("WRITE_SUCCESS:")?.trim()?.toIntOrNull() ?: 0
    }

    // ────────────────────────────────────────────────────────────────────────
    // CLEAR SESSION
    // ────────────────────────────────────────────────────────────────────────

    suspend fun clearSession() = withContext(Dispatchers.IO) {
        if (binaryReady) {
            RootShell.exec("$binaryPath clear_session 2>/dev/null")
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // REFRESH  — Re-read live values for a small visible list (UI refresh)
    // ────────────────────────────────────────────────────────────────────────

    suspend fun refreshAddresses(
        pid: Int,
        results: List<ScanResult>
    ): List<ScanResult> = withContext(Dispatchers.IO) {
        if (pid <= 0) return@withContext results
        
        results.map { sr ->
            async {
                val liveBytes = readBytes(pid, sr.address, sr.dataType.byteSize)
                if (liveBytes != null) sr.copy(currentValue = liveBytes) else sr
            }
        }.awaitAll()
    }

    // ────────────────────────────────────────────────────────────────────────
    // Internal parsing
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Parses the "TOTAL_FOUND:N" line from scanner output. Returns -1 on failure.
     */
    private fun parseTotalFound(stdout: String, pid: Int): Int {
        val line = stdout.lines().firstOrNull { it.startsWith("TOTAL_FOUND:") }
            ?: run {
                Log.e(TAG, "parseTotalFound — no TOTAL_FOUND line. pid=$pid stdout=[$stdout]")
                return -1
            }
        return line.removePrefix("TOTAL_FOUND:").trim().toIntOrNull() ?: -1
    }

    /**
     * Parses `print_results` output lines of the form:
     *   address,hh,hh,hh,hh
     *   ...
     *   TOTAL_FOUND:N
     */
    private fun parseResultLines(stdout: String, type: DataType): List<ScanResult> {
        val results = mutableListOf<ScanResult>()
        for (line in stdout.lines()) {
            if (line.isBlank() || line.startsWith("TOTAL_FOUND")) continue
            try {
                val parts = line.split(",")
                if (parts.size < 2) continue
                val addr      = parts[0].trim().toULong().toLong()
                val hexParts  = parts.drop(1).map { it.trim() }
                val byteArray = ByteArray(hexParts.size) {
                    hexParts[it].toInt(16).toByte()
                }
                results.add(ScanResult(addr, byteArray, type))
            } catch (e: Exception) {
                Log.e(TAG, "parseResultLines — bad line: [$line]", e)
            }
        }
        return results
    }

    // ────────────────────────────────────────────────────────────────────────
    // Utility
    // ────────────────────────────────────────────────────────────────────────

    private suspend fun readBytes(pid: Int, address: Long, byteCount: Int): ByteArray? {
        val cmd = "$binaryPath read $pid $address $byteCount 2>/dev/null"
        val result = RootShell.execPersistent(cmd)
        if (result.exitCode == 0 && result.stdout.isNotBlank()) {
            return try {
                hexStringToByteArray(result.stdout.trim().lowercase())
            } catch (e: Exception) { null }
        }
        return null
    }

    fun intToLEBytes(value: Int, byteCount: Int): ByteArray {
        val b = ByteArray(byteCount)
        for (i in 0 until byteCount) b[i] = ((value shr (i * 8)) and 0xFF).toByte()
        return b
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val data = ByteArray(hex.length / 2)
        for (i in data.indices)
            data[i] = ((Character.digit(hex[i * 2], 16) shl 4) +
                    Character.digit(hex[i * 2 + 1], 16)).toByte()
        return data
    }



    private fun mockResults(value: Int, type: DataType): List<ScanResult> {
        val base  = 0x7FFF0000L
        val bytes = intToLEBytes(value, type.byteSize)
        return (0..4).map { i -> ScanResult(base + i * 0x1000L, bytes.copyOf(), type) }
    }
}
