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

package com.tencent.bkrepo.job.batch.task.cache

import com.tencent.bkrepo.common.api.util.executeAndMeasureTime
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.constant.DEFAULT_STORAGE_KEY
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.util.toPath
import com.tencent.bkrepo.job.batch.base.DefaultContextJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.ExpiredCacheFileCleanupJobProperties
import com.tencent.bkrepo.job.metrics.StorageCacheMetrics
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime

/**
 * 清理缓存文件定时任务
 */
@Component
@EnableConfigurationProperties(ExpiredCacheFileCleanupJobProperties::class)
class ExpiredCacheFileCleanupJob(
    private val properties: ExpiredCacheFileCleanupJobProperties,
    private val mongoTemplate: MongoTemplate,
    private val storageService: StorageService,
    private val clusterProperties: ClusterProperties,
    private val storageProperties: StorageProperties,
    private val storageCacheMetrics: StorageCacheMetrics,
) : DefaultContextJob(properties) {

    data class TStorageCredentials(
        var id: String? = null,
        var createdBy: String,
        var createdDate: LocalDateTime,
        var lastModifiedBy: String,
        var lastModifiedDate: LocalDateTime,
        var credentials: String,
        var region: String? = null
    )

    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)

    override fun doStart0(jobContext: JobContext) {
        // cleanup default storage
        if (DEFAULT_STORAGE_KEY !in properties.ignoredStorageCredentialsKeys) {
            cleanupStorage(storageProperties.defaultStorageCredentials())
        }
        // cleanup extended storage
        mongoTemplate.find(Query(), TStorageCredentials::class.java, COLLECTION_NAME)
            .filter { clusterProperties.region.isNullOrBlank() || it.region == clusterProperties.region }
            .filter { (it.id ?: DEFAULT_STORAGE_KEY) !in properties.ignoredStorageCredentialsKeys }
            .map { convert(it) }
            .forEach { cleanupStorage(it) }
    }

    private fun cleanupStorage(storage: StorageCredentials) {
        val key = storage.key ?: "default"
        logger.info("Starting to clean up on storage [$key].")
        executeAndMeasureTime {
            storageService.cleanUp(storage)
        }.apply {
            first[storage.cache.path.toPath()]?.let {
                storageCacheMetrics.setCacheMetrics(key, it.rootDirNotDeletedSize, it.rootDirNotDeletedFile)
                storageCacheMetrics.setProjectRetainCacheMetrics(key, it.retainSha256)
            }
            logger.info("Clean up on storage[$key] completed, summary: $first, elapse [${second.seconds}] s.")
        }
    }

    private fun convert(credentials: TStorageCredentials): StorageCredentials {
        return credentials.credentials.readJsonString<StorageCredentials>().apply { this.key = credentials.id }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ExpiredCacheFileCleanupJob::class.java)
        private const val COLLECTION_NAME = "storage_credentials"
    }
}
