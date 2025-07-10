/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.storage.core.cache.event

import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import java.io.File

class CacheFileEventPublisher(private val publisher: ApplicationEventPublisher) {
    fun publishCacheFileDeletedEvent(
        path: String,
        filename: String,
        size: Long,
        credentials: StorageCredentials,
    ) {
        val cacheFilePath = "${credentials.cache.path}$path$filename"
        val event = CacheFileDeletedEvent(CacheFileEventData(credentials, filename, cacheFilePath, size))
        safePublish(event)
    }

    fun publishCacheFileLoadedEvent(credentials: StorageCredentials, cacheFile: File) {
        val data = CacheFileEventData(credentials, cacheFile.name, cacheFile.absolutePath, cacheFile.length())
        val event = CacheFileLoadedEvent(data)
        safePublish(event)
    }

    fun publishCacheFileAccessEvent(
        path: String,
        filename: String,
        size: Long,
        credentials: StorageCredentials,
    ) {
        val cacheFilePath = "${credentials.cache.path}$path$filename"
        val data = CacheFileEventData(credentials, filename, cacheFilePath, size)
        safePublish(CacheFileAccessedEvent(data))
    }

    private fun safePublish(event: Any) {
        try {
            publisher.publishEvent(event)
        } catch (e: Exception) {
            logger.error("publish cache file event failed", e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CacheFileEventPublisher::class.java)
    }
}
