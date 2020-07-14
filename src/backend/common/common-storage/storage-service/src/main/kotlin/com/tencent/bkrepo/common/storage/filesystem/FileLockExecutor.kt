package com.tencent.bkrepo.common.storage.filesystem

import com.tencent.bkrepo.common.artifact.stream.closeQuietly
import com.tencent.bkrepo.common.artifact.stream.releaseQuietly
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.channels.ReadableByteChannel

object FileLockExecutor {

    private const val READ_WRITE = "rw"

    /**
     * 独占锁，只能单独写，不能读
     */
    fun executeInLock(file: File, shared: Boolean = false, block: (channel: FileChannel) -> Unit) {
        RandomAccessFile(file, READ_WRITE).use {
            val channel = it.channel
            val lock = acquireLock(channel, shared)
            try {
                block(channel)
            } catch (exception: Exception) {
                throw exception
            } finally {
                releaseLock(lock)
            }
        }
    }

    /**
     * 共享锁, 支持多读，不能写
     */
    fun executeInLock(inputStream: InputStream, block: (channel: ReadableByteChannel) -> Unit) {
        Channels.newChannel(inputStream).use {
            if (it is FileChannel) {
                val lock = acquireLock(it, true)
                try {
                    block(it)
                } catch (exception: Exception) {
                    throw exception
                } finally {
                    releaseLock(lock)
                }
            } else {
                block(it)
            }
        }
    }

    private fun acquireLock(channel: FileChannel, shared: Boolean): FileLock {
        while (true) {
            try {
                channel.tryLock(0L, Long.MAX_VALUE, shared)?.let { return it }
                Thread.sleep(200)
            } catch (exception: OverlappingFileLockException) {
                // locked by the same JVM, ignore
            }
        }
    }

    private fun releaseLock(lock: FileLock) {
        lock.releaseQuietly()
        lock.channel().closeQuietly()
    }
}
