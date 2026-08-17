/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.config

import com.tencent.bkrepo.agent.config.properties.AgentLlmProperties
import com.tencent.bkrepo.agent.config.properties.AgentLlmPropertiesResolver
import com.tencent.bkrepo.agent.config.properties.AgentMemoryProperties
import com.tencent.bkrepo.agent.config.properties.AgentMemoryPropertiesResolver
import com.tencent.bkrepo.agent.config.properties.AgentRuntimeProperties
import com.tencent.bkrepo.agent.config.properties.AgentRuntimePropertiesResolver
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
)
class AgentRuntimeConfig {

    @Bean
    fun effectiveAgentLlmProperties(llm: AgentLlmProperties): EffectiveAgentLlmProperties =
        AgentLlmPropertiesResolver.resolve(llm)

    @Bean
    fun effectiveAgentMemoryProperties(memory: AgentMemoryProperties): EffectiveAgentMemoryProperties =
        AgentMemoryPropertiesResolver.resolve(memory)

    @Bean
    fun effectiveAgentRuntimeProperties(runtime: AgentRuntimeProperties): EffectiveAgentRuntimeProperties =
        AgentRuntimePropertiesResolver.resolve(runtime)
}
