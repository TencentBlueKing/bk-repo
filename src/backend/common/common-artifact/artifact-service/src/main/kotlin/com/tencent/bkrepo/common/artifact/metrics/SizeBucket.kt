package com.tencent.bkrepo.common.artifact.metrics

/**
 * 文件大小分桶（IEC 二进制单位）
 */
object SizeBucket {
    const val LE_1MIB = "<=1MiB"
    const val LE_10MIB = "1-10MiB"
    const val LE_50MIB = "10-50MiB"
    const val LE_100MIB = "50-100MiB"
    const val LE_1GIB = "100MiB-1GiB"
    const val LE_10GIB = "1-10GiB"
    const val LE_50GIB = "10-50GiB"
    const val LE_100GIB = "50-100GiB"
    const val LE_200GIB = "100-200GiB"
    const val GT_200GIB = ">200GiB"

    fun of(bytes: Long): String = when {
        bytes <= 1L shl 20 -> LE_1MIB
        bytes <= 10L shl 20 -> LE_10MIB
        bytes <= 50L shl 20 -> LE_50MIB
        bytes <= 100L shl 20 -> LE_100MIB
        bytes <= 1L shl 30 -> LE_1GIB
        bytes <= 10L shl 30 -> LE_10GIB
        bytes <= 50L shl 30 -> LE_50GIB
        bytes <= 100L shl 30 -> LE_100GIB
        bytes <= 200L shl 30 -> LE_200GIB
        else -> GT_200GIB
    }
}
