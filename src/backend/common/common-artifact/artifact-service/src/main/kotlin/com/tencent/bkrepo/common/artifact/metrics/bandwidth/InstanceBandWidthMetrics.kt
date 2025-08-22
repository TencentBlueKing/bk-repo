/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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
import com.tencent.bkrepo.common.artifact.metrics.ArtifactMetricsProperties
import com.tencent.bkrepo.common.storage.innercos.metrics.CosUploadMetrics.Companion.COS_ASYNC_UPLOADING_SIZE
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
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
    private val artifactMetricsProperties: ArtifactMetricsProperties
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
        taskScheduler.scheduleWithFixedDelay(this::refreshBandWidthData, DATA_REFRESH_DELAY * 1000)
    }

    fun refreshBandWidthData() {
        try {
            if (!artifactMetricsProperties.enableInstanceBandwidthMetrics) return
            storeBandWidth()
        } catch (e: Exception) {
            logger.warn("refresh bandwidth data failed, error: ${e.message}")
        }
    }

    fun storeBandWidth() {
        // 由于同时存在Prometheus和influxdb，会导致counter类型变成CompositeCounter，
        // CompositeCounter类型存在个bug，会在获取count()值时可能会变成0
        // https://github.com/micrometer-metrics/micrometer/issues/1441
        // 安全获取 Prometheus 注册表
        val realMeterRegistry = getPrometheusRegistry() ?: return
        val downloadingNow = (realMeterRegistry.meters.firstOrNull {
            it.id.name == ARTIFACT_DOWNLOADING_SIZE
        } as? Counter)?.count() ?: 0.0

        val uploadingNow = (realMeterRegistry.meters.firstOrNull {
            it.id.name == ARTIFACT_UPLOADING_SIZE
        } as? Counter)?.count() ?: 0.0
        val cosAsyncUploadingNow = (realMeterRegistry.meters.firstOrNull {
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
            store2Redis(
                instance, serviceName, currentUploadBandwidth,
                currentDownloadBandwidth, currentCosAsyncUploadBandwidth
            )
        }
        logger.debug("bandwidth record saved, elapse: ${HumanReadable.time(elapsedTime, TimeUnit.MILLISECONDS)}")
        prevDownloadingMetrics = downloadingNow
        prevUploadingMetrics = uploadingNow
        prevCosAsyncUploadingMetrics = cosAsyncUploadingNow
    }

    fun getPrometheusRegistry(): PrometheusMeterRegistry? {
        return when (val registry = ArtifactMetrics.meterRegistry) {
            is CompositeMeterRegistry -> registry.registries
                .filterIsInstance<PrometheusMeterRegistry>()
                .firstOrNull()

            is PrometheusMeterRegistry -> registry
            else -> null
        }
    }

    fun store2Redis(instanceIp: String, serviceName: String, upload: Double, download: Double, cosAsyncUpload: Double) {
        // 构造 Redis 键
        val serviceKey = "$SERVICE_PREFIX$serviceName"
        val instanceServiceKey = "$INSTANCE_PREFIX$instanceIp$SERVICE_SUFFIX"
        val instanceTotalKey = INSTANCE_BANDWIDTH
        val expireSeconds = DATA_EXPIRE_HOURS * 3600
        val currentTimestamp = System.currentTimeMillis() / 1000
        // 1. 使用管道批量更新带宽数据和时间戳
        redisTemplate.executePipelined { connection ->
            val hashOps = redisTemplate.opsForHash<String, String>()
            hashOps.put(instanceServiceKey, "$serviceKey$TS_SUFFIX", currentTimestamp.toString())
            hashOps.put(instanceServiceKey, "$serviceKey$UPLOAD_SUFFIX", upload.toString())
            hashOps.put(instanceServiceKey, "$serviceKey$DOWNLOAD_SUFFIX", download.toString())
            hashOps.put(instanceServiceKey, "$serviceKey$COS_ASYNC_UPLOAD_SUFFIX", cosAsyncUpload.toString())
            null
        }

        // 2. 计算主机总带宽
        val services = redisTemplate.opsForHash<String, String>().entries(instanceServiceKey)
        var total = 0.0
        services.forEach { (field, value) ->
            if (!field.endsWith(TS_SUFFIX)) {
                // 检查服务是否活跃（5分钟内）
                val tsKey = field.substringBeforeLast(':') + TS_SUFFIX
                val timestamp = services[tsKey]?.toLongOrNull() ?: 0L
                if ((currentTimestamp - timestamp) < 300) { // 5分钟阈值
                    total += value.toDoubleOrNull() ?: 0.0
                }
            }
        }

        // 3. 批量执行剩余Redis操作
        redisTemplate.executePipelined { connection ->
            // 更新主机总带宽有序集合
            redisTemplate.opsForZSet().add(instanceTotalKey, instanceIp, total.toDouble())

            // 将主机添加到服务的实例集合中
            redisTemplate.opsForSet().add(serviceKey, instanceIp)

            // 设置键的过期时间
            redisTemplate.expire(serviceKey, expireSeconds, TimeUnit.SECONDS)
            redisTemplate.expire(instanceServiceKey, expireSeconds, TimeUnit.SECONDS)
            redisTemplate.expire(instanceTotalKey, expireSeconds, TimeUnit.SECONDS)
            null
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InstanceBandWidthMetrics::class.java)
        private const val SERVICE_NAME = "\${service.prefix:}\${spring.application.name}\${service.suffix:}"
        private const val INSTANCE_IP = "\${spring.cloud.client.ip-address}"
        private const val DATA_EXPIRE_HOURS = 1L
        private const val DATA_REFRESH_DELAY: Long = 1 // 1s刷新一次


        // Redis键设计
        const val SERVICE_PREFIX = "bw:service:"
        const val INSTANCE_PREFIX = "bw:instance:"
        const val INSTANCE_BANDWIDTH = "bw:instance:total_bandwidth"

        const val SERVICE_SUFFIX = ":services"
        const val UPLOAD_SUFFIX = ":upload"
        const val TS_SUFFIX = ":ts"
        const val DOWNLOAD_SUFFIX = ":download"
        const val COS_ASYNC_UPLOAD_SUFFIX = ":cos_async_upload"


    }

}
