/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.opdata.service

import com.tencent.bkrepo.common.storage.config.CacheProperties
import com.tencent.bkrepo.common.storage.config.UploadProperties
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageType
import com.tencent.bkrepo.opdata.PathStatMetric
import com.tencent.bkrepo.opdata.filesystem.StoragePathStatVisitor
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Paths

/**
 * 统计配置的分布式存储的容量使用情况
 */
@Service
class FileSystemStorageService(
    private val properties: StorageProperties,
    private val storageCredentialsClient: StorageCredentialsClient

) {

    fun folderStat(): List<PathStatMetric> {
        val paths = when (properties.type) {
            StorageType.FILESYSTEM -> findFileSystemPath(properties)
            StorageType.INNERCOS -> findCfsPath()
            else -> emptyList()
        }
        return paths.map {
            val metric = PathStatMetric(it)
            Files.walkFileTree(Paths.get(it), StoragePathStatVisitor(it, metric))
            metric
        }
    }

    private fun findFileSystemPath(properties: StorageProperties): Set<String> {
        val result = mutableSetOf<String>()
        result.addAll(getLocalPath(properties.filesystem.cache, properties.filesystem.upload))
        result.add(properties.filesystem.path)
        return result
    }

    private fun findCfsPath(): Set<String> {
        val list = storageCredentialsClient.list().data ?: return emptySet()
        val default = storageCredentialsClient.findByKey().data
        val result = mutableSetOf<String>()
        list.forEach {
            result.addAll(getLocalPath(it.cache, it.upload))
        }
        default?.let {
            result.addAll(getLocalPath(default.cache, default.upload))
        }
        return result
    }

    private fun getLocalPath(cache: CacheProperties, upload: UploadProperties): List<String> {
        return listOf(cache.path, upload.localPath, upload.location)
    }
}
