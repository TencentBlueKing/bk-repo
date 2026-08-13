/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.runtime

import com.tencent.bkrepo.agent.config.properties.EffectiveAgentRuntimeProperties
import com.tencent.bkrepo.agent.runtime.store.ActiveRunStateStore
import com.tencent.bkrepo.agent.runtime.store.InMemoryActiveRunStateStore
import com.tencent.bkrepo.agent.runtime.store.RedisActiveRunStateStore
import com.tencent.bkrepo.common.redis.RedisOperation
import io.agentscope.core.state.AgentStateStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ActiveRunConfiguration {

    @Bean
    fun activeRunStateStore(
        properties: EffectiveAgentRuntimeProperties,
        redisOperation: ObjectProvider<RedisOperation>,
    ): ActiveRunStateStore {
        val redis = redisOperation.getIfAvailable()
        if (redis == null) {
            logger.warn("No RedisOperation available, falling back to in-memory active run state store")
            return InMemoryActiveRunStateStore()
        }
        return RedisActiveRunStateStore(
            redisOperation = redis,
            lockTtlSeconds = properties.activeRunTtl.seconds,
            activeTtlSeconds = properties.activeRunTtl.seconds,
        )
    }

    @Bean
    fun activeRunManager(
        stateStore: ActiveRunStateStore,
        agentStateStore: AgentStateStore,
        eventPublisher: org.springframework.context.ApplicationEventPublisher,
    ): ActiveRunManager = ActiveRunManager(stateStore, agentStateStore, eventPublisher)

    companion object {
        private val logger = LoggerFactory.getLogger(ActiveRunConfiguration::class.java)
    }
}
