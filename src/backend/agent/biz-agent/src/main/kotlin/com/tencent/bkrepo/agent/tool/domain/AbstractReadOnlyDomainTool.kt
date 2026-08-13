/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.tool.domain

import com.tencent.bkrepo.common.security.exception.PermissionException
import io.agentscope.core.message.TextBlock
import io.agentscope.core.message.ToolResultBlock
import io.agentscope.core.permission.PermissionContextState
import io.agentscope.core.permission.PermissionDecision
import io.agentscope.core.tool.ToolBase
import io.agentscope.core.tool.ToolCallParam
import reactor.core.publisher.Mono

/**
 * 服务端只读领域工具基类：统一 PermissionEngine 放行，IAM 在 [execute] 内执行。
 */
abstract class AbstractReadOnlyDomainTool(
    name: String,
    description: String,
    inputSchema: Map<String, Any>,
) : ToolBase(
    ToolBase.builder()
        .name(name)
        .description(description)
        .inputSchema(inputSchema)
        .readOnly(true)
        .concurrencySafe(true),
) {

    override fun checkPermissions(
        toolInput: MutableMap<String, Any>,
        context: PermissionContextState,
    ): Mono<PermissionDecision> = Mono.just(
        PermissionDecision.allow("Read-only domain tool '$name'"),
    )

    override fun callAsync(param: ToolCallParam): Mono<ToolResultBlock> = Mono.fromCallable {
        try {
            val payload = execute(param)
            ToolResultBlock.of(
                param.toolUseBlock.id,
                name,
                TextBlock.builder().text(payload).build(),
            )
        } catch (ex: PermissionException) {
            ToolResultBlock.error("permission_denied: ${ex.message}")
        } catch (ex: IllegalArgumentException) {
            ToolResultBlock.error("invalid_argument: ${ex.message}")
        }
    }

    protected abstract fun execute(param: ToolCallParam): String
}
