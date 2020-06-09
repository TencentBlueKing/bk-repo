package com.tencent.bkrepo.common.artifact.api

import com.tencent.bkrepo.common.artifact.hash.md5
import com.tencent.bkrepo.common.artifact.hash.sha256
import java.io.File
import java.nio.file.Files

class FileSystemArtifactFile(private val file: File) : ArtifactFile {

    private var md5: String? = null
    private var sha256: String? = null

    override fun getInputStream() = file.inputStream()

    override fun getSize() = file.length()

    override fun isInMemory() = false

    override fun getFile() = file

    override fun flushToFile() = file

    override fun isFallback() = false

    override fun getFileMd5(): String {
        return md5 ?: run { file.md5().apply { md5 = this } }
    }

    override fun getFileSha256(): String {
        return sha256 ?: run { file.sha256().apply { sha256 = this } }
    }

    override fun delete() {
        Files.deleteIfExists(file.toPath())
    }
}

fun File.toArtifactFile(): ArtifactFile {
    return FileSystemArtifactFile(this)
}
