package com.gtc.rootbridgekotlin.core.root

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long
)
