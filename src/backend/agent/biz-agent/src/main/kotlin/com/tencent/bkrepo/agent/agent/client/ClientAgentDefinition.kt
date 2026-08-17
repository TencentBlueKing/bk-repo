/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.agent.client

import com.tencent.bkrepo.agent.agent.AgentIds
import com.tencent.bkrepo.agent.agent.DomainAgentDefinition
import com.tencent.bkrepo.agent.tool.local.LocalToolDefinitions
import org.springframework.stereotype.Component

@Component
class ClientAgentDefinition : DomainAgentDefinition {

    override val agentId: String = AgentIds.CLIENT

    override val description: String =
        "处理用户本机 BKArtifacts 下载客户端任务：aria2 传输列表、磁盘、登录、客户端与引擎日志等。"

    override val sysPrompt: String = ClientAgentPrompt.DEFAULT

    override val allowedToolNames: Set<String> =
        LocalToolDefinitions.allTools().map { it.name }.toSet()
}
