package com.gtc.rootbridgekotlin.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader

object RootShell {
    suspend fun exec(cmd: String): ShellResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val process = ProcessBuilder("su", "-c", cmd).start()
            val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText).trim()
            val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText).trim()
            val exitCode = process.waitFor()
            val duration = System.currentTimeMillis() - startTime
            ShellResult(exitCode, stdout, stderr, duration)
        } catch (e: Exception) {
            ShellResult(-1, "", e.message ?: "Unknown error", System.currentTimeMillis() - startTime)
        }
    }

    suspend fun execInteractive(cmds: List<String>): ShellResult {
        val fullCmd = cmds.joinToString(" && ")
        return exec(fullCmd)
    }
}
