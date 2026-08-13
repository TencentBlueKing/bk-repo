/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Harness 记忆与上下文配置（`agent.memory`）。
 *
 * 映射 AgentScope 2.0.1 Compaction 与 ToolResultEviction；不引入 bk-ci AutoContextMemory 算法。
 */
@ConfigurationProperties("agent.memory")
data class AgentMemoryProperties(
    var contextWindowSize: Int = DEFAULT_CONTEXT_WINDOW_SIZE,
    var compactionEnabled: Boolean = DEFAULT_COMPACTION_ENABLED,
    var triggerMessages: Int = AgentCompactionProperties.DEFAULT_TRIGGER_MESSAGES,
    var keepMessages: Int = AgentCompactionProperties.DEFAULT_KEEP_MESSAGES,
    var reserved: Int = AgentCompactionProperties.DEFAULT_RESERVED,
    var flushBeforeCompact: Boolean = AgentCompactionProperties().flushBeforeCompact,
    var offloadBeforeCompact: Boolean = AgentCompactionProperties().offloadBeforeCompact,
    var toolResultEvictionEnabled: Boolean = DEFAULT_TOOL_RESULT_EVICTION_ENABLED,
) {
    companion object {
        const val DEFAULT_CONTEXT_WINDOW_SIZE = AgentModelProperties.DEFAULT_CONTEXT_WINDOW_SIZE
        const val DEFAULT_COMPACTION_ENABLED = true
        const val DEFAULT_TOOL_RESULT_EVICTION_ENABLED = true
    }
}

data class EffectiveAgentMemoryProperties(
    val contextWindowSize: Int,
    val compactionEnabled: Boolean,
    val triggerMessages: Int,
    val keepMessages: Int,
    val reserved: Int,
    val flushBeforeCompact: Boolean,
    val offloadBeforeCompact: Boolean,
    val toolResultEvictionEnabled: Boolean,
) {
    companion object {
        fun defaults(): EffectiveAgentMemoryProperties = AgentMemoryPropertiesResolver.resolve(
            memory = AgentMemoryProperties(),
            legacyCompaction = AgentCompactionProperties(),
            legacyEviction = AgentToolResultEvictionProperties(),
            legacyModel = AgentModelProperties(),
        )
    }
}

object AgentMemoryPropertiesResolver {

    fun resolve(
        memory: AgentMemoryProperties,
        legacyCompaction: AgentCompactionProperties,
        legacyEviction: AgentToolResultEvictionProperties,
        legacyModel: AgentModelProperties,
    ): EffectiveAgentMemoryProperties {
        val usesNewPrefix = memory != AgentMemoryProperties()
        return EffectiveAgentMemoryProperties(
            contextWindowSize = if (usesNewPrefix && memory.contextWindowSize != AgentMemoryProperties.DEFAULT_CONTEXT_WINDOW_SIZE) {
                memory.contextWindowSize
            } else if (legacyModel.contextWindowSize != AgentModelProperties.DEFAULT_CONTEXT_WINDOW_SIZE) {
                legacyModel.contextWindowSize
            } else {
                memory.contextWindowSize
            },
            compactionEnabled = if (usesNewPrefix) memory.compactionEnabled else legacyCompaction.enabled,
            triggerMessages = if (usesNewPrefix) memory.triggerMessages else legacyCompaction.triggerMessages,
            keepMessages = if (usesNewPrefix) memory.keepMessages else legacyCompaction.keepMessages,
            reserved = if (usesNewPrefix) memory.reserved else legacyCompaction.reserved,
            flushBeforeCompact = if (usesNewPrefix) memory.flushBeforeCompact else legacyCompaction.flushBeforeCompact,
            offloadBeforeCompact = if (usesNewPrefix) memory.offloadBeforeCompact else legacyCompaction.offloadBeforeCompact,
            toolResultEvictionEnabled = if (usesNewPrefix) {
                memory.toolResultEvictionEnabled
            } else {
                legacyEviction.enabled
            },
        )
    }

    fun detectLegacyUsage(memory: AgentMemoryProperties): List<AgentLegacyConfigurationUsage> {
        if (memory != AgentMemoryProperties()) {
            return emptyList()
        }
        return listOf(
            AgentLegacyConfigurationUsage("agent.compaction.*", "agent.memory.*"),
            AgentLegacyConfigurationUsage("agent.tool-result-eviction.*", "agent.memory.tool-result-eviction-enabled"),
            AgentLegacyConfigurationUsage("agent.model.context-window-size", "agent.memory.context-window-size"),
        )
    }
}
