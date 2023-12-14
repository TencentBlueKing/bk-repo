/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.common.artifact.event.listener

import com.tencent.bkrepo.common.api.constant.StringPool.UNKNOWN
import com.tencent.bkrepo.common.api.util.toJson
import com.tencent.bkrepo.common.artifact.constant.DEFAULT_STORAGE_KEY
import com.tencent.bkrepo.common.artifact.event.ArtifactReceivedEvent
import com.tencent.bkrepo.common.artifact.event.ArtifactResponseEvent
import com.tencent.bkrepo.common.artifact.metrics.ArtifactMetrics
import com.tencent.bkrepo.common.artifact.metrics.ArtifactMetricsProperties
import com.tencent.bkrepo.common.artifact.metrics.ArtifactTransferRecord
import com.tencent.bkrepo.common.artifact.metrics.ArtifactTransferRecordLog
import com.tencent.bkrepo.common.artifact.metrics.InfluxMetricsExporter
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.FileArtifactInputStream
import com.tencent.bkrepo.common.operate.api.ProjectUsageStatisticsService
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.actuator.CommonTagProvider
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.util.concurrent.LinkedBlockingQueue

/**
 * 构件传输事件监听器
 */
@Component // 使用kotlin时，spring aop对@Import导入的bean不生效
class ArtifactTransferListener(
    private val influxMetricsExporter: ObjectProvider<InfluxMetricsExporter>,
    private val artifactMetricsProperties: ArtifactMetricsProperties,
    private val commonTagProvider: ObjectProvider<CommonTagProvider>,
    private val projectUsageStatisticsService: ProjectUsageStatisticsService,
) {

    private var queue = LinkedBlockingQueue<ArtifactTransferRecord>(QUEUE_LIMIT)

    @EventListener(ArtifactReceivedEvent::class)
    fun listen(event: ArtifactReceivedEvent) {
        with(event) {
            logger.info("Receive artifact file, $throughput.")

            val repositoryDetail = ArtifactContextHolder.getRepoDetailOrNull()
            val clientIp: String = HttpContextHolder.getClientAddress()
            val projectId = repositoryDetail?.projectId ?: UNKNOWN
            val record = ArtifactTransferRecord(
                time = Instant.now(),
                type = ArtifactTransferRecord.RECEIVE,
                elapsed = throughput.time,
                bytes = throughput.bytes,
                average = throughput.average(),
                storage = storageCredentials?.key ?: DEFAULT_STORAGE_KEY,
                sha256 = artifactFile.getFileSha256(),
                project = projectId,
                repoName = repositoryDetail?.name ?: UNKNOWN,
                clientIp = clientIp
            )
            if (SecurityUtils.getUserId() != SYSTEM_USER) {
                projectUsageStatisticsService.inc(projectId = projectId, receivedBytes = throughput.bytes)
            }
            if (artifactMetricsProperties.collectByLog) {
                logger.info(
                    toJson(
                        ArtifactTransferRecordLog(
                            record = record,
                            commonTag = commonTagProvider.ifAvailable?.provide().orEmpty()
                        )
                    )
                )
            }
            queue.offer(record)
            ArtifactMetrics.getUploadedDistributionSummary().record(throughput.bytes.toDouble())
        }
    }

    @EventListener(ArtifactResponseEvent::class)
    fun listen(event: ArtifactResponseEvent) {
        with(event) {
            logger.info("Response artifact file, $throughput.")

            val repositoryDetail = ArtifactContextHolder.getRepoDetailOrNull()
            val clientIp: String = HttpContextHolder.getClientAddress()
            val projectId = repositoryDetail?.projectId ?: UNKNOWN
            val record = ArtifactTransferRecord(
                time = Instant.now(),
                type = ArtifactTransferRecord.RESPONSE,
                elapsed = throughput.time,
                bytes = throughput.bytes,
                average = throughput.average(),
                storage = storageCredentials?.key ?: DEFAULT_STORAGE_KEY,
                sha256 = artifactResource.node?.sha256.orEmpty(),
                project = projectId,
                repoName = repositoryDetail?.name ?: UNKNOWN,
                clientIp = clientIp
            )
            if (SecurityUtils.getUserId() != SYSTEM_USER) {
                projectUsageStatisticsService.inc(projectId = projectId, responseBytes = throughput.bytes)
            }
            ArtifactMetrics.getDownloadedDistributionSummary().record(throughput.bytes.toDouble())
            recordAccessTimeDistribution(artifactResource)
            if (artifactMetricsProperties.collectByLog) {
                logger.info(
                    toJson(
                        ArtifactTransferRecordLog(
                            record = record,
                            commonTag = commonTagProvider.ifAvailable?.provide().orEmpty()
                        )
                    )
                )
            }
            queue.offer(record)
        }
    }

    /**
     * 记录访问时间分布
     * */
    private fun recordAccessTimeDistribution(resource: ArtifactResource) {
        val accessTimeDs = ArtifactMetrics.getAccessTimeDistributionSummary()
        resource.artifactMap.filter { it.value is FileArtifactInputStream }
            .map { it.value as FileArtifactInputStream }
            .forEach {
                val attr = Files.readAttributes(
                    it.file.toPath(),
                    BasicFileAttributes::class.java,
                    LinkOption.NOFOLLOW_LINKS
                )
                // nfs不支持读取文件更新atime,所以这里用当前时间替换。
                val intervalOfMillis = System.currentTimeMillis() - attr.lastModifiedTime().toMillis()
                val intervalOfDays = intervalOfMillis / MILLIS_OF_DAY + 1
                if (logger.isDebugEnabled && intervalOfDays > 30) {
                    logger.debug("File[${it.file}] since last access more than 30d.")
                }
                accessTimeDs.record(intervalOfDays.toDouble())
            }
    }

    @Scheduled(fixedDelay = FIXED_DELAY, initialDelay = FIXED_DELAY)
    fun export() {
        val current = queue
        queue = LinkedBlockingQueue(QUEUE_LIMIT)
        influxMetricsExporter.ifAvailable?.export(current)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactTransferListener::class.java)

        /**
         * 队列大小限制
         */
        private const val QUEUE_LIMIT = 4096

        /**
         * 30s
         */
        private const val FIXED_DELAY = 30 * 1000L

        private const val MILLIS_OF_DAY = 24 * 3600 * 1000L
    }
}
