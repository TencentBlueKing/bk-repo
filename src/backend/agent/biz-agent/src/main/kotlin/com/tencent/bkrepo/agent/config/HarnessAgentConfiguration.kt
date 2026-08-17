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

import com.tencent.bkrepo.agent.config.properties.EffectiveAgentMemoryProperties
import com.tencent.bkrepo.agent.config.properties.EffectiveAgentRuntimeProperties
import com.tencent.bkrepo.agent.tool.frontend.RegisteredFrontendTools
import io.agentscope.core.model.Model
import io.agentscope.core.permission.PermissionContextState
import io.agentscope.core.state.AgentStateStore
import io.agentscope.core.tool.Toolkit
import io.agentscope.harness.agent.HarnessAgent
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class HarnessAgentConfiguration {

    /**
     * agent 实例在两次调用之间无状态，会话状态由 [AgentStateStore] 按 (userId, sessionId) 寻址，因此单例即可服务并发请求。
     */
    @Bean
    fun harnessAgent(
        properties: EffectiveAgentRuntimeProperties,
        memory: EffectiveAgentMemoryProperties,
        model: Model,
        stateStore: AgentStateStore,
        toolkit: Toolkit,
        permissionContext: PermissionContextState,
        agentHarnessConfigurer: AgentHarnessConfigurer,
        frontendTools: RegisteredFrontendTools,
    ): HarnessAgent {
        val agent = agentHarnessConfigurer.configure(
            properties = properties,
            memory = memory,
            model = model,
            stateStore = stateStore,
            toolkit = toolkit,
            permissionContext = permissionContext,
        )
        if (properties.frontendToolsEnabled && properties.topology.coordinator.enabled) {
            val removed = frontendTools.registeredToolNames.filter { toolkit.getTool(it) != null }
            removed.forEach { toolkit.removeTool(it) }
            logger.info(
                "coordinator live toolkit: removed {} frontend tools (subagent factory retains build-time copy)",
                removed.size,
            )
        }
        val preview = properties.sysPrompt.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        logger.info(
            "HarnessAgent ready: agentId={}, sysPromptChars={}, preview=\"{}\"",
            properties.name,
            properties.sysPrompt.length,
            preview.take(80),
        )
        check(properties.sysPrompt.isNotBlank()) {
            "agent.runtime.sys-prompt must not be blank; check Consul/YAML for empty agent.runtime.sys-prompt override"
        }
        return agent
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HarnessAgentConfiguration::class.java)
    }
}
