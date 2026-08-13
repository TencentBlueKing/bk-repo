/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.agent

import com.tencent.bkrepo.agent.config.properties.EffectiveAgentTopology
import io.agentscope.harness.agent.subagent.SubagentDeclaration
import org.springframework.stereotype.Component

@Component
class AgentFactory {

    fun toSubagentDeclaration(
        definition: DomainAgentDefinition,
        binding: EffectiveAgentTopology.AgentBinding,
    ): SubagentDeclaration = SubagentDeclaration.builder()
        .name(definition.agentId)
        .description(definition.description)
        .inlineAgentsBody(definition.sysPrompt)
        .tools(definition.allowedToolNames.toList())
        .steps(binding.maxSteps)
        .maxIters(binding.maxSteps)
        .inheritParentPermissions(true)
        .mode(SubagentDeclaration.Mode.SUBAGENT)
        .persistSession(false)
        .build()
}
