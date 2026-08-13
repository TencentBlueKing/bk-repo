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
class ListRepositoriesTool(
    private val domainToolGateway: DomainToolGateway,
) : AbstractReadOnlyDomainTool(
    name = DomainToolNames.LIST_REPOSITORIES,
    description = "列出当前项目下用户有权访问的制品仓库。返回 projectId 与 repoName 列表。",
    inputSchema = DomainToolSchemas.NO_ARGS,
) {
    override fun execute(param: ToolCallParam): String {
        val projectId = domainToolGateway.currentProjectId(param.runtimeContext)
        domainToolGateway.requireResourcePermission(
            runtimeContext = param.runtimeContext,
            resourceType = ResourceType.PROJECT,
            action = PermissionAction.READ,
        )
        // Phase F 占位：后续接入 RepositoryService 真实查询
        return """{"ok":true,"projectId":"$projectId","repositories":[],"note":"repository listing pending backend integration"}"""
    }
}
