package com.tencent.bkrepo.common.artifact.resolve.file.chunk

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.event.ArtifactReceivedEvent
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactDataReceiver
import com.tencent.bkrepo.common.ratelimiter.service.RequestLimitCheckService
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitor
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.common.storage.util.toPath
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.NoSuchFileException

/**
 * 支持边写边读，随机读取的ArtifactFile
 * */
class RandomAccessArtifactFile(
    private val monitor: StorageHealthMonitor,
    private val storageCredentials: StorageCredentials,
    storageProperties: StorageProperties,
    private val requestLimitCheckService: RequestLimitCheckService
) : ArtifactFile {

    /**
     * 是否初始化
     */
    private var initialized: Boolean = false

    /**
     * 文件sha1值
     */
    private var sha1: String? = null

    /**
     * 数据接收器
     */
    private val receiver: ArtifactDataReceiver

    init {
        val path = storageCredentials.upload.location.toPath()
        receiver = ArtifactDataReceiver(
            storageProperties.receive, storageProperties.monitor, path,
            requestLimitCheckService = requestLimitCheckService
        )
        monitor.add(receiver)
        if (!monitor.healthy.get()) {
            receiver.unhealthy(monitor.getFallbackPath(), monitor.fallBackReason)
        }
    }

    override fun getInputStream(): InputStream {
        return receiver.getInputStream()
    }

    override fun getSize(): Long {
        return receiver.received
    }

    override fun isInMemory(): Boolean {
        return receiver.inMemory
    }

    override fun getFile(): File? {
        require(receiver.finished)
        return if (!isInMemory()) {
            receiver.filePath.toFile()
        } else null
    }

    override fun flushToFile(): File {
        require(receiver.finished)
        receiver.flushToFile()
        return receiver.filePath.toFile()
    }

    override fun isFallback(): Boolean {
        return receiver.fallback
    }

    override fun getFileMd5(): String {
        require(receiver.finished)
        return receiver.listener.getMd5()
    }

    /**
     * sha1的计算会重新读取流
     */
    override fun getFileSha1(): String {
        require(receiver.finished)
        return sha1 ?: getInputStream().sha1().apply { sha1 = this }
    }

    override fun getFileSha256(): String {
        require(receiver.finished)
        return receiver.listener.getSha256()
    }

    override fun delete() {
        if (initialized && !isInMemory()) {
            try {
                Files.deleteIfExists(receiver.filePath)
            } catch (ignored: NoSuchFileException) { // already deleted
            }
        }
    }

    override fun hasInitialized(): Boolean {
        return initialized
    }

    /**
     * 写入分块数据
     * @param chunk 分块数据
     * @param offset 偏移
     * @param length 数据长度
     */
    fun write(chunk: ByteArray, offset: Int, length: Int) {
        receiver.receiveChunk(chunk, offset, length)
    }

    /**
     * 数据接收完毕
     * 触发后，后续不能再接收数据
     */
    fun finish(): Throughput {
        val throughput = receiver.finish()
        monitor.remove(receiver)
        SpringContextUtils.publishEvent(ArtifactReceivedEvent(this, throughput, storageCredentials))
        return throughput
    }

    /**
     * 关闭文件执行清理逻辑，因为是被动接收数据，所以需要手动关闭文件
     */
    fun close() {
        receiver.cleanOriginalOutputStream()
        monitor.remove(receiver)
    }

    /**
     * 随机读取
     * */
    fun read(position: Long, buf: ByteBuffer): Int {
        val n = buf.remaining().coerceAtMost((getSize() - position.toInt()).toInt())
        if (n == 0) return n
        val data = ByteArray(n)
        if (receiver.inMemory) {
            receiver.cachedByteArray!!.copyInto(
                destination = data, startIndex = position.toInt(),
                endIndex = position.plus(n)
                    .toInt()
            )
        } else {
            val file = receiver.filePath.toFile()
            val randomAccessFile = RandomAccessFile(file, "r")
            randomAccessFile.use {
                randomAccessFile.seek(position)
                randomAccessFile.read(data)
            }
        }
        buf.put(data, 0, n)
        return n
    }

    override fun isInLocalDisk(): Boolean {
        return false
    }
}
