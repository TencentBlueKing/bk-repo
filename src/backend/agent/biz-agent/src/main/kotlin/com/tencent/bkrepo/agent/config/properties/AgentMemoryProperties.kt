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
    var triggerMessages: Int = DEFAULT_TRIGGER_MESSAGES,
    var keepMessages: Int = DEFAULT_KEEP_MESSAGES,
    var reserved: Int = DEFAULT_RESERVED,
    var flushBeforeCompact: Boolean = DEFAULT_FLUSH_BEFORE_COMPACT,
    var offloadBeforeCompact: Boolean = DEFAULT_OFFLOAD_BEFORE_COMPACT,
    var toolResultEvictionEnabled: Boolean = DEFAULT_TOOL_RESULT_EVICTION_ENABLED,
) {
    companion object {
        const val DEFAULT_CONTEXT_WINDOW_SIZE = 128_000
        const val DEFAULT_COMPACTION_ENABLED = true
        const val DEFAULT_TOOL_RESULT_EVICTION_ENABLED = true
        const val DEFAULT_TRIGGER_MESSAGES = 0
        const val DEFAULT_KEEP_MESSAGES = 0
        const val DEFAULT_RESERVED = 20_000
        const val DEFAULT_FLUSH_BEFORE_COMPACT = false
        const val DEFAULT_OFFLOAD_BEFORE_COMPACT = false
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
        fun defaults(): EffectiveAgentMemoryProperties = AgentMemoryPropertiesResolver.resolve(AgentMemoryProperties())
    }
}

object AgentMemoryPropertiesResolver {

    fun resolve(memory: AgentMemoryProperties): EffectiveAgentMemoryProperties = EffectiveAgentMemoryProperties(
        contextWindowSize = memory.contextWindowSize,
        compactionEnabled = memory.compactionEnabled,
        triggerMessages = memory.triggerMessages,
        keepMessages = memory.keepMessages,
        reserved = memory.reserved,
        flushBeforeCompact = memory.flushBeforeCompact,
        offloadBeforeCompact = memory.offloadBeforeCompact,
        toolResultEvictionEnabled = memory.toolResultEvictionEnabled,
    )
}
