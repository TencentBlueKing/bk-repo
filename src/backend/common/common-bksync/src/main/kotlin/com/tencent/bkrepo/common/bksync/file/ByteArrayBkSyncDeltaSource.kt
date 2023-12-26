package com.tencent.bkrepo.common.bksync.file

import java.io.InputStream

/**
 * 字节数组实现的BD资源
 * */
class ByteArrayBkSyncDeltaSource(
    src: String,
    dest: String,
    srcMd5: ByteArray,
    val bytes: ByteArray,
) : BkSyncDeltaSource(src, dest, srcMd5) {
    override fun content(): InputStream {
        return bytes.inputStream()
    }

    override fun contentLength(): Long {
        return bytes.size.toLong()
    }
}
