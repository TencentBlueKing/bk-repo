/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.proxy.artifact.storage

import com.tencent.bkrepo.common.service.exception.RemoteErrorCodeException
import com.tencent.bkrepo.common.storage.filesystem.ArtifactFileVisitor
import com.tencent.bkrepo.repository.api.proxy.ProxyFileReferenceClient
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit

/**
 * 不同步时，清理本地存储的文件遍历器
 */
class ProxyCleanFileVisitor(
    private val proxyFileReferenceClient: ProxyFileReferenceClient,
    private val cacheExpireDays: Int
) : ArtifactFileVisitor() {
    override fun needWalk(): Boolean {
        return true
    }

    override fun visitFile(filePath: Path, attrs: BasicFileAttributes?): FileVisitResult {
        try {
            if (filePath.toString().endsWith(".sync")) {
                val syncFile = filePath.toFile()
                val dataFile = File(filePath.toString().removeSuffix(".sync"))
                cleanFile(dataFile, syncFile)
            } else {
                ProxyStorageUtils.deleteCacheFile(filePath, cacheExpireDays)
            }
        } catch (e: Exception) {
            logger.error("clean file error: ", e)
        }
        return FileVisitResult.CONTINUE
    }

    /**
     * 当文件索引为0时，删除本地存储
     */
    private fun cleanFile(dataFile: File, syncFile: File) {
        val attrs = Files.readAttributes(dataFile.toPath(), BasicFileAttributes::class.java)
        val creationTime = attrs.creationTime().toMillis()
        // 防止刚存储，未创建文件引用时被误删
        if (System.currentTimeMillis() - creationTime < TimeUnit.MINUTES.toMillis(1)) {
            return
        }
        val sha256 = dataFile.name
        val credentialKeys = ProxyStorageUtils.readStorageCredentialKeys(syncFile)
        try {
            credentialKeys.forEach {
                val fileReference = proxyFileReferenceClient.count(sha256, it).data!!
                if (fileReference > 0) {
                    logger.info("skip clean file[$sha256] which file reference is $fileReference on storage[$it]")
                    return
                }
            }
        } catch (e: RemoteErrorCodeException) {
            logger.warn("get sha256[$sha256] file reference failed: ${e.errorMessage}")
            return
        }
        dataFile.delete()
        syncFile.delete()
        logger.info("clean file[$sha256] success")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProxyCleanFileVisitor::class.java)
    }
}
