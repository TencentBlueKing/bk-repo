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

package com.tencent.bkrepo.common.artifact.metrics

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream.Companion.METADATA_KEY_LOAD_FROM_CACHE
import com.tencent.bkrepo.common.artifact.stream.FileArtifactInputStream
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.BaseUnits
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration

/**
 * 存储层缓存使用情况数据统计
 */
@Component
class ArtifactCacheMetrics(
    private val registry: MeterRegistry,
    private val storageProperties: StorageProperties,
    private val artifactMetricsProperties: ArtifactMetricsProperties,
) {

    /**
     * 统计缓存使用情况
     */
    fun record(resource: ArtifactResource) {
        if (!artifactMetricsProperties.enableArtifactCacheMetrics) {
            return
        }
        try {
            addMetrics(resource)
        } catch (e: Exception) {
            logger.error("record artifact cache metrics failed", e)
        }
    }

    private fun addMetrics(resource: ArtifactResource) {
        val repositoryDetail = ArtifactContextHolder.getRepoDetailOrNull()
        val projectId = repositoryDetail?.projectId
        if (projectId == null || projectId.startsWith("CODE_") || projectId.startsWith("CLOSED_SOURCE_")) {
            return
        }
        val credentials = repositoryDetail.storageCredentials()

        for (inputStream in resource.artifactMap.values) {
            if (inputStream.getMetadata(METADATA_KEY_LOAD_FROM_CACHE) == null) {
                // 不支持从缓存加载时不统计缓存命中率
                continue
            }

            // 统计缓存命中率
            if (inputStream is FileArtifactInputStream) {
                incHitCount(credentials.key(), projectId)
                // 统计缓存访问时间分布
                recordAccessInterval(inputStream.file.toPath(), credentials.cache.expireDuration)
            } else {
                if (inputStream.range.total!! > LOG_CACHE_MISS_FILE_SIZE) {
                    val fullPath = resource.node?.fullPath
                    logger.info(
                        "large file cache miss, " +
                            "project[$projectId], repoName[${repositoryDetail.name}], fullPath[$fullPath]"
                    )
                }
                incMissCount(credentials.key(), projectId)
            }
            // 统计访问的缓存文件大小分布
            recordAccessCacheFileSize(inputStream.range.total!!)
        }
    }

    /**
     * 记录缓存访问时间与创建时间间隔时长的分布
     */
    private fun recordAccessInterval(filePath: Path, maxDuration: Duration) {
        val attr = Files.readAttributes(
            filePath,
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS
        )
        // nfs不支持读取文件更新atime,所以这里用当前时间替换。
        val intervalOfMillis = System.currentTimeMillis() - attr.lastModifiedTime().toMillis()

        DistributionSummary.builder(CACHE_ACCESS_INTERVAL)
            .description("storage cache access time and modified time interval")
            .baseUnit(BaseUnits.MILLISECONDS)
            .publishPercentileHistogram()
            .minimumExpectedValue(MIN_CACHE_ACCESS_INTERVAL)
            .maximumExpectedValue(maxDuration.toMillis().toDouble())
            .register(registry)
            .record(intervalOfMillis.toDouble())
    }

    /**
     * 统计访问的缓存文件大小分布
     */
    private fun recordAccessCacheFileSize(size: Long) {
        DistributionSummary.builder(CACHE_ACCESS_FILE_SIZE)
            .description("storage cache file size")
            .baseUnit(BaseUnits.BYTES)
            .publishPercentileHistogram()
            .minimumExpectedValue(storageProperties.receive.fileSizeThreshold.toBytes().toDouble())
            .maximumExpectedValue(MAX_CACHE_FILE_SIZE)
            .register(registry)
            .record(size.toDouble())
    }

    private fun incHitCount(storageKey: String, projectId: String, inc: Double = 1.0) {
        Counter.builder(CACHE_COUNT_HIT)
            .tag("storageKey", storageKey)
            .tag("projectId", projectId)
            .description("storage cache total count")
            .register(registry)
            .increment(inc)
    }

    private fun incMissCount(storageKey: String, projectId: String, inc: Double = 1.0) {
        Counter.builder(CACHE_COUNT_MISS)
            .tag("storageKey", storageKey)
            .tag("projectId", projectId)
            .description("storage cache total count")
            .register(registry)
            .increment(inc)
    }

    private fun RepositoryDetail.storageCredentials() =
        storageCredentials ?: storageProperties.defaultStorageCredentials()

    private fun StorageCredentials.key() = key ?: "default"

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactCacheMetrics::class.java)
        private const val LOG_CACHE_MISS_FILE_SIZE = 1L * 1024 * 1024 * 1024
        private const val MAX_CACHE_FILE_SIZE = 100.0 * 1024 * 1024 * 1024
        private const val MIN_CACHE_ACCESS_INTERVAL = 1000.0
        private const val CACHE_COUNT_HIT = "storage.cache.count.hit"
        private const val CACHE_COUNT_MISS = "storage.cache.count.miss"
        private const val CACHE_ACCESS_INTERVAL = "storage.cache.access.interval"
        private const val CACHE_ACCESS_FILE_SIZE = "storage.cache.access.file.size"
    }
}
