package com.gtc.rootbridgekotlin.core.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.util.concurrent.TimeUnit

object RootShell {

    private const val TAG = "RootShell"

    /**
     * Execute a single command via `su`.
     *
     * Each call spawns a new su process. For high-frequency operations (like memory
     * scanning) prefer [execBatch] which reuses a persistent shell session.
     */
    suspend fun exec(cmd: String): ShellResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            val os = DataOutputStream(process.outputStream)

            os.writeBytes("$cmd\n")
            os.writeBytes("echo __EXIT__\$?\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()

            val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
            process.waitFor(30, TimeUnit.SECONDS)

            val duration = System.currentTimeMillis() - startTime
            parseOutput(cmd, output, duration)
        } catch (e: Exception) {
            Log.e(TAG, "exec failed: ${e.message}", e)
            ShellResult(-1, "", e.message ?: "Unknown error", System.currentTimeMillis() - startTime)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Persistent shell session
    // ──────────────────────────────────────────────────────────────────────────

    private var persistentProcess: Process? = null
    private var persistentOs: DataOutputStream? = null
    private val sessionMutex = Mutex()

    /**
     * Execute a command through a persistent `su` shell session.
     * More efficient than [exec] because it avoids fork/exec overhead per command.
     *
     * Thread-safe: commands are serialised through a Mutex.
     */
    suspend fun execPersistent(cmd: String): ShellResult = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            ensureSession()
            val startTime = System.currentTimeMillis()
            try {
                val os = persistentOs ?: return@withLock exec(cmd) // fallback

                os.writeBytes("$cmd\n")
                os.writeBytes("echo __EXIT__\$?\n")
                os.flush()

                val process = persistentProcess ?: return@withLock exec(cmd)
                val lines = mutableListOf<String>()
                val reader = process.inputStream.bufferedReader()

                // Read until we see the sentinel
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.startsWith("__EXIT__")) {
                        lines.add(line) // include sentinel for parseOutput
                        break
                    }
                    lines.add(line)
                }

                val duration = System.currentTimeMillis() - startTime
                val raw = lines.joinToString("\n")
                parseOutput(cmd, raw, duration)
            } catch (e: Exception) {
                Log.e(TAG, "execPersistent failed, resetting session: ${e.message}", e)
                resetSession()
                exec(cmd) // one-shot fallback
            }
        }
    }

    suspend fun execInteractive(cmds: List<String>): ShellResult {
        val fullCmd = cmds.joinToString(" && ")
        return exec(fullCmd)
    }

    fun closeSession() {
        try { persistentOs?.close() } catch (_: Exception) {}
        try { persistentProcess?.destroy() } catch (_: Exception) {}
        persistentProcess = null
        persistentOs = null
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun ensureSession() {
        if (persistentProcess?.isAlive == true) return
        try {
            val p = ProcessBuilder("su").redirectErrorStream(true).start()
            persistentProcess = p
            persistentOs = DataOutputStream(p.outputStream)
            Log.d(TAG, "Persistent su session started (hash=${p.hashCode()})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start persistent su session: ${e.message}")
        }
    }

    private fun resetSession() {
        closeSession()
    }

    private fun parseOutput(cmd: String, output: String, duration: Long): ShellResult {
        val lines = output.lines()
        val sentinelIdx = lines.indexOfLast { it.startsWith("__EXIT__") }

        val realExitCode = if (sentinelIdx >= 0) {
            lines[sentinelIdx].removePrefix("__EXIT__").trim().toIntOrNull() ?: 0
        } else {
            0
        }

        val stdout = if (sentinelIdx >= 0) {
            lines.take(sentinelIdx).joinToString("\n").trim()
        } else {
            output.trim()
        }

        Log.d(TAG, "cmd='${cmd.take(100)}' exit=$realExitCode time=${duration}ms out='${stdout.take(120)}'")
        return ShellResult(realExitCode, stdout, "", duration)
    }
}
