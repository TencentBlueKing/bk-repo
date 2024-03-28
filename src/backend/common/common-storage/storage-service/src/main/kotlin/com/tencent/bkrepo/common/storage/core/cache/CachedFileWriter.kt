/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.storage.core.cache

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.stream.StreamReadListener
import com.tencent.bkrepo.common.artifact.stream.closeQuietly
import com.tencent.bkrepo.common.storage.util.createNewOutputStream
import com.tencent.bkrepo.common.storage.util.existReal
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path

/**
 * 实现[StreamReadListener]接口，用于将FileStorage读取到的数据写入到缓存文件中
 *
 * @param cachePath 缓存路径
 * @param filename 缓存文件名称
 * @param tempPath 临时路径
 * @param listener 缓存写入过程监听器
 *
 * 处理逻辑：
 * 1. 在[tempPath]下原子创建锁文件[filename].locked
 * 1. 在[tempPath]下创建一个临时文件
 * 2. 数据写入该临时文件
 * 3. 数据写完毕后，将该文件move到[cachePath]位置
 *
 * 并发处理逻辑：对于同一个文件[filename]，可能存在多个并发下载请求触发缓存
 * 1. 首先看是否可以获取到锁，如果不能则跳过（说明此时有其它请求正在进行缓存）
 * 2. 获取锁成功后，执行上述流程
 * 3. 最后判断当前的锁拥有者是否是自己，不是则跳过，理由同上。（NFS的原子创建不保证原子性，所以获取到锁的请求不一定唯一，这里需要再次校验缓存的执行者）
 * 4. 判断[cachePath]是否存在缓存文件，没有则执行move操作。（如果[cachePath]已经有文件，再进行move，可能会删除使用中的缓存文件）
 *
 */
class CachedFileWriter(
    private val cachePath: Path,
    private val filename: String,
    tempPath: Path,
    private val listener: CacheFileWriterListener? = null,
) : StreamReadListener {

    private val taskId = StringPool.randomStringByLongValue(prefix = CACHE_PREFIX, suffix = CACHE_SUFFIX)
    private var tempFilePath: Path = tempPath.resolve(taskId)
    private var lockFilePath: Path = tempPath.resolve(filename.plus(LOCK_SUFFIX))
    private var cacheFilePath = cachePath.resolve(filename)
    private var outputStream: OutputStream? = null

    init {
        try {
            if (!cacheFilePath.existReal() && tryLock()) {
                outputStream = Files.newOutputStream(tempFilePath)
                logger.info("Prepare cache file[$cacheFilePath].")
            }
        } catch (ignore: FileAlreadyExistsException) {
            // 如果目录或者文件已存在则忽略
        } catch (exception: Exception) {
            logger.error("initial CacheFileWriter error: $exception", exception)
            close()
        }
    }

    override fun data(i: Int) {
        outputStream?.let {
            try {
                it.write(i)
            } catch (ignored: Exception) {
                // ignored
                close()
            }
        }
    }

    override fun data(buffer: ByteArray, off: Int, length: Int) {
        outputStream?.let {
            try {
                it.write(buffer, off, length)
            } catch (ignored: Exception) {
                // ignored
                close()
            }
        }
    }

    override fun finish() {
        outputStream?.let {
            try {
                it.flush()
                it.closeQuietly()
                moveToCachePath()
            } finally {
                close()
            }
        }
    }

    /**
     * 尝试获取锁
     * 原子生成.locked的锁文件
     * @return 成功生成锁文件返回true,否则返回false
     * */
    private fun tryLock(): Boolean {
        return try {
            lockFilePath.createNewOutputStream().use {
                it.write(taskId.toByteArray())
            }
            true
        } catch (e: FileAlreadyExistsException) {
            // ignore
            false
        }
    }

    /**
     * 释放锁
     * 删除锁文件
     * */
    private fun releaseLock() {
        Files.deleteIfExists(lockFilePath)
    }

    /**
     * 判断锁的拥有者是否是自己
     * */
    private fun isSelfLock(): Boolean {
        if (!Files.exists(lockFilePath)) {
            return false
        }
        Files.newInputStream(lockFilePath).use {
            val lockId = String(it.readBytes())
            return lockId == taskId
        }
    }

    /**
     * 将文件移动到缓存路径
     * */
    private fun moveToCachePath() {
        if (!Files.exists(cachePath)) {
            Files.createDirectories(cachePath)
        }
        if (!isSelfLock()) {
            return
        }
        try {
            if (!cacheFilePath.existReal()) {
                Files.move(tempFilePath, cacheFilePath)
                listener?.onCacheFileWritten(filename, cacheFilePath)
                logger.info("Success cache file $filename")
            }
        } catch (ignore: FileAlreadyExistsException) {
            logger.info("File[$cacheFilePath] already exists")
        } catch (exception: Exception) {
            logger.error("Finish CacheFileWriter error: $exception", exception)
        }
    }

    override fun close() {
        outputStream?.let {
            try {
                it.closeQuietly()
            } catch (exception: Exception) {
                logger.error("close CacheFileWriter error: $exception", exception)
            } finally {
                outputStream = null
                release()
            }
        }
    }

    /**
     * 释放资源
     * */
    private fun release() {
        Files.deleteIfExists(tempFilePath)
        if (isSelfLock()) {
            releaseLock()
        }
    }

    companion object {
        private const val LOCK_SUFFIX = ".locked"
        private const val CACHE_PREFIX = "cache_"
        private const val CACHE_SUFFIX = ".temp"
        private val logger = LoggerFactory.getLogger(CachedFileWriter::class.java)
    }
}
