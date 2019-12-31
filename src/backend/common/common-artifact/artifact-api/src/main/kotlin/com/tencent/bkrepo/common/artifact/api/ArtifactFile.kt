package com.tencent.bkrepo.common.artifact.api

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * 构件文件
 */
interface ArtifactFile {
    @Throws(IOException::class)
    fun getInputStream(): InputStream
    @Throws(IOException::class)
    fun getOutputStream(): OutputStream
    fun getSize(): Long
    fun getTempFile(): File
    fun delete()
    fun isInMemory(): Boolean
}
