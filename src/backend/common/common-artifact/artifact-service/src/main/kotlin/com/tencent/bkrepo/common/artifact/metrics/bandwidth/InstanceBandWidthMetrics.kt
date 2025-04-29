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
    private lateinit var host: String

    @Value(SERVICE_NAME)
    private lateinit var serviceName: String

    @Volatile
    private var prevDownloadingMetrics: Double = 0.0

    @Volatile
    private var prevUploadingMetrics: Double = 0.0

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
        val currentDownloadBandwidth = downloadingNow - prevDownloadingMetrics
        val currentUploadBandwidth = uploadingNow - prevUploadingMetrics
        logger.debug(
            "instance ip: $host, service name: $serviceName, " +
                "upload bandwidth: $currentUploadBandwidth, download bandwidth: $currentDownloadBandwidth"
        )
        val elapsedTime = measureTimeMillis {
            recordBandwidth(host, serviceName, currentUploadBandwidth.toLong(), currentDownloadBandwidth.toLong())
        }
        logger.debug("bandwidth record saved, elapse: ${HumanReadable.time(elapsedTime, TimeUnit.MILLISECONDS)}")
        prevDownloadingMetrics = downloadingNow
        prevUploadingMetrics = uploadingNow
    }

    fun recordBandwidth(instanceIp: String, serviceName: String, upload: Long, download: Long) {
        val script = DefaultRedisScript(UPDATE_SCRIPT, Long::class.java)
        redisTemplate.execute(
            script,
            listOf(
                "$SERVICE_BANDWIDTH_PREFIX$serviceName",
                "$SERVICE_BANDWIDTH_PREFIX$serviceName$SERVICE_NODES_SUFFIX",
                "$SERVICE_BANDWIDTH_PREFIX$serviceName$SERVICE_SORTED_SUFFIX"
            ),
            instanceIp,
            upload.toString(),
            download.toString(),
            (System.currentTimeMillis() / 1000).toString(),
            (DATA_EXPIRE_HOURS * 3600).toString()
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InstanceBandWidthMetrics::class.java)
        private const val SERVICE_NAME = "\${service.prefix:}\${spring.application.name}\${service.suffix:}"
        private const val INSTANCE_IP = "\${spring.cloud.client.ip-address}"
        private const val DATA_EXPIRE_HOURS = 1L
        private const val DATA_REFRESH_DELAY: Long = 10 * 1000 // 10s刷新一次


        // Redis键设计
        const val SERVICE_BANDWIDTH_PREFIX = "bw:service:"        // 主键前缀
        const val SERVICE_NODES_SUFFIX = ":nodes"                 // 服务节点集合后缀
        const val SERVICE_SORTED_SUFFIX = ":sorted"               // 排序集合后缀

        // Lua脚本保证原子性更新
        private val UPDATE_SCRIPT = """
        -- 参数定义
        local serviceKey = KEYS[1]    -- 服务主键
        local nodesKey = KEYS[2]      -- 节点集合键
        local sortedKey = KEYS[3]     -- 排序集合键
        local nodeIp = ARGV[1]    -- 实例ip
        local upload = tonumber(ARGV[2])  -- 上传带宽
        local download = tonumber(ARGV[3])  -- 下载带宽
        local total = upload + download  -- 总带宽
        local timestamp = tonumber(ARGV[4])     -- 时间戳
        local expireSeconds = tonumber(ARGV[5])  -- 过期时间
        
        -- 更新节点数据
        redis.call('HSET', serviceKey, nodeIp..':upload', upload)
        redis.call('HSET', serviceKey, nodeIp..':download', download)
        redis.call('HSET', serviceKey, nodeIp..':total', total)
        redis.call('HSET', serviceKey, nodeIp..':ts', timestamp)  -- 时间戳
        
        -- 维护节点索引
        redis.call('SADD', nodesKey, nodeIp)
        redis.call('ZADD', sortedKey, total, nodeIp)  -- 按总带宽排序
        
        -- 清理过期节点(超过失效时间未更新的节点)
        local expiredNodes = {}
        local allNodes = redis.call('SMEMBERS', nodesKey)
        for _, ip in ipairs(allNodes) do
            local ts = redis.call('HGET', serviceKey, ip..':ts')
            if not ts or (timestamp - tonumber(ts)) > expireSeconds then
                table.insert(expiredNodes, ip)
            end
        end
        
           -- 批量删除过期节点
        if #expiredNodes > 0 then
            redis.call('SREM', nodesKey, unpack(expiredNodes))
            redis.call('ZREM', sortedKey, unpack(expiredNodes))
        end
        
        -- 设置过期时间
        redis.call('EXPIRE', serviceKey, expireSeconds)
        redis.call('EXPIRE', nodesKey, expireSeconds)
        redis.call('EXPIRE', sortedKey, expireSeconds)
        
        return 1
    """.trimIndent()
    }

}