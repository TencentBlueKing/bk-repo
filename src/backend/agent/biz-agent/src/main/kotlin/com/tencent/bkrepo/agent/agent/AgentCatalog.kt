/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.agent

import com.tencent.bkrepo.agent.config.properties.EffectiveAgentRuntimeProperties
import com.tencent.bkrepo.agent.config.properties.EffectiveAgentTopology
import com.tencent.bkrepo.agent.tool.domain.RegisteredDomainTools
import io.agentscope.harness.agent.subagent.SubagentDeclaration
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AgentCatalog(
    definitions: List<DomainAgentDefinition>,
    private val runtimeProperties: EffectiveAgentRuntimeProperties,
    private val agentFactory: AgentFactory,
    domainToolRegistrar: RegisteredDomainTools,
) {
    private val definitionsById: Map<String, DomainAgentDefinition> = definitions.associateBy { it.agentId }

    init {
        validateDefinitions(definitions, domainToolRegistrar.registeredToolNames)
    }

    @PostConstruct
    fun logTopology() {
        if (!runtimeProperties.topology.coordinator.enabled) {
            logger.info("agent topology: coordinator disabled")
            return
        }
        val enabled = enabledDefinitions().map { it.agentId }
        logger.info(
            "agent topology: coordinator enabled, taskList={}, maxDelegations={}, enabledAgents={}",
            runtimeProperties.topology.coordinator.taskListEnabled,
            runtimeProperties.topology.coordinator.maxDelegations,
            enabled,
        )
    }

    fun enabledDefinitions(): List<DomainAgentDefinition> {
        if (!runtimeProperties.topology.coordinator.enabled) {
            return emptyList()
        }
        return definitionsById.values.filter { bindingFor(it).enabled }
    }

    fun resolveSubagentDeclarations(): List<SubagentDeclaration> =
        enabledDefinitions().map { definition ->
            agentFactory.toSubagentDeclaration(definition, bindingFor(definition))
        }

    fun bindingFor(definition: DomainAgentDefinition): EffectiveAgentTopology.AgentBinding =
        when (definition.agentId) {
            AgentIds.DISCOVERY -> runtimeProperties.topology.agents.discovery
            AgentIds.TRANSFER_DIAGNOSTICS -> runtimeProperties.topology.agents.transferDiagnostics
            else -> error("Unknown agent id: ${definition.agentId}")
        }

    private fun validateDefinitions(
        definitions: List<DomainAgentDefinition>,
        registeredDomainToolNames: Set<String>,
    ) {
        require(definitions.isNotEmpty()) { "At least one DomainAgentDefinition bean is required" }

        val duplicateAgentIds = definitions
            .groupBy { it.agentId }
            .filter { it.value.size > 1 }
            .keys
        require(duplicateAgentIds.isEmpty()) {
            "Duplicate agentId in DomainAgentDefinition beans: $duplicateAgentIds"
        }

        definitions.forEach { definition ->
            require(definition.allowedToolNames.isNotEmpty()) {
                "Agent '${definition.agentId}' must declare a non-empty tool allowlist"
            }
            definition.allowedToolNames.forEach { toolName ->
                require(toolName in registeredDomainToolNames) {
                    "Agent '${definition.agentId}' references unknown tool '$toolName'"
                }
            }
        }

        val toolOwners = definitions.flatMap { definition ->
            definition.allowedToolNames.map { toolName -> toolName to definition.agentId }
        }
        val duplicateTools = toolOwners
            .groupBy({ it.first }, { it.second })
            .filter { it.value.size > 1 }
        require(duplicateTools.isEmpty()) {
            "Tool allowlist must be globally unique across agents: $duplicateTools"
        }

        val topology = runtimeProperties.topology
        if (topology.coordinator.enabled) {
            require(topology.coordinator.maxDelegations > 0) {
                "agent.runtime.topology.coordinator.max-delegations must be positive"
            }
            require(topology.coordinator.maxParallelDelegations > 0) {
                "agent.runtime.topology.coordinator.max-parallel-delegations must be positive"
            }
            require(topology.coordinator.maxParallelDelegations <= topology.coordinator.maxDelegations) {
                "max-parallel-delegations cannot exceed max-delegations"
            }
        }

        definitions.forEach { definition ->
            val binding = bindingFor(definition)
            if (binding.enabled) {
                require(binding.maxSteps > 0) {
                    "Enabled agent '${definition.agentId}' must have positive max-steps"
                }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AgentCatalog::class.java)
    }
}
