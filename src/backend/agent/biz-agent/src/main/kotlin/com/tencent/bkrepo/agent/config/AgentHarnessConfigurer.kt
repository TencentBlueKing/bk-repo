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

import com.tencent.bkrepo.agent.agent.AgentCatalog
import com.tencent.bkrepo.agent.config.properties.EffectiveAgentMemoryProperties
import com.tencent.bkrepo.agent.config.properties.EffectiveAgentRuntimeProperties
import io.agentscope.core.model.Model
import io.agentscope.core.permission.PermissionContextState
import io.agentscope.core.state.AgentStateStore
import io.agentscope.core.tool.Toolkit
import io.agentscope.harness.agent.HarnessAgent
import org.springframework.stereotype.Component
import java.nio.file.Paths

/**
 * 集中装配 [HarnessAgent]，显式固定自定义 Middleware 与框架内置 Middleware 的相对顺序。
 *
 * 消息归档已迁移至 AG-UI 事件层（[com.tencent.bkrepo.agent.agui.AguiMessageArchiveHandler]），
 * 不再经由 AgentScope Middleware 双写。
 */
@Component
class AgentHarnessConfigurer(
    private val agentMemoryConfig: AgentMemoryConfig,
    private val agentCatalog: AgentCatalog,
) {

    fun configure(
        properties: EffectiveAgentRuntimeProperties,
        memory: EffectiveAgentMemoryProperties,
        model: Model,
        stateStore: AgentStateStore,
        toolkit: Toolkit,
        permissionContext: PermissionContextState,
    ): HarnessAgent {
        var builder = HarnessAgent.builder()
            .name(properties.name)
            .sysPrompt(properties.sysPrompt)
            .model(model)
            .maxIters(properties.maxIters)
            .stateStore(stateStore)
            .workspace(Paths.get(properties.workspace))
            .toolkit(toolkit)
            .permissionContext(permissionContext)
            .enablePendingToolRecovery(true)
            .disableFilesystemTools()
            .disableShellTool()
            .disableDynamicSkills()
            .disableDynamicSubagents()
            .disableMemoryTools()
            .disableWorkspaceContext()

        if (properties.topology.coordinator.enabled) {
            builder = builder.enableTaskList(properties.topology.coordinator.taskListEnabled)
            val subagents = agentCatalog.resolveSubagentDeclarations()
            if (subagents.isNotEmpty()) {
                builder = builder.subagents(subagents)
            }
        } else {
            builder = builder.disableSubagents()
        }

        builder = agentMemoryConfig.apply(builder, memory)

        return builder.build()
    }
}
