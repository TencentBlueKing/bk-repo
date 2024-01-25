package com.tencent.bkrepo.common.bksync.file

import java.io.File
import java.io.InputStream

/**
 * BD文件
 * */
class FileBkSyncDeltaSource(
    src: String,
    dest: String,
    srcMd5: ByteArray,
    val file: File,
) : BkSyncDeltaSource(src, dest, srcMd5) {
    override fun content(): InputStream {
        return file.inputStream()
    }

    override fun contentLength(): Long {
        return file.length()
    }
}
