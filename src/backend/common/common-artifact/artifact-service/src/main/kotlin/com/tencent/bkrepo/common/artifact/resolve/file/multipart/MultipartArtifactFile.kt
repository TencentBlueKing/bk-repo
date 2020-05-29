package com.tencent.bkrepo.common.artifact.resolve.file.multipart

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactFile.Companion.generatePath
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Paths

class MultipartArtifactFile(
    private val multipartFile: MultipartFile,
    private val fileSizeThreshold: Int,
    location: String,
    resolveLazily: Boolean = true
) : ArtifactFile {

    private val filePath = generatePath(Paths.get(location))
    private val size: Long = multipartFile.size
    private var hasInitialized: Boolean = false
    private var isInMemory: Boolean = true

    init {
        if (!resolveLazily) {
            init()
        }
    }

    fun getOriginalFilename() = multipartFile.originalFilename.orEmpty()

    override fun getInputStream(): InputStream {
        init()
        return if (!isInMemory) {
            Files.newInputStream(filePath)
        } else {
            multipartFile.inputStream
        }
    }

    override fun getSize(): Long = size

    override fun isInMemory() = isInMemory

    override fun getFile(): File? {
        init()
        return if (!isInMemory) {
            filePath.toFile()
        } else null
    }


    override fun flushToFile(): File {
        init()
        if (isInMemory) {
            synchronized(this) {
                multipartFile.transferTo(filePath.toFile())
                isInMemory = false
            }
        }
        return filePath.toFile()
    }

    override fun delete() {
        if (hasInitialized && !isInMemory()) {
            try {
                Files.deleteIfExists(filePath)
            } catch (e: NoSuchFileException) { // already deleted
            }
        }
    }

    private fun init() {
        if (!hasInitialized) {
            if (multipartFile.size > fileSizeThreshold) {
                multipartFile.transferTo(filePath.toFile())
                isInMemory = false
            }
            hasInitialized = true
        }
    }
}
