/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
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
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.agent.session

import com.fasterxml.jackson.databind.ObjectMapper
import com.tencent.bkrepo.agent.config.properties.AgentProperties
import com.tencent.bkrepo.common.redis.RedisOperation
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class AgentSessionConfiguration {

    /** 无 Redis 时退化为进程内实现，便于本地开发；生产多副本应配置 Redis。 */
    @Bean
    fun agentSessionStore(
        properties: AgentProperties,
        redisOperation: ObjectProvider<RedisOperation>,
    ): AgentSessionStore {
        val redis = redisOperation.getIfAvailable()
        if (redis == null) {
            logger.warn("No RedisOperation available, falling back to in-memory agent session ownership store")
            return InMemoryAgentSessionStore()
        }
        return RedisAgentSessionStore(
            redisOperation = redis,
            sessionTtlSeconds = properties.sessionTtl.seconds,
        )
    }

    @Bean
    fun agentRunLock(
        properties: AgentProperties,
        redisOperation: ObjectProvider<RedisOperation>,
    ): AgentRunLock {
        val redis = redisOperation.getIfAvailable()
        if (redis == null) {
            logger.warn("No RedisOperation available, falling back to in-memory agent run lock")
            return InMemoryAgentRunLock()
        }
        return RedisAgentRunLock(
            redisOperation = redis,
            lockTtlSeconds = properties.runLockTtl.seconds,
        )
    }

    @Bean
    fun agentPendingInterruptStore(
        properties: AgentProperties,
        redisOperation: ObjectProvider<RedisOperation>,
        objectMapper: ObjectMapper,
    ): AgentPendingInterruptStore {
        val redis = redisOperation.getIfAvailable()
        if (redis == null) {
            logger.warn("No RedisOperation available, falling back to in-memory pending interrupt store")
            return InMemoryAgentPendingInterruptStore()
        }
        return RedisAgentPendingInterruptStore(
            redisOperation = redis,
            objectMapper = objectMapper,
            ttlSeconds = properties.sessionTtl.seconds,
        )
    }

    @Bean
    fun agentResumeIdempotencyStore(
        properties: AgentProperties,
        redisOperation: ObjectProvider<RedisOperation>,
    ): AgentResumeIdempotencyStore {
        val redis = redisOperation.getIfAvailable()
        if (redis == null) {
            logger.warn("No RedisOperation available, falling back to in-memory resume idempotency store")
            return InMemoryAgentResumeIdempotencyStore()
        }
        return RedisAgentResumeIdempotencyStore(
            redisOperation = redis,
            ttlSeconds = properties.runLockTtl.seconds,
        )
    }

    @Bean
    fun agentActiveRunStore(
        properties: AgentProperties,
        redisOperation: ObjectProvider<RedisOperation>,
    ): AgentActiveRunStore {
        val redis = redisOperation.getIfAvailable()
        if (redis == null) {
            logger.warn("No RedisOperation available, falling back to in-memory active run store")
            return InMemoryAgentActiveRunStore()
        }
        return RedisAgentActiveRunStore(
            redisOperation = redis,
            ttlSeconds = properties.runLockTtl.seconds,
        )
    }

    @Bean
    fun agentRunCancelStore(
        properties: AgentProperties,
        redisOperation: ObjectProvider<RedisOperation>,
    ): AgentRunCancelStore {
        val redis = redisOperation.getIfAvailable()
        if (redis == null) {
            logger.warn("No RedisOperation available, falling back to in-memory run cancel store")
            return InMemoryAgentRunCancelStore()
        }
        return RedisAgentRunCancelStore(
            redisOperation = redis,
            ttlSeconds = properties.runLockTtl.seconds,
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AgentSessionConfiguration::class.java)
    }
}
