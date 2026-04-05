package com.gtc.rootbridgekotlin.core.memory

enum class DataType(val byteSize: Int) {
    BYTE(1),
    WORD(2),
    DWORD(4),
    QWORD(8),
    FLOAT(4),
    DOUBLE(8)
}

data class ScanResult(
    val address: Long,
    val currentValue: ByteArray,
    val dataType: DataType
) {
    fun getIntValue(): Int {
        if (currentValue.size < 4) return 0
        return (currentValue[0].toInt() and 0xFF) or
               ((currentValue[1].toInt() and 0xFF) shl 8) or
               ((currentValue[2].toInt() and 0xFF) shl 16) or
               ((currentValue[3].toInt() and 0xFF) shl 24)
    }
    
    // For equals/hashcode since we use ByteArray
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScanResult

        if (address != other.address) return false
        if (!currentValue.contentEquals(other.currentValue)) return false
        if (dataType != other.dataType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + currentValue.contentHashCode()
        result = 31 * result + dataType.hashCode()
        return result
    }
}
