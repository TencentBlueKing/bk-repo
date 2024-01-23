package com.tencent.bkrepo.common.api.util

import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset

/**
 * 流工具
 * */
object StreamUtils {

    /**
     * 阻塞读取满data
     * */
    fun readFully(inputStream: InputStream, data: ByteArray): Int {
        var pos = 0
        val total = data.size
        var remain = total - pos
        do {
            val bytes = inputStream.read(data, pos, remain)
            if (bytes == -1) {
                return pos
            }
            pos += bytes
            remain = total - pos
        } while (remain > 0)
        return pos
    }

    fun InputStream.readText(charset: Charset = Charsets.UTF_8) = this.use {
        it.reader(charset).use { reader -> reader.readText() }
    }

    fun use(
        inputStream: InputStream,
        outputStream: OutputStream,
        block: (input: InputStream, output: OutputStream) -> Unit,
    ) {
        inputStream.use {
            outputStream.use {
                block(inputStream, outputStream)
            }
        }
    }

    fun useCopy(
        inputStream: InputStream,
        outputStream: OutputStream,
    ) {
        use(inputStream, outputStream) { input, output ->
            input.copyTo(output)
        }
    }

    fun InputStream.drain(): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytesRead: Int
        var byteCount: Long = 0
        while (this.read(buffer).also { bytesRead = it } != -1) {
            byteCount += bytesRead
        }
        return byteCount
    }
}
