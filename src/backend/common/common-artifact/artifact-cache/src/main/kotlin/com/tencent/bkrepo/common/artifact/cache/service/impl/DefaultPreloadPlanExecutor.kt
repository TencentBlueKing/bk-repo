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

package com.tencent.bkrepo.common.artifact.cache.service.impl

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.StreamUtils.drain
import com.tencent.bkrepo.common.artifact.cache.config.ArtifactPreloadProperties
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlan
import com.tencent.bkrepo.common.artifact.cache.service.PreloadListener
import com.tencent.bkrepo.common.artifact.cache.service.PreloadPlanExecutor
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.core.cache.CacheStorageService
import com.tencent.bkrepo.common.storage.core.locator.FileLocator
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.common.storage.monitor.measureThroughput
import com.tencent.bkrepo.common.storage.util.existReal
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 制品加载器，负责将制品加载到存储缓存中
 */
class DefaultPreloadPlanExecutor(
    private val preloadProperties: ArtifactPreloadProperties,
    private val cacheStorageService: StorageService,
    private val storageCredentialsClient: StorageCredentialsClient,
    private val fileLocator: FileLocator,
    private val storageProperties: StorageProperties,
) : PreloadPlanExecutor {
    private val executor = ThreadPoolExecutor(
        preloadProperties.preloadConcurrency, preloadProperties.preloadConcurrency,
        1L, TimeUnit.MINUTES,
        SynchronousQueue(),
        ThreadFactoryBuilder().setNameFormat("preload-%d").build(),
    )

    override fun execute(plan: ArtifactPreloadPlan, listener: PreloadListener?): Boolean {
        require(cacheStorageService is CacheStorageService)
        updateExecutor()
        val credentials = if (plan.credentialsKey != null) {
            storageCredentialsClient.findByKey(plan.credentialsKey).data!!
        } else {
            storageProperties.defaultStorageCredentials()
        }

        // 仅缓存健康检查成功时才执行预加载
        if (!cacheStorageService.cacheHealthy(credentials)) {
            logger.warn("cache[${credentials.key}] unhealthy, refuse preload ${plan.sha256}]")
            return false
        }

        // 提交预加载任务
        try {
            executor.execute { load(plan, credentials, listener) }
        } catch (e: RejectedExecutionException) {
            return false
        }
        return true
    }

    fun load(plan: ArtifactPreloadPlan, credentials: StorageCredentials, listener: PreloadListener?) {
        try {
            logger.info("preload start, ${plan.artifactInfo()}")
            listener?.onPreloadStart(plan)
            if (System.currentTimeMillis() - plan.executeTime > preloadProperties.planTimeout.toMillis()) {
                throw RuntimeException("plan timeout[${plan.executeTime}], ${plan.artifactInfo()}")
            }
            val cacheFile = Paths.get(credentials.cache.path, fileLocator.locate(plan.sha256), plan.sha256)
            val cacheFileLock = Paths.get(credentials.cache.path, StringPool.TEMP, "${plan.sha256}.locked")
            val throughput = if (cacheFile.existReal()) {
                Files.setLastModifiedTime(cacheFile, FileTime.fromMillis(System.currentTimeMillis()))
                logger.info("cache already exists, update LastModifiedTime, ${plan.artifactInfo()}")
                null
            } else if (cacheFileLock.existReal()) {
                logger.info("cache file is loading, skip preload, ${plan.artifactInfo()}")
                null
            } else {
                // 执行预加载
                doLoad(plan.sha256, plan.size, credentials)
            }
            logger.info("preload success, ${plan.artifactInfo()}, throughput[$throughput]")
            listener?.onPreloadSuccess(plan)
        } catch (e: Exception) {
            listener?.onPreloadFailed(plan)
            logger.warn("preload failed, ${plan.artifactInfo()}", e)
        } finally {
            listener?.onPreloadFinished(plan)
        }
    }

    private fun doLoad(sha256: String, size: Long, credentials: StorageCredentials?): Throughput {
        return cacheStorageService.load(sha256, Range.full(size), credentials)?.use {
            val aisCacheEnabled = it.getMetadata(ArtifactInputStream.METADATA_KEY_CACHE_ENABLED)
            if (aisCacheEnabled == true) {
                measureThroughput { it.drain() }
            } else {
                throw RuntimeException("cache is not enabled")
            }
        } ?: throw RuntimeException("artifact not exists")
    }

    private fun updateExecutor() {
        if (preloadProperties.preloadConcurrency != executor.corePoolSize) {
            executor.corePoolSize = preloadProperties.preloadConcurrency
            executor.maximumPoolSize = preloadProperties.preloadConcurrency
            logger.info("update executor pool size to ${preloadProperties.preloadConcurrency} success")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DefaultPreloadPlanExecutor::class.java)
    }
}
