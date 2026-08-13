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
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.agent.config

import com.tencent.bkrepo.agent.config.properties.EffectiveAgentRuntimeProperties
import io.agentscope.core.state.AgentStateStore
import io.agentscope.core.state.InMemoryAgentStateStore
import io.agentscope.extensions.redis.state.RedisAgentStateStore
import io.lettuce.core.RedisClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory

@Configuration(proxyBeanMethods = false)
class AgentStateConfiguration {

    /**
     * AgentScope 的 [RedisAgentStateStore] 要求传入 Lettuce [RedisClient]，比 common-redis 暴露的
     * RedisOperation 低一层，因此直接取 Spring 已建好的原生 client：连接参数、连接池以及 common-redis
     * 为兼容 Redis 4/5 强制的 RESP2 协议都自动沿用，也不会额外开一套连接。
     *
     * client 由 [LettuceConnectionFactory] 持有并负责关闭，此处不能自行 shutdown。
     */
    @Bean
    fun agentStateStore(
        properties: EffectiveAgentRuntimeProperties,
        connectionFactory: ObjectProvider<RedisConnectionFactory>,
    ): AgentStateStore {
        val client = (connectionFactory.getIfAvailable() as? LettuceConnectionFactory)?.nativeClient as? RedisClient
        if (client == null) {
            logger.warn(
                "No lettuce redis client available, falling back to in-memory agent state. " +
                    "Sessions will be lost on restart and cannot be recovered on another replica."
            )
            return InMemoryAgentStateStore()
        }
        return RedisAgentStateStore.builder()
            .lettuceClient(client)
            .keyPrefix(properties.stateKeyPrefix)
            .build()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AgentStateConfiguration::class.java)
    }
}
