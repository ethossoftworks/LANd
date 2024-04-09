package com.ethossoftworks.land.lib.bytes

fun ByteArray.offsetContentEquals(other: ByteArray, start: Int): Boolean {
    if (start >= size) return false

    for (i in other.indices) {
        if (this[i + start] != other[i]) return false
    }

    return true
}

fun ByteArray.toUShort(): UShort {
    val byte1 = (getOrNull(0)?.toUByte()?.toUInt() ?: 0u)
    val byte2 = (getOrNull(1)?.toUByte()?.toUInt() ?: 0u)
    return ((byte1 shl 8) or byte2).toUShort()
}

fun UShort.toByteArray() = byteArrayOf(
    (this.toUInt() shr 8).toByte(),
    (this.toUInt() and 0xFFu).toByte(),
)