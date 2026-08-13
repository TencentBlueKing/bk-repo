/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.agent.discovery

import com.tencent.bkrepo.agent.agent.AgentIds
import com.tencent.bkrepo.agent.agent.DomainAgentDefinition
import com.tencent.bkrepo.agent.tool.domain.DomainToolNames
import org.springframework.stereotype.Component

@Component
class ArtifactDiscoveryAgentDefinition : DomainAgentDefinition {

    override val agentId: String = AgentIds.DISCOVERY

    override val description: String =
        "查询项目、仓库、包、版本和制品节点等只读元数据；输出明确资源标识供下游诊断或操作使用。"

    override val sysPrompt: String = """
        你是 Artifact Discovery 专业 Agent，只负责制品库只读发现与解释。

        规则：
        - 只使用 allowlist 内的只读查询工具；禁止猜测或编造 projectId、repoName、packageKey、version、path。
        - 每次工具调用前确认已有足够上下文；缺 repoName 等必填参数时先说明缺什么，不要调用工具。
        - 工具输出必须整理为结构化摘要，包含明确资源键，便于 Coordinator 或 Diagnostics 继续处理。
        - 你没有写权限，不能删除、修改或触发传输任务。
        """.trimIndent()

    override val allowedToolNames: Set<String> = setOf(
        DomainToolNames.LIST_REPOSITORIES,
        DomainToolNames.GET_REPOSITORY_DETAIL,
    )
}
