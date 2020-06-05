package com.tencent.bkrepo.rpm.util

import java.io.File
import java.io.FileOutputStream
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
}
