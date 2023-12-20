package com.tencent.bkrepo.common.bksync.file

/**
 * BD资源头
 * */
class BkSyncDeltaFileHeader(
    val src: String,
    val dest: String,
    val md5Bytes: ByteArray,
    val dataSize: Long,
    val extra: Byte,
) {
    /**
     * 文件长度
     * */
    val size = src.toByteArray().size + dest.toByteArray().size + dataSize + STATIC_LENGTH

    companion object {
        // bd文件头静态部分固定长度
        const val STATIC_LENGTH = 41
    }
}
