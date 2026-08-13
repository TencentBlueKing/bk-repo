/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.config

import com.tencent.bkrepo.agent.config.properties.EffectiveAgentMemoryProperties
import io.agentscope.harness.agent.HarnessAgent
import io.agentscope.harness.agent.memory.compaction.CompactionConfig
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig
import org.springframework.stereotype.Component

@Component
class AgentMemoryConfig {

    fun apply(builder: HarnessAgent.Builder, memory: EffectiveAgentMemoryProperties): HarnessAgent.Builder {
        var configured = builder.maxContextTokens(memory.contextWindowSize)
        configured = if (memory.compactionEnabled) {
            configured.compaction(buildCompactionConfig(memory))
        } else {
            configured.disableCompaction()
        }
        return if (memory.toolResultEvictionEnabled) {
            configured.toolResultEviction(ToolResultEvictionConfig.defaults())
        } else {
            configured.disableToolResultEviction()
        }
    }

    private fun buildCompactionConfig(memory: EffectiveAgentMemoryProperties): CompactionConfig {
        val builder = CompactionConfig.builder()
            .flushBeforeCompact(memory.flushBeforeCompact)
            .offloadBeforeCompact(memory.offloadBeforeCompact)
            .reserved(memory.reserved)
        if (memory.triggerMessages > 0) {
            builder.triggerMessages(memory.triggerMessages)
        }
        if (memory.keepMessages > 0) {
            builder.keepMessages(memory.keepMessages)
        }
        return builder.build()
    }
}
