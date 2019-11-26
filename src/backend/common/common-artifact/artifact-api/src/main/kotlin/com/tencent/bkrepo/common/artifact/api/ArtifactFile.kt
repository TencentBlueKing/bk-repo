package com.tencent.bkrepo.common.artifact.api

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.apache.commons.io.output.DeferredFileOutputStream

/**
 * application/octet-stream 流文件
 *
 * @author: carrypan
 * @date: 2019/10/30
 */
open class ArtifactFile(
    private val directory: File,
    sizeThreshold: Int
) {

    /**
     * The size of the item, in bytes. This is used to cache the size when a
     * file item is moved from its original location.
     */
    private var size: Long = -1

    /**
     * Cached contents of the file.
     */
    private var cachedContent: ByteArray? = null

    /**
     * The temporary file to use.
     */
    @Transient
    private var tempFile: File? = null

    /**
     * Output stream for this item.
     */
    @Transient
    val outputStream: DeferredFileOutputStream

    init {
        val outputFile = getTempFile()
        outputStream = DeferredFileOutputStream(sizeThreshold, outputFile)
    }

    @Throws(IOException::class)
    fun getInputStream(): InputStream {
        if (!isInMemory()) {
            return FileInputStream(outputStream.file)
        }

        if (cachedContent == null) {
            cachedContent = outputStream.data
        }
        return ByteArrayInputStream(cachedContent)
    }

    fun getSize(): Long {
        return when {
            size >= 0 -> size
            cachedContent != null -> cachedContent!!.size.toLong()
            outputStream.isInMemory -> outputStream.data.size.toLong()
            else -> outputStream.file.length()
        }
    }

    fun getTempFile(): File {
        if (tempFile == null) {
            val tempFileName = "upload_${uid}_${getUniqueId()}.tmp"
            tempFile = File(directory, tempFileName)
        }
        return tempFile as File
    }

    fun delete() {
        cachedContent = null
        val outputFile = getStoreLocation()
        outputFile?.takeIf { this.isInMemory() && it.exists() }?.delete()
    }

    protected fun finalize() {
        if (outputStream.isInMemory) {
            return
        }
        val outputFile = outputStream.file

        if (outputFile?.exists() == true) {
            outputFile.delete()
        }
    }

    private fun getUniqueId(): String {
        val limit = 100000000
        val current = counter.getAndIncrement()
        var id = current.toString()

        // If you manage to get more than 100 million of ids, you'll
        // start getting ids longer than 8 characters.
        if (current < limit) {
            id = "00000000$id".substring(id.length)
        }
        return id
    }

    private fun isInMemory(): Boolean {
        return if (cachedContent != null) true else outputStream.isInMemory
    }

    private fun getStoreLocation(): File? {
        return when {
            isInMemory() -> null
            else -> outputStream.file
        }
    }

    override fun toString(): String {
        return "StoreLocation=${getStoreLocation()}, size=${getSize()} bytes, inMemory=${isInMemory()}"
    }

    companion object {
        /**
         * UID used in unique file name generation.
         */
        private val uid = UUID.randomUUID().toString().replace('-', '_')

        /**
         * Counter used in unique identifier generation.
         */
        private val counter = AtomicInteger(0)
    }
}
