/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.agent

import com.tencent.bkrepo.agent.agent.discovery.ArtifactDiscoveryAgentDefinition
import com.tencent.bkrepo.agent.agent.transfer.TransferDiagnosticsAgentDefinition
import com.tencent.bkrepo.agent.config.properties.EffectiveAgentRuntimeProperties
import com.tencent.bkrepo.agent.config.properties.EffectiveAgentTopology
import com.tencent.bkrepo.agent.tool.domain.DomainToolNames
import com.tencent.bkrepo.agent.tool.domain.RegisteredDomainTools
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("AgentCatalog 拓扑校验")
class AgentCatalogTest {

    private val discovery = ArtifactDiscoveryAgentDefinition()
    private val transfer = TransferDiagnosticsAgentDefinition()
    private val registeredTools = setOf(
        DomainToolNames.LIST_REPOSITORIES,
        DomainToolNames.GET_REPOSITORY_DETAIL,
        DomainToolNames.GET_TRANSFER_TASK_STATUS,
        DomainToolNames.GET_TRANSFER_ERROR_DETAIL,
    )

    @Test
    fun `默认拓扑应启用 discovery 并禁用 transfer-diagnostics`() {
        val catalog = catalog(
            definitions = listOf(discovery, transfer),
            topology = EffectiveAgentTopology.defaults(),
        )

        assertEquals(listOf(AgentIds.DISCOVERY), catalog.enabledDefinitions().map { it.agentId })
        assertEquals(1, catalog.resolveSubagentDeclarations().size)
    }

    @Test
    fun `重复 agentId 应启动失败`() {
        assertThrows(IllegalArgumentException::class.java) {
            catalog(
                definitions = listOf(discovery, discovery),
                topology = EffectiveAgentTopology.defaults(),
            )
        }
    }

    @Test
    fun `空工具 allowlist 应启动失败`() {
        val emptyTools = object : DomainAgentDefinition {
            override val agentId = AgentIds.DISCOVERY
            override val description = "bad"
            override val sysPrompt = "bad"
            override val allowedToolNames: Set<String> = emptySet()
        }
        assertThrows(IllegalArgumentException::class.java) {
            catalog(definitions = listOf(emptyTools), topology = EffectiveAgentTopology.defaults())
        }
    }

    @Test
    fun `跨 Agent 重复工具名应启动失败`() {
        val overlapping = object : DomainAgentDefinition {
            override val agentId = AgentIds.TRANSFER_DIAGNOSTICS
            override val description = "bad"
            override val sysPrompt = "bad"
            override val allowedToolNames = setOf(DomainToolNames.LIST_REPOSITORIES)
        }
        assertThrows(IllegalArgumentException::class.java) {
            catalog(definitions = listOf(discovery, overlapping), topology = EffectiveAgentTopology.defaults())
        }
    }

    @Test
    fun `引用未注册工具应启动失败`() {
        val unknownTool = object : DomainAgentDefinition {
            override val agentId = AgentIds.DISCOVERY
            override val description = "bad"
            override val sysPrompt = "bad"
            override val allowedToolNames = setOf("unknown_tool")
        }
        assertThrows(IllegalArgumentException::class.java) {
            catalog(definitions = listOf(unknownTool), topology = EffectiveAgentTopology.defaults())
        }
    }

    @Test
    fun `启用 transfer-diagnostics 时应出现在拓扑中`() {
        val topology = EffectiveAgentTopology.defaults().copy(
            agents = EffectiveAgentTopology.defaults().agents.copy(
                transferDiagnostics = EffectiveAgentTopology.AgentBinding(
                    enabled = true,
                    modelProfile = "default",
                    maxSteps = 10,
                ),
            ),
        )
        val catalog = catalog(definitions = listOf(discovery, transfer), topology = topology)
        val enabledIds = catalog.enabledDefinitions().map { it.agentId }
        assertTrue(enabledIds.contains(AgentIds.DISCOVERY))
        assertTrue(enabledIds.contains(AgentIds.TRANSFER_DIAGNOSTICS))
    }

    private fun catalog(
        definitions: List<DomainAgentDefinition>,
        topology: EffectiveAgentTopology,
    ): AgentCatalog {
        val runtime = EffectiveAgentRuntimeProperties.defaults().copy(topology = topology)
        return AgentCatalog(
            definitions = definitions,
            runtimeProperties = runtime,
            agentFactory = AgentFactory(),
            domainToolRegistrar = StubDomainToolRegistrar(registeredTools),
        )
    }

    private class StubDomainToolRegistrar(
        override val registeredToolNames: Set<String>,
    ) : RegisteredDomainTools
}
