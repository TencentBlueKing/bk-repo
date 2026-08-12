/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.agui

import com.tencent.bkrepo.agent.permission.ToolRiskLevel
import com.tencent.bkrepo.agent.tool.local.LocalToolDefinition
import com.tencent.bkrepo.agent.tool.local.LocalToolDefinitions
import io.agentscope.core.agui.model.AguiTool
import org.springframework.stereotype.Component

/** 服务端 authoritative 本地工具目录（allowlist），供 AG-UI frontend tool 注入。 */
@Component
class FrontendToolCatalog {

    private val byName: Map<String, LocalToolDefinition> =
        LocalToolDefinitions.allTools().associateBy { it.name }

    fun allAguiTools(): List<AguiTool> = LocalToolDefinitions.allTools().map { it.toAguiTool() }

    fun find(name: String): LocalToolDefinition? = byName[name]

    fun riskLevel(name: String): ToolRiskLevel? = byName[name]?.riskLevel

    fun isWriteTool(name: String): Boolean {
        val level = riskLevel(name) ?: return false
        return level == ToolRiskLevel.WRITE_REVERSIBLE || level == ToolRiskLevel.WRITE_DESTRUCTIVE
    }

    private fun LocalToolDefinition.toAguiTool(): AguiTool =
        AguiTool(name, description, inputSchema)
}
