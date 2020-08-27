package com.tencent.bkrepo.rpm.util

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object GZipUtils {
    /**
     * 将'xml'以gzip压缩后返回'xml.gz'
     */
    fun ByteArray.gZip(indexType: String): File {
        val file = File.createTempFile("name", "-$indexType.xml.gz")
        GZIPOutputStream(FileOutputStream(file)).use { it.write(this, 0, this.size) }
        return file
    }

    /**
     * 解压
     */
    fun InputStream.unGzipInputStream(): InputStream {
        return ByteArrayInputStream(GZIPInputStream(this).readBytes())
    }
}
