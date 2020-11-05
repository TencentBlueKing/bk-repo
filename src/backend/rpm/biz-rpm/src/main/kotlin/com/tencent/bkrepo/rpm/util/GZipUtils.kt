package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.common.artifact.stream.closeQuietly
import com.tencent.bkrepo.rpm.pojo.IndexType
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

    /**
     * 压缩文件，并关闭文件流
     */
    @Throws(IOException::class)
    fun InputStream.gZip(indexType: IndexType): File {
        try {
            val file = File.createTempFile("rpm", "-${indexType.value}.xml.gz")
            val buffer = ByteArray(1 * 1024 * 1024)
            GZIPOutputStream(FileOutputStream(file)).use { gzipOutputStream ->
                var mark: Int
                while (this.read(buffer).also { mark = it } > 0) {
                    gzipOutputStream.write(buffer, 0, mark)
                    gzipOutputStream.flush()
                }
            }
            return file
        } finally {
            this.closeQuietly()
        }
    }

    /**
     * 解压gz文件，并关闭文件流
     */
    fun InputStream.unGzipInputStream(): File {
        val gZIPInputStream = GZIPInputStream(this)
        val file = File.createTempFile("rpm", ".xmlStream")
        val bufferedOutputStream = BufferedOutputStream(FileOutputStream(file))
        val buffer = ByteArray(1 * 1024 * 1024)
        var mark: Int
        try {
            while (gZIPInputStream.read(buffer).also { mark = it } > 0) {
                bufferedOutputStream.write(buffer, 0, mark)
                bufferedOutputStream.flush()
            }
        } finally {
            gZIPInputStream.closeQuietly()
            bufferedOutputStream.closeQuietly()
            this.closeQuietly()
        }
        return file
    }
}
