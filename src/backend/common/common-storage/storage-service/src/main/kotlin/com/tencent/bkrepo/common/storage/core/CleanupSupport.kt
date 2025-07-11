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

package com.tencent.bkrepo.common.storage.core

import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.filesystem.FileSystemClient
import com.tencent.bkrepo.common.storage.filesystem.cleanup.CleanupFileVisitor
import com.tencent.bkrepo.common.storage.filesystem.cleanup.CleanupResult
import com.tencent.bkrepo.common.storage.filesystem.cleanup.BasedAtimeAndMTimeFileExpireResolver
import com.tencent.bkrepo.common.storage.util.toPath
import java.nio.file.Path

/**
 * 文件清理操作实现类
 */
abstract class CleanupSupport : HealthCheckSupport() {

    override fun cleanUp(storageCredentials: StorageCredentials?): Map<Path, CleanupResult> {
        val credentials = getCredentialsOrDefault(storageCredentials)
        val tempPath = getTempPath(credentials)
        val result = mutableMapOf<Path, CleanupResult>()
        result[tempPath] = cleanupPath(tempPath, credentials)
        result.putAll(cleanUploadPath(credentials))
        return result
    }

    protected fun cleanUploadPath(credentials: StorageCredentials): Map<Path, CleanupResult> {
        val uploadPath = credentials.upload.location.toPath()
        val localUploadPath = credentials.upload.localPath.toPath()
        return mapOf(
            uploadPath to  cleanupPath(uploadPath, credentials),
            localUploadPath to cleanupPath(localUploadPath, credentials),
        )
    }

    private fun cleanupPath(path: Path, credentials: StorageCredentials): CleanupResult {
        val fileExpireResolver = BasedAtimeAndMTimeFileExpireResolver(credentials.cache.expireDuration)
        val visitor = CleanupFileVisitor(
            path,
            path,
            null,
            fileStorage,
            fileLocator,
            credentials,
            fileExpireResolver,
            publisher
        )
        FileSystemClient(path).walk(visitor)
        return visitor.result
    }
}
