package com.tencent.bkrepo.rpm.util

import com.sun.xml.internal.messaging.saaj.util.ByteInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object GZipUtil {
    fun ByteArray.gZip(): File {
        val file = File.createTempFile("name", "-primary.xml.gz")
        val tempOutputStream = FileOutputStream(file)
        val gZIPOutputStream = GZIPOutputStream(tempOutputStream)
        gZIPOutputStream.use {
            it.write(this, 0, this.size)
        }
        return file
    }

    /**
     * 解压
     */
    fun InputStream.UnGzipInputStream(): InputStream {
        val gzipInputStream = GZIPInputStream(this)
        val byteArray = gzipInputStream.readBytes()
        return ByteInputStream(byteArray, byteArray.size)
    }
}
