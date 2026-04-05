package com.gtc.rootbridgekotlin.core.memory

data class MemoryRegion(
    val startAddress: Long,
    val endAddress: Long,
    val permissions: String,
    val label: String
)
