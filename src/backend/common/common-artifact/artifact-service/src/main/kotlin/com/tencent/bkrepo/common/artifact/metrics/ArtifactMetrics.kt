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

package com.tencent.bkrepo.common.artifact.metrics

import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactDataReceiver
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.MeterBinder
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

@Suppress("LateinitUsage")
@Component
class ArtifactMetrics(
    private val threadPoolTaskExecutor: ThreadPoolTaskExecutor,
    tagProvider: ArtifactTransferTagProvider,
    meterRegistry: MeterRegistry,
    val properties: ArtifactMetricsProperties
) : MeterBinder {

    private val logger = LoggerFactory.getLogger(ArtifactMetrics::class.java)
    private val customMeterNames = listOf(
        ARTIFACT_DOWNLOADING_TIME,
        ARTIFACT_DOWNLOADING_SIZE,
        ARTIFACT_UPLOADING_TIME,
        ARTIFACT_UPLOADING_SIZE,
        ARTIFACT_UPLOADED_SIZE,
        ARTIFACT_DOWNLOADED_SIZE
    )
    var uploadingCount = AtomicInteger(0)
    var downloadingCount = AtomicInteger(0)

    init {
        Companion.tagProvider = tagProvider
        Companion.meterRegistry = meterRegistry
    }

    @Scheduled(fixedDelay = 60 * 1000)
    fun gc() {
        logger.info("Start gc in meter registry.")
        var cleans = 0
        val total = meterRegistry.meters.size
        if (total > properties.maxMeters) {
            meterRegistry.meters.forEach {
                val name = it.id.name
                // 清理自定义meter,只清理timer和counter,gauge是在一开始注册且数量固定
                if (customMeterNames.contains(name)) {
                    meterRegistry.remove(it)
                    cleans++
                }
            }
            logger.info("Clean $cleans/$total meters.")
        }
    }

    override fun bindTo(meterRegistry: MeterRegistry) {
        Gauge.builder(ARTIFACT_UPLOADING_COUNT, uploadingCount, { it.get().toDouble() })
            .description(ARTIFACT_UPLOADING_COUNT_DESC)
            .register(meterRegistry)

        Gauge.builder(ARTIFACT_DOWNLOADING_COUNT, downloadingCount, { it.get().toDouble() })
            .description(ARTIFACT_DOWNLOADING_COUNT_DESC)
            .register(meterRegistry)

        Gauge.builder(ASYNC_TASK_ACTIVE_COUNT, threadPoolTaskExecutor.threadPoolExecutor, { it.activeCount.toDouble() })
            .description(ASYNC_TASK_ACTIVE_COUNT_DESC)
            .register(meterRegistry)

        Gauge.builder(ASYNC_TASK_QUEUE_SIZE, threadPoolTaskExecutor.threadPoolExecutor, { it.queue.size.toDouble() })
            .description(ASYNC_TASK_QUEUE_SIZE_DESC)
            .register(meterRegistry)
    }

    companion object {
        private lateinit var tagProvider: ArtifactTransferTagProvider
        private lateinit var meterRegistry: MeterRegistry
        private const val BYTES = "bytes"

        /**
         * 获取已上传文件大小摘要
         * 用于上传计算文件分布
         * */
        fun getUploadedDistributionSummary(): DistributionSummary {
            return DistributionSummary.builder(ARTIFACT_UPLOADED_SIZE)
                .description(ARTIFACT_UPLOADED_SIZE_DESC)
                .baseUnit(BYTES)
                .publishPercentileHistogram()
                .register(meterRegistry)
        }

        /**
         * 获取已下载文件摘要
         * 用于计算下载文件分布
         * */
        fun getDownloadedDistributionSummary(): DistributionSummary {
            return DistributionSummary.builder(ARTIFACT_DOWNLOADED_SIZE)
                .description(ARTIFACT_DOWNLOADED_SIZE_DESC)
                .baseUnit(BYTES)
                .publishPercentileHistogram()
                .register(meterRegistry)
        }

        /**
         * 获取实时上传计数器
         * 用于计算上传实时流量
         * */
        fun getUploadingCounter(receiver: ArtifactDataReceiver): Counter {
            return Counter.builder(ARTIFACT_UPLOADING_SIZE)
                .description(ARTIFACT_UPLOADING_SIZE_DESC)
                .tags(tagProvider.getTags(receiver, true))
                .baseUnit(BYTES)
                .register(meterRegistry)
        }

        /**
         * 获取实时上传计时器
         * 用于计算上传IOPS和IO平均延迟
         * */
        fun getUploadingTimer(receiver: ArtifactDataReceiver): Timer {
            return Timer.builder(ARTIFACT_UPLOADING_TIME)
                .description(ARTIFACT_UPLOADING_TIME_DESC)
                .tags(tagProvider.getTags(receiver, false))
                .register(meterRegistry)
        }

        /**
         * 获取实时下载计数器
         * 用于计算下载实时流量
         * */
        fun getDownloadingCounter(inputStream: ArtifactInputStream): Counter {
            return Counter.builder(ARTIFACT_DOWNLOADING_SIZE)
                .description(ARTIFACT_DOWNLOADING_SIZE_DESC)
                .baseUnit(BYTES)
                .tags(tagProvider.getTags(inputStream, true))
                .register(meterRegistry)
        }

        /**
         * 获取实时下载计时器
         * 用于计算下载IOPS和IO平均延迟
         * */
        fun getDownloadingTimer(inputStream: ArtifactInputStream): Timer {
            return Timer.builder(ARTIFACT_DOWNLOADING_TIME)
                .description(ARTIFACT_DOWNLOADING_TIME_DESC)
                .tags(tagProvider.getTags(inputStream, false))
                .register(meterRegistry)
        }
    }
}
