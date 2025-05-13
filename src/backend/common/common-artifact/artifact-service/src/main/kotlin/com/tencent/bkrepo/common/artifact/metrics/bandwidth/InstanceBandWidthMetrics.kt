/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.metrics.bandwidth

import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.artifact.metrics.ARTIFACT_DOWNLOADING_SIZE
import com.tencent.bkrepo.common.artifact.metrics.ARTIFACT_UPLOADING_SIZE
import com.tencent.bkrepo.common.artifact.metrics.ArtifactMetrics
import com.tencent.bkrepo.common.storage.innercos.metrics.CosUploadMetrics.Companion.COS_ASYNC_UPLOADING_SIZE
import io.micrometer.core.instrument.Counter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 *  每个服务实例统计下当前上传/下载带宽, 然后存入redis
 */
@Component
class InstanceBandWidthMetrics(
    private val redisTemplate: RedisTemplate<String, String>,
    private val taskScheduler: ThreadPoolTaskScheduler,
) {

    @Value(INSTANCE_IP)
    private lateinit var instance: String

    @Value(SERVICE_NAME)
    private lateinit var serviceName: String

    @Volatile
    private var prevDownloadingMetrics: Double = 0.0

    @Volatile
    private var prevUploadingMetrics: Double = 0.0

    @Volatile
    private var prevCosAsyncUploadingMetrics: Double = 0.0


    init {
        taskScheduler.scheduleWithFixedDelay(this::refreshBandWidthData, DATA_REFRESH_DELAY)
    }

    fun refreshBandWidthData() {
        try {
            storeBandWidth()
        } catch (e: Exception) {
            logger.warn("refresh bandwidth data failed", e)
        }
    }

    fun storeBandWidth() {
        val downloadingNow = (ArtifactMetrics.meterRegistry.meters.firstOrNull {
            it.id.name == ARTIFACT_DOWNLOADING_SIZE
        } as? Counter)?.count() ?: 0.0
        val uploadingNow = (ArtifactMetrics.meterRegistry.meters.firstOrNull {
            it.id.name == ARTIFACT_UPLOADING_SIZE
        } as? Counter)?.count() ?: 0.0
        val cosAsyncUploadingNow = (ArtifactMetrics.meterRegistry.meters.firstOrNull {
            it.id.name == COS_ASYNC_UPLOADING_SIZE
        } as? Counter)?.count() ?: 0.0
        logger.debug(
            "instance ip: $instance, service name: $serviceName, " +
                "downloadingNow: $downloadingNow, uploadingNow: $uploadingNow, " +
                "cosAsyncUploadingNow: $cosAsyncUploadingNow"
        )
        val currentDownloadBandwidth =
            (downloadingNow - prevDownloadingMetrics).coerceAtLeast(0.0) / DATA_REFRESH_DELAY
        val currentUploadBandwidth =
            (uploadingNow - prevUploadingMetrics).coerceAtLeast(0.0) / DATA_REFRESH_DELAY
        val currentCosAsyncUploadBandwidth =
            (cosAsyncUploadingNow - prevCosAsyncUploadingMetrics).coerceAtLeast(0.0) / DATA_REFRESH_DELAY

        logger.debug(
            "instance ip: $instance, service name: $serviceName, " +
                "upload bandwidth: $currentUploadBandwidth, download bandwidth: $currentDownloadBandwidth, " +
                "cos async upload bandwidth: $currentCosAsyncUploadBandwidth"
        )
        val elapsedTime = measureTimeMillis {
            recordBandwidth(
                instance, serviceName, currentUploadBandwidth.toLong(),
                currentDownloadBandwidth.toLong(), currentCosAsyncUploadBandwidth.toLong()
            )
        }
        logger.debug("bandwidth record saved, elapse: ${HumanReadable.time(elapsedTime, TimeUnit.MILLISECONDS)}")
        prevDownloadingMetrics = downloadingNow
        prevUploadingMetrics = uploadingNow
        prevCosAsyncUploadingMetrics = cosAsyncUploadingNow
    }

    fun recordBandwidth(instanceIp: String, serviceName: String, upload: Long, download: Long, cosAsyncUpload: Long) {
        val script = DefaultRedisScript(InstanceBandwidthScript.instanceBandwidthScript, List::class.java)
        val host = instanceIp.replace(".", "_")
        val result = redisTemplate.execute(
            script,
            listOf(
                "$SERVICE_PREFIX$serviceName",
                "$INSTANCE_PREFIX$host$SERVICE_SUFFIX",
                INSTANCE_BANDWIDTH
            ),
            instanceIp,
            upload.toString(),
            download.toString(),
            cosAsyncUpload.toString(),
            (System.currentTimeMillis() / 1000).toString(),
            (DATA_EXPIRE_HOURS * 3600).toString()
        )
        logger.info("result: $result")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InstanceBandWidthMetrics::class.java)
        private const val SERVICE_NAME = "\${service.prefix:}\${spring.application.name}\${service.suffix:}"
        private const val INSTANCE_IP = "\${spring.cloud.client.ip-address}"
        private const val DATA_EXPIRE_HOURS = 24L
        private const val DATA_REFRESH_DELAY: Long = 10 * 1000 // 10s刷新一次


        // Redis键设计
        const val SERVICE_PREFIX = "bw:service:"
        const val INSTANCE_PREFIX = "bw:instance:"
        const val INSTANCE_BANDWIDTH = "bw:instance:total_bandwidth"

        const val SERVICE_SUFFIX = ":services"
    }

}