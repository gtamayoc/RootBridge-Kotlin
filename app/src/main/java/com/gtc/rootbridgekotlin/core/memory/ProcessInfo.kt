package com.gtc.rootbridgekotlin.core.memory

data class ProcessInfo(
    val pid: Int,
    val packageName: String,
    val processName: String,
    val isActive: Boolean
)
