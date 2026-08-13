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
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 迁移期一次性启动告警：检测到 legacy 配置前缀时提示对应的新 key。
 */
@Component
class AgentLegacyConfigurationListener(
    private val llm: AgentLlmProperties,
    private val legacyModel: AgentModelProperties,
    private val memory: AgentMemoryProperties,
    private val runtime: AgentRuntimeProperties,
    private val legacy: AgentProperties,
) {

    @EventListener(ApplicationReadyEvent::class)
    fun warnLegacyConfigurationKeys() {
        val warnings = buildList {
            addAll(AgentLlmPropertiesResolver.detectLegacyUsage(llm, legacyModel))
            addAll(AgentMemoryPropertiesResolver.detectLegacyUsage(memory))
            addAll(AgentRuntimePropertiesResolver.detectLegacyUsage(runtime, legacy))
        }
        if (warnings.isEmpty()) {
            return
        }
        logger.warn(
            "Detected legacy agent configuration keys; migrate to new prefixes before the migration window ends:"
        )
        warnings.forEach { usage ->
            logger.warn("  {} -> {}", usage.key, usage.newKey)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AgentLegacyConfigurationListener::class.java)
    }
}
