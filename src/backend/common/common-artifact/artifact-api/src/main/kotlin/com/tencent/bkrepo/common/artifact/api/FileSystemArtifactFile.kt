package com.tencent.bkrepo.common.artifact.api

import java.io.File

class FileSystemArtifactFile(private val file: File) : ArtifactFile {
    override fun getInputStream() = file.inputStream()

    override fun getSize() = file.length()

    override fun isInMemory() = false

    override fun getFile() = file

    override fun flushToFile() = file

    override fun delete() = file.deleteOnExit()
}

fun File.toArtifactFile(): ArtifactFile {
    return FileSystemArtifactFile(this)
}
