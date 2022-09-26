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

package com.tencent.bkrepo.opdata.job

import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.storage.config.CacheProperties
import com.tencent.bkrepo.common.storage.config.UploadProperties
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageType
import com.tencent.bkrepo.opdata.config.OpFileSystemStatJobProperties
import com.tencent.bkrepo.opdata.filesystem.StoragePathStatVisitor
import com.tencent.bkrepo.opdata.model.TPathStatMetric
import com.tencent.bkrepo.opdata.pojo.storage.PathStatMetric
import com.tencent.bkrepo.opdata.repository.FileSystemMetricsRepository
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Paths

@Service
class FileSystemStorageStatJob(
    private val opJobProperties: OpFileSystemStatJobProperties,
    private val properties: StorageProperties,
    private val storageCredentialsClient: StorageCredentialsClient,
    private val fileSystemMetricsRepository: FileSystemMetricsRepository

) {
    @Scheduled(cron = "00 00 05 * * ?")
    @SchedulerLock(name = "FileSystemStorageStatJob", lockAtMostFor = "PT10H")
    fun statFolderSize() {
        if (!opJobProperties.enabled) {
            logger.info("The job of file system storage stat is disabled.")
            return
        }
        logger.info("start to stat the metrics of file system storage")
        fileSystemMetricsRepository.deleteAll()
        folderStatAndStore()
        logger.info("stat metrics of file system storage done")
    }

    fun folderStatAndStore() {
        logger.info("Will start to collect the metrics for folders")
        val paths = when (properties.type) {
            StorageType.FILESYSTEM -> findFileSystemPath(properties)
            StorageType.INNERCOS -> findStoragePath()
            else -> emptyList()
        }
        paths.map {
            logger.info("Metrics of folders $it will be collected")
            val file = File(it)
            val metric = PathStatMetric(
                path = it,
                totalSpace = file.totalSpace,
                usableSpace = file.usableSpace
            )
            try {
                Files.walkFileTree(Paths.get(it), StoragePathStatVisitor(it, metric))
            } catch (ignore: NoSuchFileException) {
            }
            storeMetrics(metric)
        }
    }

    private fun storeMetrics(metric: PathStatMetric) {
        val folderMetricsList = mutableListOf<TPathStatMetric>()
        folderMetricsList.add(
            TPathStatMetric(
                path = metric.path,
                totalSize = metric.totalSize,
                totalFileCount = metric.totalFileCount,
                totalFolderCount = metric.totalFolderCount,
                totalSpace = metric.totalSpace,
                usedPercent = if (metric.totalSpace == 0L) {
                    0.0
                } else {
                    BigDecimal((metric.totalSpace - metric.usableSpace) / (metric.totalSpace * 1.0))
                        .setScale(4, RoundingMode.HALF_UP).toDouble()
                }
            )
        )
        folderMetricsList.addAll(
            metric.folders.map {
                TPathStatMetric(
                    path = it.key,
                    totalSize = it.value,
                    rootPath = metric.path
                )
            }
        )
        logger.info("start to insert the metrics of file system storage path ${metric.path}")
        fileSystemMetricsRepository.insert(folderMetricsList)
    }

    private fun findFileSystemPath(properties: StorageProperties): Set<String> {
        val result = mutableSetOf<String>()
        result.addAll(getLocalPath(properties.filesystem.cache, properties.filesystem.upload))
        result.add(properties.filesystem.path)
        return result
    }

    private fun findStoragePath(): Set<String> {
        val list = storageCredentialsClient.list().data ?: return emptySet()
        val default = properties.defaultStorageCredentials()
        val result = mutableSetOf<String>()
        list.forEach {
            result.addAll(getLocalPath(it.cache, it.upload))
        }
        result.addAll(getLocalPath(default.cache, default.upload))
        return result
    }

    private fun getLocalPath(cache: CacheProperties, upload: UploadProperties): List<String> {
        return listOf(cache.path, upload.location)
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}
