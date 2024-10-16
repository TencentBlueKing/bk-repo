package com.tencent.bkrepo.common.api.stream

import java.io.InputStream

/**
 * 分块输入流
 * */
class ChunkedInputStream(
    val iterator: Iterator<InputStream>,
) : InputStream() {
    private var cursor: InputStream = iterator.next()
    override fun read(): Int {
        val read = cursor.read()
        if (read == -1) {
            cursor.close()
            if (iterator.hasNext()) {
                move2next()
                return cursor.read()
            }
        }
        return read
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = cursor.read(b, off, len)
        if (read == -1) {
            cursor.close()
            if (iterator.hasNext()) {
                move2next()
                return cursor.read(b, off, len)
            }
        }
        return read
    }

    private fun move2next() {
        cursor = iterator.next()
    }

    override fun close() {
        cursor.close()
    }
}
