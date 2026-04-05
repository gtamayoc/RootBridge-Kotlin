package com.gtc.rootbridgekotlin.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RootChecker {
    suspend fun checkRootAccess(): RootAccessState = withContext(Dispatchers.IO) {
        val paths = arrayOf(
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        val binaryExists = paths.any { File(it).exists() } || isSuInPath()

        if (!binaryExists) {
            return@withContext RootAccessState.MissingBinary
        }

        val result = RootShell.exec("id")
        if (result.exitCode == 0 && result.stdout.contains("uid=0(root)")) {
            RootAccessState.Authorized
        } else {
            RootAccessState.Denied(reason = "Permiso denegado por Magisk/KernelSU. ${result.stderr}".trim())
        }
    }

    private fun isSuInPath(): Boolean {
        val envPath = System.getenv("PATH") ?: return false
        val paths = envPath.split(":")
        return paths.any { safelyCheckExists(File(it, "su")) }
    }
    
    private fun safelyCheckExists(file: File): Boolean {
        return try {
            file.exists()
        } catch (e: SecurityException) {
            false
        }
    }
}
