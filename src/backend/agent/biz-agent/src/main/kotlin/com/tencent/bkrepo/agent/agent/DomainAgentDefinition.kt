/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.agent

/**
 * 专业 Agent 业务元数据：只描述 bk-repo 领域职责与安全上限，不复制 AgentScope 运行协议。
 */
interface DomainAgentDefinition {
    val agentId: String
    val description: String
    val sysPrompt: String
    val allowedToolNames: Set<String>
}
