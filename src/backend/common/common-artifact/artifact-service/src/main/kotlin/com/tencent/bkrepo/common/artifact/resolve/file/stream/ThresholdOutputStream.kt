package com.tencent.bkrepo.common.artifact.resolve.file.stream

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

class ThresholdOutputStream(
    private val fileSizeThreshold: Int,
    private val filePath: Path
) : OutputStream() {
    var totalBytes: Long = 0L
    private val contentBytes = ByteArrayOutputStream()
    private var outputStream: OutputStream = contentBytes
    private var isInMemory: Boolean = true

    fun isInMemory() = isInMemory

    fun getCachedByteArray(): ByteArray {
        return contentBytes.toByteArray()
    }

    @Synchronized
    fun flushToFile() {
        if (isInMemory) {
            Files.createFile(filePath)
            val fileOutputStream = Files.newOutputStream(filePath)
            contentBytes.writeTo(fileOutputStream)
            contentBytes.flush()
            outputStream = fileOutputStream
            isInMemory = false
        }
    }

    override fun write(b: Int) {
        checkThreshold(1)
        outputStream.write(b)
        totalBytes += 1
    }

    override fun write(byteArray: ByteArray) {
        checkThreshold(byteArray.size)
        outputStream.write(byteArray)
        totalBytes += byteArray.size
    }

    override fun write(byteArray: ByteArray, off: Int, len: Int) {
        checkThreshold(len)
        outputStream.write(byteArray, off, len)
        totalBytes += len
    }

    override fun flush() {
        outputStream.flush()
    }

    override fun close() {
        try {
            flush()
        } catch (ignored: IOException) {
            // ignore
        }
        outputStream.close()
    }

    private fun checkThreshold(size: Int) {
        if (isInMemory && totalBytes + size > fileSizeThreshold) {
            flushToFile()
        }
    }
}
