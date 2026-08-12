/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.agui

import com.tencent.bkrepo.agent.config.properties.AgentProperties
import com.tencent.bkrepo.common.api.exception.ParameterInvalidException
import io.agentscope.core.agui.model.AguiTool
import io.agentscope.core.agui.model.RunAgentInput
import org.springframework.stereotype.Component

/**
 * 校验并替换客户端 [RunAgentInput.tools]：仅保留 allowlist 内工具，且 schema 一律以服务端目录为准。
 */
@Component
class FrontendToolSanitizer(
    private val properties: AgentProperties,
    private val catalog: FrontendToolCatalog,
) {

    fun sanitize(input: RunAgentInput): RunAgentInput {
        if (!properties.localToolsEnabled) {
            if (input.hasTools()) {
                throw ParameterInvalidException("tools: local tools are disabled")
            }
            return sanitizeForwardedProps(input)
        }

        val sanitizedTools = resolveTools(input.tools)
        val withTools = if (sanitizedTools == input.tools) {
            input
        } else {
            RunAgentInput.builder()
                .threadId(input.threadId)
                .runId(input.runId)
                .messages(input.messages)
                .tools(sanitizedTools)
                .context(input.context)
                .state(input.state)
                .forwardedProps(input.forwardedProps)
                .resume(input.resume)
                .build()
        }
        return sanitizeForwardedProps(withTools)
    }

    private fun sanitizeForwardedProps(input: RunAgentInput): RunAgentInput {
        val sanitizedProps = AgentForwardedPropsSupport.sanitizeForwardedProps(input.forwardedProps)
        if (sanitizedProps == input.forwardedProps) {
            return input
        }
        return RunAgentInput.builder()
            .threadId(input.threadId)
            .runId(input.runId)
            .messages(input.messages)
            .tools(input.tools)
            .context(input.context)
            .state(input.state)
            .forwardedProps(sanitizedProps)
            .resume(input.resume)
            .build()
    }

    private fun resolveTools(clientTools: List<AguiTool>): List<AguiTool> {
        if (clientTools.isEmpty()) {
            return catalog.allAguiTools()
        }
        val requestedNames = linkedSetOf<String>()
        for (tool in clientTools) {
            val name = tool.name?.trim().orEmpty()
            if (name.isBlank()) {
                throw ParameterInvalidException("tools: tool name is blank")
            }
            if (!requestedNames.add(name)) {
                throw ParameterInvalidException("tools: duplicate tool name[$name]")
            }
            if (catalog.find(name) == null) {
                throw ParameterInvalidException("tools: tool[$name] is not allowed")
            }
        }
        return requestedNames.mapNotNull { catalog.find(it)?.toAguiTool() }
    }

    private fun com.tencent.bkrepo.agent.tool.local.LocalToolDefinition.toAguiTool(): AguiTool =
        AguiTool(name, description, inputSchema)
}
