package com.tencent.bkrepo.common.artifact.api

import java.io.File
import java.io.InputStream

/**
 * 构件文件
 */
interface ArtifactFile {
    fun getInputStream(): InputStream
    fun getSize(): Long
    fun getFile(): File
    fun delete()
    fun isInMemory(): Boolean
}
