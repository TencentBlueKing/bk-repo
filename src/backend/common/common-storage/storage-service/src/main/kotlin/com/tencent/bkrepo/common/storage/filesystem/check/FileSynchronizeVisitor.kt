/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.storage.filesystem.check

import com.tencent.bkrepo.common.api.constant.JOB_LOGGER_NAME
import com.tencent.bkrepo.common.storage.core.FileStorage
import com.tencent.bkrepo.common.storage.core.locator.FileLocator
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.filesystem.ArtifactFileVisitor
import com.tencent.bkrepo.common.storage.util.delete
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

class FileSynchronizeVisitor(
    private val rootPath: Path,
    private val fileLocator: FileLocator,
    private val fileStorage: FileStorage,
    private val credential: StorageCredentials
) : ArtifactFileVisitor() {

    val result = SynchronizeResult()

    @Throws(IOException::class)
    override fun visitFile(filePath: Path, attributes: BasicFileAttributes): FileVisitResult {
        try {
            if (!exist(filePath)) {
                val size = upload(filePath)
                result.totalSize += size
                result.synchronizedCount += 1
            } else {
                result.ignoredCount += 1
            }
        } catch (ignored: Exception) {
            logger.error("Synchronize file[${filePath.fileName}] error.", ignored)
            result.errorCount += 1
        } finally {
            result.totalCount += 1
        }
        return FileVisitResult.CONTINUE
    }

    @Throws(IOException::class)
    override fun postVisitDirectory(dirPath: Path, exc: IOException?): FileVisitResult {
        if (dirPath == rootPath) {
            return FileVisitResult.CONTINUE
        }
        val deleted = dirPath.delete()
        if (deleted) {
            logger.info("Clean up folder[$dirPath].")
            result.cleanupFolder++
        }
        result.totalFolder++
        return FileVisitResult.CONTINUE
    }

    override fun needWalk() = true

    /**
     * 判断文件在最终存储层是否存在
     * @param filePath 文件路径
     */
    private fun exist(filePath: Path): Boolean {
        val filename = filePath.fileName.toString()
        val path = fileLocator.locate(filename)
        return fileStorage.exist(path, filename, credential)
    }

    /**
     * 上传文件到最终存储层
     * @param filePath 文件路径
     */
    private fun upload(filePath: Path): Long {
        val filename = filePath.fileName.toString()
        val path = fileLocator.locate(filename)
        val file = filePath.toFile()
        fileStorage.store(path, filename, file, credential)
        logger.info("Synchronize file[$filename] success.")
        Files.deleteIfExists(file.toPath())
        logger.info("Delete staging file[$filename] success.")
        return file.length()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(JOB_LOGGER_NAME)
    }
}
