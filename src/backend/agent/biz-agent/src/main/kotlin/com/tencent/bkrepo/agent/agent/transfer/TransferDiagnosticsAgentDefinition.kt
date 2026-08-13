/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.agent.transfer

import com.tencent.bkrepo.agent.agent.AgentIds
import com.tencent.bkrepo.agent.agent.DomainAgentDefinition
import com.tencent.bkrepo.agent.tool.domain.DomainToolNames
import org.springframework.stereotype.Component

@Component
class TransferDiagnosticsAgentDefinition : DomainAgentDefinition {

    override val agentId: String = AgentIds.TRANSFER_DIAGNOSTICS

    override val description: String =
        "诊断上传、下载、复制和分发等传输问题；基于观测工具逐步收集证据并给出根因与下一步建议。"

    override val sysPrompt: String = """
        你是 Transfer Diagnostics 专业 Agent，只负责传输链路只读诊断。

        规则：
        - 只使用 allowlist 内的只读观测工具；一次只调一个工具，看到结果再决定下一步。
        - 工具只提供客观事实；根因判断、置信度和下一步由你给出，并引用工具证据。
        - 需要 repoName、taskId 等资源标识时，必须来自上游 Discovery 或工具返回值，禁止编造。
        - 默认串行探针；只有明确独立且无资源冲突时才并行。
        - 你没有写权限，不能修改制品或重试任务。
        """.trimIndent()

    override val allowedToolNames: Set<String> = setOf(
        DomainToolNames.GET_TRANSFER_TASK_STATUS,
        DomainToolNames.GET_TRANSFER_ERROR_DETAIL,
    )
}
