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

package com.tencent.bkrepo.common.storage.filesystem.cleanup

import com.google.common.util.concurrent.RateLimiter
import com.tencent.bkrepo.common.api.constant.JOB_LOGGER_NAME
import com.tencent.bkrepo.common.storage.core.FileStorage
import com.tencent.bkrepo.common.storage.core.locator.FileLocator
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.filesystem.ArtifactFileVisitor
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * 文件清理visitor
 */
@Suppress("UnstableApiUsage")
class CleanupFileVisitor(
    private val rootPath: Path,
    private val tempPath: Path,
    private val stagingPath: Path? = null,
    private val fileStorage: FileStorage,
    private val fileLocator: FileLocator,
    private val credentials: StorageCredentials,
    private val fileExpireResolver: FileExpireResolver,
) : ArtifactFileVisitor() {

    val result = CleanupResult()
    private val expireDuration = credentials.cache.expireDuration
    private val rateLimiter = RateLimiter.create(permitsPerSecond)

    @Throws(IOException::class)
    override fun visitFile(filePath: Path, attributes: BasicFileAttributes): FileVisitResult {
        val size = attributes.size()
        try {
            if (fileExpireResolver.isExpired(filePath.toFile()) && !isNFSTempFile(filePath)) {
                if (isTempFile(filePath) || existInStorage(filePath)) {
                    rateLimiter.acquire()
                    Files.delete(filePath)
                    result.cleanupFile += 1
                    result.cleanupSize += size
                    logger.info("Clean up file[$filePath], size[$size], summary: $result")
                }
            }
        } catch (ignored: Exception) {
            logger.error("Clean file[${filePath.fileName}] error.", ignored)
            result.errorCount++
        } finally {
            result.totalFile += 1
            result.totalSize += size
        }
        return FileVisitResult.CONTINUE
    }

    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes?): FileVisitResult {
        if (dir == stagingPath) {
            return FileVisitResult.SKIP_SUBTREE
        }
        return FileVisitResult.CONTINUE
    }

    @Throws(IOException::class)
    override fun postVisitDirectory(dirPath: Path, exc: IOException?): FileVisitResult {
        // 当目录不为根目录，且目录下不存在子文件时，删除当前目录。临时目录和暂存目录不删除。
        if (dirPath == rootPath || dirPath == tempPath || dirPath == stagingPath) {
            return FileVisitResult.CONTINUE
        }
        // 由于支持删除上传路径，所以这里即使是空目录，也需要判断过期时间。
        if (fileExpireResolver.isExpired(dirPath.toFile())) {
            Files.newDirectoryStream(dirPath).use {
                if (!it.iterator().hasNext()) {
                    Files.delete(dirPath)
                    logger.info("Clean up folder[$dirPath].")
                    result.cleanupFolder += 1
                }
            }
        }
        result.totalFolder += 1
        return FileVisitResult.CONTINUE
    }

    override fun needWalk(): Boolean {
        return expireDuration.seconds > 0
    }

    override fun visitFileFailed(file: Path, exc: IOException?): FileVisitResult {
        if (exc is NoSuchFileException) {
            // 目录或者文件已经由其他进程删除。
            logger.info("File [$file] already delete.")
        } else {
            logger.error("Clean up file [$file] error.", exc)
            result.errorCount++
            if (Files.isRegularFile(file)) {
                result.totalFile++
            } else {
                result.totalFolder++
            }
        }
        return FileVisitResult.CONTINUE
    }

    /**
     * 判断文件是否为临时文件
     * @param filePath 文件路径
     */
    private fun isTempFile(filePath: Path): Boolean {
        return filePath.startsWith(tempPath)
    }

    /**
     * 判断文件是否存在于最终存储态
     * @param filePath 文件路径
     */
    private fun existInStorage(filePath: Path): Boolean {
        val filename = filePath.fileName.toString()
        val path = fileLocator.locate(filename)
        return fileStorage.exist(path, filename, credentials)
    }

    /**
     * 判断是否是nfs临时文件
     * */
    private fun isNFSTempFile(filePath: Path): Boolean {
        return filePath.fileName.toString().startsWith(NFS_TEMP_FILE_PREFIX)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(JOB_LOGGER_NAME)
        private const val permitsPerSecond = 30.0
        private const val NFS_TEMP_FILE_PREFIX = ".nfs"
    }
}
