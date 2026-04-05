package com.gtc.rootbridgekotlin.core.memory

import com.gtc.rootbridgekotlin.core.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object MemoryEngine {

    // WARNING: A real memory scanner requires a C binary for performance.
    // This pure-shell implementation is a placeholder/mock that simulates scanning time
    // to allow testing the UI/UX architecture of the engine.
    suspend fun scanValue(pid: Int, value: Int, type: DataType): List<ScanResult> = withContext(Dispatchers.IO) {
        delay(2000) // Simulate scan time
        
        val mockResults = mutableListOf<ScanResult>()
        // Return 5 fake addresses for demonstration
        val baseAddress = 0x7FFF0000L
        for (i in 0..4) {
            val bytes = ByteArray(4)
            bytes[0] = (value and 0xFF).toByte()
            bytes[1] = ((value shr 8) and 0xFF).toByte()
            bytes[2] = ((value shr 16) and 0xFF).toByte()
            bytes[3] = ((value shr 24) and 0xFF).toByte()
            
            mockResults.add(ScanResult(baseAddress + (i * 0x1000), bytes, type))
        }
        
        mockResults
    }

    suspend fun filterResults(pid: Int, results: List<ScanResult>, newValue: Int): List<ScanResult> = withContext(Dispatchers.IO) {
        delay(1000)
        // Simulate filtering exactly 1 element
        if (results.isNotEmpty()) {
            val bytes = ByteArray(4)
            bytes[0] = (newValue and 0xFF).toByte()
            bytes[1] = ((newValue shr 8) and 0xFF).toByte()
            bytes[2] = ((newValue shr 16) and 0xFF).toByte()
            bytes[3] = ((newValue shr 24) and 0xFF).toByte()
            
            listOf(ScanResult(results.first().address, bytes, DataType.DWORD))
        } else {
            emptyList()
        }
    }

    suspend fun writeValue(pid: Int, address: Long, value: Int, type: DataType): Boolean = withContext(Dispatchers.IO) {
        val hexAddr = address.toString(10)
        
        val b0 = value and 0xFF
        val b1 = (value shr 8) and 0xFF
        val b2 = (value shr 16) and 0xFF
        val b3 = (value shr 24) and 0xFF
        
        val hexFmt = String.format("\\x%02x\\x%02x\\x%02x\\x%02x", b0, b1, b2, b3)
        // Write to actual memory using dd over su
        val cmd = "printf '$hexFmt' | dd of=/proc/$pid/mem bs=1 seek=$hexAddr conv=notrunc 2>/dev/null"
        val result = RootShell.exec(cmd)
        
        result.exitCode == 0
    }
}
