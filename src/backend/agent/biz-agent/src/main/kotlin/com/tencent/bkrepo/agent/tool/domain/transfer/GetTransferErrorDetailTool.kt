/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.tool.domain.transfer

import com.tencent.bkrepo.agent.tool.domain.AbstractReadOnlyDomainTool
import com.tencent.bkrepo.agent.tool.domain.DomainToolNames
import com.tencent.bkrepo.agent.tool.domain.DomainToolSchemas
import com.tencent.bkrepo.agent.tool.gateway.DomainToolGateway
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import io.agentscope.core.tool.ToolCallParam
import org.springframework.stereotype.Component

@Component
class GetTransferErrorDetailTool(
    private val domainToolGateway: DomainToolGateway,
) : AbstractReadOnlyDomainTool(
    name = DomainToolNames.GET_TRANSFER_ERROR_DETAIL,
    description = "查询传输任务错误详情。需要 taskId；返回错误码、阶段与可读摘要。",
    inputSchema = DomainToolSchemas.obj(
        "taskId" to DomainToolSchemas.str("传输任务 ID"),
        required = listOf("taskId"),
    ),
) {
    override fun execute(param: ToolCallParam): String {
        val taskId = param.input["taskId"]?.toString()?.trim().orEmpty()
        require(taskId.isNotEmpty()) { "taskId is required" }
        val projectId = domainToolGateway.currentProjectId(param.runtimeContext)
        domainToolGateway.requireResourcePermission(
            runtimeContext = param.runtimeContext,
            resourceType = ResourceType.PROJECT,
            action = PermissionAction.READ,
        )
        return """{"ok":true,"projectId":"$projectId","taskId":"$taskId","errorCode":null,"note":"transfer error detail pending backend integration"}"""
    }
}
