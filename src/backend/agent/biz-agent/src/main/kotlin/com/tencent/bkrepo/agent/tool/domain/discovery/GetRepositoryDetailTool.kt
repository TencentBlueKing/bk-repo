/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.tool.domain.discovery

import com.tencent.bkrepo.agent.tool.domain.AbstractReadOnlyDomainTool
import com.tencent.bkrepo.agent.tool.domain.DomainToolNames
import com.tencent.bkrepo.agent.tool.domain.DomainToolSchemas
import com.tencent.bkrepo.agent.tool.gateway.DomainToolGateway
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import io.agentscope.core.tool.ToolCallParam
import org.springframework.stereotype.Component

@Component
class GetRepositoryDetailTool(
    private val domainToolGateway: DomainToolGateway,
) : AbstractReadOnlyDomainTool(
    name = DomainToolNames.GET_REPOSITORY_DETAIL,
    description = "查询指定仓库详情。参数 repoName 必填；返回 projectId、repoName、type 等结构化字段。",
    inputSchema = DomainToolSchemas.obj(
        "repoName" to DomainToolSchemas.str("仓库名称"),
        required = listOf("repoName"),
    ),
) {
    override fun execute(param: ToolCallParam): String {
        val repoName = param.input["repoName"]?.toString()?.trim().orEmpty()
        require(repoName.isNotEmpty()) { "repoName is required" }
        val projectId = domainToolGateway.currentProjectId(param.runtimeContext)
        domainToolGateway.requireResourcePermission(
            runtimeContext = param.runtimeContext,
            resourceType = ResourceType.REPO,
            action = PermissionAction.READ,
            repoName = repoName,
        )
        return """{"ok":true,"projectId":"$projectId","repoName":"$repoName","note":"repository detail pending backend integration"}"""
    }
}
