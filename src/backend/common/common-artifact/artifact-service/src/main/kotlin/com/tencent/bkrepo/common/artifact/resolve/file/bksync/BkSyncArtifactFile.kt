/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 *
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

package com.tencent.bkrepo.common.artifact.resolve.file.bksync

import com.google.common.hash.HashCode
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.bksync.BkSync
import com.tencent.bkrepo.common.bksync.BlockChannel
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitor
import com.tencent.bkrepo.common.storage.util.toPath
import java.io.File
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.math.abs

class BkSyncArtifactFile(
    private val blockChannel: BlockChannel,
    private val deltaInputStream: InputStream,
    private val blockSize: Int,
    private val storageCredentials: StorageCredentials,
    private val monitor: StorageHealthMonitor
) : ArtifactFile {
    private var path = storageCredentials.upload.location.toPath()
    private val tempFile: File
    private var isFallback = false

    /**
     * 文件sha1值
     */
    private var sha1: String? = null

    /**
     * 文件sha256值
     */
    private var sha256: String? = null

    /**
     * 文件md5值
     */
    private var md5: String? = null

    init {
        val fallbackPath = monitor.getFallbackPath()
        if (!monitor.healthy.get() && fallbackPath != null) {
            path = fallbackPath
            isFallback = true
        }
        tempFile = path.resolve(generateFilename()).toFile()
    }

    /**
     * 是否初始化
     */
    private var initialized: Boolean = false

    override fun getInputStream(): InputStream {
        init()
        return tempFile.inputStream()
    }

    override fun getSize(): Long {
        init()
        return tempFile.length()
    }

    override fun isInMemory(): Boolean {
        return false
    }

    override fun getFile(): File {
        init()
        return tempFile
    }

    override fun flushToFile(): File {
        init()
        return tempFile
    }

    override fun delete() {
        tempFile.delete()
    }

    override fun hasInitialized(): Boolean {
        return initialized
    }

    override fun isFallback(): Boolean {
        init()
        return isFallback
    }

    override fun getFileMd5(): String {
        init()
        return md5!!
    }

    override fun getFileSha1(): String {
        init()
        return sha1!!
    }

    override fun getFileSha256(): String {
        init()
        return sha256!!
    }

    override fun isInLocalDisk(): Boolean {
        return false
    }

    private fun init() {
        if (initialized) {
            return
        }
        val bkSync = BkSync(blockSize)
        val channel = FileChannel.open(tempFile.toPath(), StandardOpenOption.WRITE)
        channel.use {
            bkSync.merge(blockChannel, deltaInputStream, channel)
        }
        digest()
        initialized = true
    }

    @Suppress("UnstableApiUsage")
    private fun digest() {
        val md5Digest: MessageDigest = MessageDigest.getInstance("MD5")
        val sha256Digest = MessageDigest.getInstance("SHA-256")
        val sha1Digest = MessageDigest.getInstance("SHA-1")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        tempFile.inputStream().buffered().use {
            var read = it.read(buffer)
            while (read != -1) {
                md5Digest.update(buffer, 0, read)
                sha1Digest.update(buffer, 0, read)
                sha256Digest.update(buffer, 0, read)
                read = it.read(buffer)
            }
        }
        md5 = HashCode.fromBytes(md5Digest.digest()).toString()
        sha256 = HashCode.fromBytes(sha256Digest.digest()).toString()
        sha1 = HashCode.fromBytes(sha1Digest.digest()).toString()
    }

    companion object {
        private val random = SecureRandom()
        private const val BK_SYNC_PATCH_PREFIX = "patch_"
        private const val BK_SYNC_PATCH_SUFFIX = ".temp"

        private fun generateFilename(): String {
            var randomLong = random.nextLong()
            randomLong = if (randomLong == Long.MIN_VALUE) 0 else abs(randomLong)
            return BK_SYNC_PATCH_PREFIX + randomLong.toString() + BK_SYNC_PATCH_SUFFIX
        }
    }
}
