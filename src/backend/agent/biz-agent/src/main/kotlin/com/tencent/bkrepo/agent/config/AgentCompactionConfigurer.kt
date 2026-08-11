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

package com.tencent.bkrepo.agent.config

import com.tencent.bkrepo.agent.config.properties.AgentCompactionProperties
import com.tencent.bkrepo.agent.config.properties.AgentModelProperties
import com.tencent.bkrepo.agent.config.properties.AgentToolResultEvictionProperties
import io.agentscope.harness.agent.HarnessAgent
import io.agentscope.harness.agent.memory.compaction.CompactionConfig
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig
import org.springframework.stereotype.Component

@Component
class AgentCompactionConfigurer(
    private val compactionProperties: AgentCompactionProperties,
    private val toolResultEvictionProperties: AgentToolResultEvictionProperties,
    private val modelProperties: AgentModelProperties,
) {

    fun apply(builder: HarnessAgent.Builder): HarnessAgent.Builder {
        var configured = builder.maxContextTokens(modelProperties.contextWindowSize)
        configured = if (compactionProperties.enabled) {
            configured.compaction(buildCompactionConfig())
        } else {
            configured.disableCompaction()
        }
        return if (toolResultEvictionProperties.enabled) {
            configured.toolResultEviction(ToolResultEvictionConfig.defaults())
        } else {
            configured.disableToolResultEviction()
        }
    }

    private fun buildCompactionConfig(): CompactionConfig {
        val builder = CompactionConfig.builder()
            .flushBeforeCompact(compactionProperties.flushBeforeCompact)
            .offloadBeforeCompact(compactionProperties.offloadBeforeCompact)
            .reserved(compactionProperties.reserved)
        if (compactionProperties.triggerMessages > 0) {
            builder.triggerMessages(compactionProperties.triggerMessages)
        }
        if (compactionProperties.keepMessages > 0) {
            builder.keepMessages(compactionProperties.keepMessages)
        }
        return builder.build()
    }
}
