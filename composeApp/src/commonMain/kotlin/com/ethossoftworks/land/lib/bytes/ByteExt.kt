package com.ethossoftworks.land.lib.bytes

fun ByteArray.offsetContentEquals(other: ByteArray, start: Int): Boolean {
    if (start >= size) return false

    for (i in other.indices) {
        if (this[i + start] != other[i]) return false
    }

    return true
}