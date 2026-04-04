/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.opdata.service

import com.tencent.bkrepo.opdata.pojo.gateway.GatewayHeartbeatInfo
import com.tencent.bkrepo.opdata.pojo.gateway.GatewayHeartbeatRequest
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Gateway心跳服务
 */
@Service
class GatewayHeartbeatService(
    private val redisTemplate: RedisTemplate<String, String>
) {

    /**
     * 保存Gateway心跳数据
     */
    fun saveHeartbeat(request: GatewayHeartbeatRequest) {
        try {
            val ip = request.ip
            val tag = request.tag
            val lastUpdate = LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            logger.info("Saving heartbeat for gateway: ip=$ip, tag=$tag")

            // 存储心跳信息到Redis Hash
            val heartbeatKey = "$GATEWAY_HEARTBEAT_PREFIX$ip"
            val heartbeatData: Map<String, String> = mapOf(
                "ip" to ip,
                "tag" to tag,
                "last_update" to lastUpdate
            )
            redisTemplate.opsForHash<String, String>().putAll(heartbeatKey, heartbeatData)

            // 设置过期时间为180秒（心跳间隔60秒，允许最多丢失2次心跳）
            redisTemplate.expire(heartbeatKey, HEARTBEAT_EXPIRE_SECONDS, TimeUnit.SECONDS)

            // 维护Gateway列表
            redisTemplate.opsForSet().add(GATEWAY_LIST_KEY, ip)
            redisTemplate.expire(GATEWAY_LIST_KEY, HEARTBEAT_EXPIRE_SECONDS, TimeUnit.SECONDS)

            logger.info("Heartbeat saved successfully for gateway: $ip")
        } catch (e: Exception) {
            logger.error("Failed to save heartbeat for gateway: ${request.ip}", e)
            throw e
        }
    }

    /**
     * 获取所有Gateway的tag列表
     */
    fun listAllTags(): List<String> {
        return try {
            // 从gateway列表中获取所有IP
            val gatewayIps = redisTemplate.opsForSet().members(GATEWAY_LIST_KEY) ?: emptySet()
            logger.info("Found ${gatewayIps.size} gateway IPs in Redis")

            // 获取每个IP的tag，去重后返回
            val tags = mutableSetOf<String>()
            gatewayIps.forEach { ip ->
                val key = "$GATEWAY_HEARTBEAT_PREFIX$ip"
                val tag = redisTemplate.opsForHash<String, String>().get(key, "tag")
                if (!tag.isNullOrBlank()) {
                    tags.add(tag)
                }
            }

            logger.info("Found ${tags.size} unique tags")
            tags.sorted()
        } catch (e: Exception) {
            logger.error("Failed to list all tags", e)
            emptyList()
        }
    }

    /**
     * 根据tag获取Gateway列表（只返回在线的Gateway）
     */
    fun listGatewaysByTag(tag: String): List<GatewayHeartbeatInfo> {
        return try {
            // 从gateway列表中获取所有在线IP
            val gatewayIps = redisTemplate.opsForSet().members(GATEWAY_LIST_KEY) ?: emptySet()
            logger.info("Searching for gateways with tag: $tag from ${gatewayIps.size} online IPs")

            // 过滤出指定tag的Gateway，并获取心跳信息
            val result = gatewayIps.mapNotNull { ip ->
                val key = "$GATEWAY_HEARTBEAT_PREFIX$ip"
                val heartbeatData = redisTemplate.opsForHash<String, String>().entries(key)

                // 检查是否有数据且tag匹配
                if (heartbeatData.isNotEmpty() && heartbeatData["tag"] == tag) {
                    GatewayHeartbeatInfo(
                        ip = heartbeatData["ip"] ?: ip,
                        tag = heartbeatData["tag"] ?: "",
                        lastUpdate = heartbeatData["last_update"] ?: "",
                        online = true
                    )
                } else {
                    null
                }
            }

            logger.info("Found ${result.size} online gateways with tag: $tag")
            result
        } catch (e: Exception) {
            logger.error("Failed to list gateways by tag: $tag", e)
            emptyList()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GatewayHeartbeatService::class.java)

        // Redis Key常量
        private const val GATEWAY_HEARTBEAT_PREFIX = "bkrepo:gateway:heartbeat:"
        private const val GATEWAY_LIST_KEY = "bkrepo:gateway:list"

        // 心跳过期时间（秒）
        private const val HEARTBEAT_EXPIRE_SECONDS = 180L
    }
}


