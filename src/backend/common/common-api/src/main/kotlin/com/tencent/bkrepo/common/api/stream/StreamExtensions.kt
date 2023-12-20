package com.tencent.bkrepo.common.api.stream

import com.google.common.primitives.Ints
import com.google.common.primitives.Longs
import java.io.IOException
import java.io.InputStream

fun InputStream.readInt(): Int {
    val bytes = ByteArray(4)
    if (this.read(bytes) < 4) {
        throw IOException("Not enough bytes to read for int.")
    }
    return Ints.fromByteArray(bytes)
}

fun InputStream.readLong(): Long {
    val bytes = ByteArray(8)
    if (this.read(bytes) < 8) {
        throw IOException("Not enough bytes to read for long.")
    }
    return Longs.fromByteArray(bytes)
}
