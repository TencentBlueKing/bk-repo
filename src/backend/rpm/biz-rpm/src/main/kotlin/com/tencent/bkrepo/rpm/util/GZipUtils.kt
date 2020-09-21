package com.tencent.bkrepo.rpm.util

import java.io.File
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object GZipUtils {
    /**
     * 将'xml'以gzip压缩后返回'xml.gz'
     */
    fun ByteArray.gZip(indexType: String): File {
        val file = File.createTempFile("rpm", "-$indexType.xml.gz")
        GZIPOutputStream(FileOutputStream(file)).use { it.write(this, 0, this.size) }
        return file
    }

    @Throws(IOException::class)
    fun InputStream.gZip(indexType: String): File {
        val file = File.createTempFile("rpm", "-$indexType.xml.gz")
        val buffer = ByteArray(5 * 1024 * 1024)
        GZIPOutputStream(FileOutputStream(file)).use {
            var mark: Int
            while (this.read(buffer).also { mark = it } > 0) {
                it.write(buffer, 0, mark)
                it.flush()
            }
        }
        return file
    }

    /**
     * 解压
     */
    fun InputStream.unGzipInputStream(): File {
        val gZIPInputStream = GZIPInputStream(this)
        val file = File.createTempFile("rpm", ".xmlStream")
        val bufferedOutputStream = BufferedOutputStream(FileOutputStream(file))
        val buffer = ByteArray(5 * 1024 * 1024)
        var mark: Int
        while (gZIPInputStream.read(buffer).also { mark = it } > 0) {
            bufferedOutputStream.write(buffer, 0, mark)
            bufferedOutputStream.flush()
        }
        return file
    }
}
