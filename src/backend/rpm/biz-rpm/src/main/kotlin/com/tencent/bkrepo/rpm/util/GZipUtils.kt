package com.tencent.bkrepo.rpm.util

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object GZipUtils {
    /**
     * 将字节数组写入临时 gzip 压缩文件
     */
    fun ByteArray.gZip(): File {
        val file = File.createTempFile("rpm_", "_xml.gz")
        GZIPOutputStream(FileOutputStream(file)).use { it.write(this) }
        return file
    }

    /**
     * 将输入流写入临时 gzip 压缩文件
     */
    fun InputStream.gZip(): File {
        this.use {
            val file = File.createTempFile("rpm_", "_xml.gz")
            GZIPOutputStream(FileOutputStream(file)).use { gzipOutputStream ->
                var len: Int
                val buffer = ByteArray(1 * 1024 * 1024)
                while (this.read(buffer).also { len = it } > 0) {
                    gzipOutputStream.write(buffer, 0, len)
                }
                gzipOutputStream.flush()
            }
            return file
        }
    }

    /**
     * 将文件压缩为临时 gzip 文件
     */
    fun File.gZip(): File {
        return this.inputStream().gZip()
    }

    /**
     * 将 gzip 输入流解压到临时文件
     */
    fun InputStream.unGzipInputStream(): File {
        GZIPInputStream(this).use { gZIPInputStream ->
            val file = File.createTempFile("rpm_", ".xmlStream")
            BufferedOutputStream(FileOutputStream(file)).use { bufferedOutputStream ->
                var len: Int
                val buffer = ByteArray(1 * 1024 * 1024)
                while (gZIPInputStream.read(buffer).also { len = it } > 0) {
                    bufferedOutputStream.write(buffer, 0, len)
                }
                bufferedOutputStream.flush()
            }
            return file
        }
    }
}
