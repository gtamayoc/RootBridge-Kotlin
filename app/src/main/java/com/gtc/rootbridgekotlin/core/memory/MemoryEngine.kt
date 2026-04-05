package com.gtc.rootbridgekotlin.core.memory

import android.util.Log
import com.gtc.rootbridgekotlin.core.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MemoryEngine {

    private const val TAG = "MemoryEngine"

    /** Maximum addresses returned from a single scan to avoid OOM. */
    const val MAX_RESULTS = 500

    /** Chunk size for reading large memory regions — 8 MB per shell round-trip. */
    private const val CHUNK_SIZE = 8 * 1024 * 1024L // 8 MB

    /** Maximum region size to scan (skip e.g. GPU/graphics buffers > 256 MB). */
    private const val MAX_REGION_SIZE = 256 * 1024 * 1024L // 256 MB

    // ──────────────────────────────────────────────────────────────────────────
    // SCAN
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Scans the readable/writable memory regions of [pid] for [value].
     *
     * Strategy:
     *  1. Read /proc/<pid>/maps to get rw-p regions.
     *  2. For each region, use Python via root to find all little-endian occurrences.
     *     Large regions are chunked in 8 MB slices to avoid Python out-of-memory.
     *  3. Returns up to [MAX_RESULTS] matching [ScanResult]s.
     *
     * Falls back to mock results when pid ≤ 0 (demo/no-root mode).
     *
     * @param onProgress Called after each region with (regionsScanned, regionsTotal).
     */
    suspend fun scanValue(
        pid: Int,
        value: Int,
        type: DataType,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<ScanResult> = withContext(Dispatchers.IO) {
        if (pid <= 0) {
            Log.w(TAG, "scanValue — demo mode (pid=$pid)")
            return@withContext mockResults(value, type)
        }

        val regions = ProcessScanner.getMemoryMaps(pid)
        if (regions.isEmpty()) {
            Log.e(TAG, "scanValue — no readable rw-p regions for pid=$pid")
            return@withContext emptyList()
        }

        val needle = intToLEBytes(value, type.byteSize)
        val scannable = regions.filter {
            (it.endAddress - it.startAddress) in 1..MAX_REGION_SIZE
        }
        val total = scannable.size

        Log.i(TAG, "scanValue — ${scannable.size} scannable regions for pid=$pid value=$value")

        val results = mutableListOf<ScanResult>()
        var scanned = 0

        for (region in scannable) {
            if (results.size >= MAX_RESULTS) break

            val found = scanRegionChunked(pid, region, needle, type)
            results.addAll(found.take(MAX_RESULTS - results.size))

            scanned++
            onProgress(scanned, total)
        }

        Log.i(TAG, "scanValue — done. ${results.size} total matches from $scanned/$total regions")
        results
    }

    /**
     * Re-reads addresses from a previous scan and keeps only those still matching [newValue].
     * Uses the locked PID from the scan session; falls back to demo mode when pid ≤ 0.
     */
    suspend fun filterResults(
        pid: Int,
        results: List<ScanResult>,
        newValue: Int
    ): List<ScanResult> = withContext(Dispatchers.IO) {
        if (pid <= 0) {
            if (results.isEmpty()) return@withContext emptyList()
            val bytes = intToLEBytes(newValue, results.first().dataType.byteSize)
            return@withContext listOf(results.first().copy(currentValue = bytes))
        }

        val needle = intToLEBytes(newValue, results.firstOrNull()?.dataType?.byteSize ?: 4)
        val filtered = mutableListOf<ScanResult>()

        for (sr in results) {
            val current = readBytes(pid, sr.address, sr.dataType.byteSize) ?: continue
            if (current.contentEquals(needle)) {
                filtered.add(sr.copy(currentValue = current))
            }
        }

        Log.i(TAG, "filterResults — ${filtered.size}/${results.size} addresses still match $newValue")
        filtered
    }

    // ──────────────────────────────────────────────────────────────────────────
    // WRITE
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Writes [value] at [address] in process [pid] as root.
     *
     * Primary:  Python `os.open("/proc/<pid>/mem")` + `os.write` — works reliably
     *           when root is granted (no shell-quoting issues with arbitrary byte values).
     * Fallback: `dd` via printf (may fail with non-printable bytes in some shells).
     */
    suspend fun writeValue(
        pid: Int,
        address: Long,
        value: Int,
        type: DataType = DataType.DWORD
    ): Boolean = withContext(Dispatchers.IO) {
        if (pid <= 0) {
            Log.d(TAG, "writeValue — demo mode (pid=$pid), simulating success")
            return@withContext true
        }

        val bytes = intToLEBytes(value, type.byteSize)
        Log.d(TAG, "writeValue — pid=$pid addr=0x${address.toString(16)} value=$value type=$type")

        // Primary: Python write
        val pyResult = RootShell.exec(buildPythonWriteCmd(pid, address, bytes))
        Log.d(TAG, "writeValue [python] — exit=${pyResult.exitCode} out='${pyResult.stdout}'")
        if (pyResult.exitCode == 0) return@withContext true

        // Fallback: dd with printf escape
        Log.w(TAG, "writeValue — python failed (exit ${pyResult.exitCode}), trying dd fallback")
        val bytesEscaped = bytes.joinToString("") { "\\x%02x".format(it.toInt() and 0xFF) }
        val ddCmd = "printf '$bytesEscaped' | dd of=/proc/$pid/mem bs=1 seek=${address} conv=notrunc 2>&1"
        val ddResult = RootShell.exec(ddCmd)
        Log.d(TAG, "writeValue [dd] — exit=${ddResult.exitCode} out='${ddResult.stdout}'")
        ddResult.exitCode == 0
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private — scanning
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Scans a memory region in [CHUNK_SIZE] byte slices.
     * This avoids asking Python to `os.read(fd, 64MB)` in one call, preventing
     * OOM kills inside the su process and reducing latency between progress updates.
     */
    private suspend fun scanRegionChunked(
        pid: Int,
        region: MemoryRegion,
        needle: ByteArray,
        type: DataType
    ): List<ScanResult> {
        val regionSize = region.endAddress - region.startAddress
        val found = mutableListOf<ScanResult>()
        var offset = 0L

        while (offset < regionSize && found.size < MAX_RESULTS) {
            val chunkSize = minOf(CHUNK_SIZE, regionSize - offset)
            val chunkStart = region.startAddress + offset

            val chunkResults = scanChunk(pid, chunkStart, chunkSize, needle, type)
            found.addAll(chunkResults.take(MAX_RESULTS - found.size))
            offset += chunkSize
        }

        return found
    }

    /**
     * Scans exactly [size] bytes starting at [start] inside /proc/<pid>/mem.
     * Returns a list of absolute addresses where [needle] was found.
     */
    private suspend fun scanChunk(
        pid: Int,
        start: Long,
        size: Long,
        needle: ByteArray,
        type: DataType
    ): List<ScanResult> {
        val script = buildPythonScanScript(pid, start, size, needle)
        val result = RootShell.exec(script)

        if (result.exitCode != 0) {
            val out = result.stdout
            when {
                out.contains("No such process") ->
                    Log.w(TAG, "  chunk 0x${start.toString(16)} — process died")
                out.contains("ermission") ->
                    Log.w(TAG, "  chunk 0x${start.toString(16)} — permission denied")
                out.isNotBlank() ->
                    Log.d(TAG, "  chunk 0x${start.toString(16)} — err: ${out.take(120)}")
            }
            return emptyList()
        }

        val found = mutableListOf<ScanResult>()
        for (line in result.stdout.lines()) {
            val addr = line.trim().toLongOrNull(16) ?: continue
            found.add(ScanResult(addr, needle.copyOf(), type))
            if (found.size >= MAX_RESULTS) break
        }
        return found
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private — read-back for filter
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Reads [byteCount] bytes from /proc/<pid>/mem at [address] via Python + xxd.
     * Returns null on any failure.
     */
    private suspend fun readBytes(pid: Int, address: Long, byteCount: Int): ByteArray? {
        // Inline Python: read raw bytes, pipe through xxd -p for safe text delivery
        val script = buildString {
            append("python3 -c \"\n")
            append("import os, sys\n")
            append("fd = os.open('/proc/$pid/mem', os.O_RDONLY)\n")
            append("os.lseek(fd, $address, os.SEEK_SET)\n")
            append("data = os.read(fd, $byteCount)\n")
            append("os.close(fd)\n")
            append("sys.stdout.buffer.write(data)\n")
            append("\" | xxd -p | tr -d '\\n'")
        }

        val result = RootShell.exec(script)
        if (result.exitCode != 0 || result.stdout.isBlank()) return null

        return try {
            hexStringToByteArray(result.stdout.trim())
        } catch (e: Exception) {
            Log.w(TAG, "readBytes — hex parse failed: ${e.message}")
            null
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Python script builders
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Generates a Python `-c` script that:
     *  1. Opens /proc/<pid>/mem read-only.
     *  2. Seeks to [start] and reads [size] bytes.
     *  3. Searches for all non-overlapping occurrences of [needle].
     *  4. Prints each match address as a hex string (no '0x' prefix).
     */
    private fun buildPythonScanScript(
        pid: Int,
        start: Long,
        size: Long,
        needle: ByteArray
    ): String {
        val needleHex = needle.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return buildString {
            append("python3 -c \"\n")
            append("import os\n")
            append("needle = bytes.fromhex('$needleHex')\n")
            append("fd = os.open('/proc/$pid/mem', os.O_RDONLY)\n")
            append("try:\n")
            append("  os.lseek(fd, $start, os.SEEK_SET)\n")
            append("  data = os.read(fd, $size)\n")
            append("  idx = 0\n")
            append("  count = 0\n")
            append("  nl = len(needle)\n")
            append("  while count < $MAX_RESULTS:\n")
            append("    pos = data.find(needle, idx)\n")
            append("    if pos == -1: break\n")
            append("    print(format($start + pos, 'x'))\n")
            append("    idx = pos + nl\n")
            append("    count += 1\n")
            append("except Exception as e:\n")
            append("  import sys; print(str(e), file=sys.stderr)\n")
            append("finally:\n")
            append("  os.close(fd)\n")
            append("\"")
        }
    }

    /**
     * Generates a Python `-c` script that writes [bytes] into /proc/<pid>/mem at [address].
     */
    private fun buildPythonWriteCmd(pid: Int, address: Long, bytes: ByteArray): String {
        val bytesHex = bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return buildString {
            append("python3 -c \"\n")
            append("import os\n")
            append("data = bytes.fromhex('$bytesHex')\n")
            append("fd = os.open('/proc/$pid/mem', os.O_WRONLY)\n")
            append("os.lseek(fd, $address, os.SEEK_SET)\n")
            append("os.write(fd, data)\n")
            append("os.close(fd)\n")
            append("print('ok')\n")
            append("\"")
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────────────────────────────────

    private fun intToLEBytes(value: Int, byteCount: Int): ByteArray {
        val b = ByteArray(byteCount)
        for (i in 0 until byteCount) b[i] = ((value shr (i * 8)) and 0xFF).toByte()
        return b
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val clean = hex.replace("\\s".toRegex(), "")
        val data = ByteArray(clean.length / 2)
        for (i in data.indices) {
            data[i] = ((Character.digit(clean[i * 2], 16) shl 4) +
                    Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
        return data
    }

    /** Deterministic mock results for demo/no-root mode. */
    private fun mockResults(value: Int, type: DataType): List<ScanResult> {
        val base = 0x7FFF0000L
        val bytes = intToLEBytes(value, type.byteSize)
        return (0..4).map { i -> ScanResult(base + i * 0x1000L, bytes.copyOf(), type) }
    }
}
