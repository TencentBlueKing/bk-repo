/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.config

import com.tencent.bkrepo.agent.config.properties.AgentCompactionProperties
import com.tencent.bkrepo.agent.config.properties.AgentLlmProperties
import com.tencent.bkrepo.agent.config.properties.AgentLlmPropertiesResolver
import com.tencent.bkrepo.agent.config.properties.AgentMemoryProperties
import com.tencent.bkrepo.agent.config.properties.AgentMemoryPropertiesResolver
import com.tencent.bkrepo.agent.config.properties.AgentModelProperties
import com.tencent.bkrepo.agent.config.properties.AgentProperties
import com.tencent.bkrepo.agent.config.properties.AgentRuntimeProperties
import com.tencent.bkrepo.agent.config.properties.AgentRuntimePropertiesResolver
import com.tencent.bkrepo.agent.config.properties.AgentStateProperties
import com.tencent.bkrepo.agent.config.properties.AgentToolResultEvictionProperties
import com.tencent.bkrepo.agent.config.properties.EffectiveAgentLlmProperties
import com.tencent.bkrepo.agent.config.properties.EffectiveAgentMemoryProperties
import com.tencent.bkrepo.agent.config.properties.EffectiveAgentRuntimeProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    AgentLlmProperties::class,
    AgentMemoryProperties::class,
    AgentRuntimeProperties::class,
    AgentProperties::class,
    AgentModelProperties::class,
    AgentStateProperties::class,
    AgentCompactionProperties::class,
    AgentToolResultEvictionProperties::class,
)
class AgentRuntimeConfig {

    @Bean
    fun effectiveAgentLlmProperties(
        llm: AgentLlmProperties,
        legacyModel: AgentModelProperties,
    ): EffectiveAgentLlmProperties = AgentLlmPropertiesResolver.resolve(llm, legacyModel)

    @Bean
    fun effectiveAgentMemoryProperties(
        memory: AgentMemoryProperties,
        legacyCompaction: AgentCompactionProperties,
        legacyEviction: AgentToolResultEvictionProperties,
        legacyModel: AgentModelProperties,
    ): EffectiveAgentMemoryProperties = AgentMemoryPropertiesResolver.resolve(
        memory = memory,
        legacyCompaction = legacyCompaction,
        legacyEviction = legacyEviction,
        legacyModel = legacyModel,
    )

    @Bean
    fun effectiveAgentRuntimeProperties(
        runtime: AgentRuntimeProperties,
        legacy: AgentProperties,
        legacyState: AgentStateProperties,
    ): EffectiveAgentRuntimeProperties = AgentRuntimePropertiesResolver.resolve(runtime, legacy, legacyState)
}
