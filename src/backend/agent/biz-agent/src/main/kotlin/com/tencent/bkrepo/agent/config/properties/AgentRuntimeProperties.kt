/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.config.properties

import com.tencent.bkrepo.agent.agent.bkrepo.BkrepoAssistantPrompt
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Agent 运行时配置（`agent.runtime`）。
 *
 * 收敛 run 生命周期、输入限制、state 与 feature 开关；迁移期仍可从 legacy `agent.*` 回退。
 */
@ConfigurationProperties("agent.runtime")
data class AgentRuntimeProperties(
    var name: String = AgentProperties.DEFAULT_NAME,
    var sysPrompt: String = BkrepoAssistantPrompt.DEFAULT,
    var maxIters: Int = AgentProperties.DEFAULT_MAX_ITERS,
    var workspace: String = AgentProperties.DEFAULT_WORKSPACE,
    var sseTimeout: Duration = AgentProperties.DEFAULT_SSE_TIMEOUT,
    var maxMessageLength: Int = AgentProperties.DEFAULT_MAX_MESSAGE_LENGTH,
    var maxThreadIdLength: Int = AgentProperties.DEFAULT_MAX_THREAD_ID_LENGTH,
    var sessionTtl: Duration = AgentProperties.DEFAULT_SESSION_TTL,
    var activeRunTtl: Duration = AgentProperties.DEFAULT_RUN_LOCK_TTL,
    var runEventTtl: Duration = AgentProperties.DEFAULT_RUN_EVENT_TTL,
    var reconnectPollInterval: Duration = AgentProperties.DEFAULT_RECONNECT_POLL_INTERVAL,
    var reconnectTimeout: Duration = AgentProperties.DEFAULT_RECONNECT_TIMEOUT,
    var state: State = State(),
    var features: Features = Features(),
    var topology: Topology = Topology(),
) {
    data class State(
        var keyPrefix: String = AgentStateProperties.DEFAULT_KEY_PREFIX,
        var requireRedis: Boolean = DEFAULT_REQUIRE_REDIS,
    )

    data class Features(
        var frontendToolsEnabled: Boolean = AgentProperties.DEFAULT_LOCAL_TOOLS_ENABLED,
    )

    data class Topology(
        var coordinator: Coordinator = Coordinator(),
        var agents: Agents = Agents(),
    ) {
        data class Coordinator(
            var enabled: Boolean = true,
            var maxDelegations: Int = DEFAULT_MAX_DELEGATIONS,
            var maxParallelDelegations: Int = DEFAULT_MAX_PARALLEL_DELEGATIONS,
            var taskListEnabled: Boolean = true,
        )

        data class Agents(
            var discovery: AgentBinding = AgentBinding(enabled = true),
            var transferDiagnostics: AgentBinding = AgentBinding(enabled = false, maxSteps = 10),
        )

        data class AgentBinding(
            var enabled: Boolean = false,
            var modelProfile: String = DEFAULT_MODEL_PROFILE,
            var maxSteps: Int = DEFAULT_MAX_STEPS,
        )
    }

    companion object {
        const val DEFAULT_REQUIRE_REDIS = false
        const val DEFAULT_MAX_DELEGATIONS = 8
        const val DEFAULT_MAX_PARALLEL_DELEGATIONS = 1
        const val DEFAULT_MODEL_PROFILE = "default"
        const val DEFAULT_MAX_STEPS = 8
    }
}

data class EffectiveAgentTopology(
    val coordinator: Coordinator,
    val agents: Agents,
) {
    data class Coordinator(
        val enabled: Boolean,
        val maxDelegations: Int,
        val maxParallelDelegations: Int,
        val taskListEnabled: Boolean,
    )

    data class AgentBinding(
        val enabled: Boolean,
        val modelProfile: String,
        val maxSteps: Int,
    )

    data class Agents(
        val discovery: AgentBinding,
        val transferDiagnostics: AgentBinding,
    )

    companion object {
        fun from(topology: AgentRuntimeProperties.Topology): EffectiveAgentTopology = EffectiveAgentTopology(
            coordinator = Coordinator(
                enabled = topology.coordinator.enabled,
                maxDelegations = topology.coordinator.maxDelegations,
                maxParallelDelegations = topology.coordinator.maxParallelDelegations,
                taskListEnabled = topology.coordinator.taskListEnabled,
            ),
            agents = Agents(
                discovery = AgentBinding(
                    enabled = topology.agents.discovery.enabled,
                    modelProfile = topology.agents.discovery.modelProfile,
                    maxSteps = topology.agents.discovery.maxSteps,
                ),
                transferDiagnostics = AgentBinding(
                    enabled = topology.agents.transferDiagnostics.enabled,
                    modelProfile = topology.agents.transferDiagnostics.modelProfile,
                    maxSteps = topology.agents.transferDiagnostics.maxSteps,
                ),
            ),
        )

        fun defaults(): EffectiveAgentTopology = from(AgentRuntimeProperties.Topology())
    }
}

data class EffectiveAgentRuntimeProperties(
    val name: String,
    val sysPrompt: String,
    val maxIters: Int,
    val workspace: String,
    val sseTimeout: Duration,
    val maxMessageLength: Int,
    val maxThreadIdLength: Int,
    val sessionTtl: Duration,
    val activeRunTtl: Duration,
    val runEventTtl: Duration,
    val reconnectPollInterval: Duration,
    val reconnectTimeout: Duration,
    val stateKeyPrefix: String,
    val requireRedis: Boolean,
    val frontendToolsEnabled: Boolean,
    val topology: EffectiveAgentTopology,
) {
    companion object {
        fun defaults(): EffectiveAgentRuntimeProperties = AgentRuntimePropertiesResolver.resolve(
            runtime = AgentRuntimeProperties(),
            legacy = AgentProperties(),
            legacyState = AgentStateProperties(),
        )
    }
}

object AgentRuntimePropertiesResolver {

    fun resolve(
        runtime: AgentRuntimeProperties,
        legacy: AgentProperties,
        legacyState: AgentStateProperties,
    ): EffectiveAgentRuntimeProperties {
        val usesNewPrefix = runtime != defaultRuntime()
        return EffectiveAgentRuntimeProperties(
            name = pick(runtime.name, legacy.name, usesNewPrefix, AgentProperties.DEFAULT_NAME),
            sysPrompt = pick(runtime.sysPrompt, legacy.sysPrompt, usesNewPrefix, BkrepoAssistantPrompt.DEFAULT),
            maxIters = pick(runtime.maxIters, legacy.maxIters, usesNewPrefix, AgentProperties.DEFAULT_MAX_ITERS),
            workspace = pick(runtime.workspace, legacy.workspace, usesNewPrefix, AgentProperties.DEFAULT_WORKSPACE),
            sseTimeout = pickDuration(runtime.sseTimeout, legacy.sseTimeout, usesNewPrefix, AgentProperties.DEFAULT_SSE_TIMEOUT),
            maxMessageLength = pick(
                runtime.maxMessageLength,
                legacy.maxMessageLength,
                usesNewPrefix,
                AgentProperties.DEFAULT_MAX_MESSAGE_LENGTH,
            ),
            maxThreadIdLength = pick(
                runtime.maxThreadIdLength,
                legacy.maxThreadIdLength,
                usesNewPrefix,
                AgentProperties.DEFAULT_MAX_THREAD_ID_LENGTH,
            ),
            sessionTtl = pickDuration(runtime.sessionTtl, legacy.sessionTtl, usesNewPrefix, AgentProperties.DEFAULT_SESSION_TTL),
            activeRunTtl = if (usesNewPrefix) {
                runtime.activeRunTtl
            } else {
                legacy.runLockTtl
            },
            runEventTtl = pickDuration(runtime.runEventTtl, legacy.runEventTtl, usesNewPrefix, AgentProperties.DEFAULT_RUN_EVENT_TTL),
            reconnectPollInterval = pickDuration(
                runtime.reconnectPollInterval,
                legacy.reconnectPollInterval,
                usesNewPrefix,
                AgentProperties.DEFAULT_RECONNECT_POLL_INTERVAL,
            ),
            reconnectTimeout = pickDuration(
                runtime.reconnectTimeout,
                legacy.reconnectTimeout,
                usesNewPrefix,
                AgentProperties.DEFAULT_RECONNECT_TIMEOUT,
            ),
            stateKeyPrefix = if (usesNewPrefix && runtime.state.keyPrefix != AgentStateProperties.DEFAULT_KEY_PREFIX) {
                runtime.state.keyPrefix
            } else {
                legacyState.keyPrefix
            },
            requireRedis = if (usesNewPrefix) runtime.state.requireRedis else AgentRuntimeProperties.DEFAULT_REQUIRE_REDIS,
            frontendToolsEnabled = if (usesNewPrefix) {
                runtime.features.frontendToolsEnabled
            } else {
                legacy.localToolsEnabled
            },
            topology = if (usesNewPrefix) {
                EffectiveAgentTopology.from(runtime.topology)
            } else {
                EffectiveAgentTopology.defaults()
            },
        )
    }

    fun detectLegacyUsage(runtime: AgentRuntimeProperties, legacy: AgentProperties): List<AgentLegacyConfigurationUsage> {
        if (runtime != defaultRuntime()) {
            return emptyList()
        }
        val usages = mutableListOf<AgentLegacyConfigurationUsage>()
        if (legacy.name != AgentProperties.DEFAULT_NAME) {
            usages += AgentLegacyConfigurationUsage("agent.name", "agent.runtime.name")
        }
        if (legacy.workspace != AgentProperties.DEFAULT_WORKSPACE) {
            usages += AgentLegacyConfigurationUsage("agent.workspace", "agent.runtime.workspace")
        }
        if (legacy.maxIters != AgentProperties.DEFAULT_MAX_ITERS) {
            usages += AgentLegacyConfigurationUsage("agent.max-iters", "agent.runtime.max-iters")
        }
        if (legacy.runLockTtl != AgentProperties.DEFAULT_RUN_LOCK_TTL) {
            usages += AgentLegacyConfigurationUsage("agent.run-lock-ttl", "agent.runtime.active-run-ttl")
        }
        if (legacy.localToolsEnabled != AgentProperties.DEFAULT_LOCAL_TOOLS_ENABLED) {
            usages += AgentLegacyConfigurationUsage(
                "agent.local-tools-enabled",
                "agent.runtime.features.frontend-tools-enabled",
            )
        }
        if (legacy.maxMessageLength != AgentProperties.DEFAULT_MAX_MESSAGE_LENGTH) {
            usages += AgentLegacyConfigurationUsage("agent.max-message-length", "agent.runtime.max-message-length")
        }
        if (legacy.reconnectPollInterval != AgentProperties.DEFAULT_RECONNECT_POLL_INTERVAL) {
            usages += AgentLegacyConfigurationUsage(
                "agent.reconnect-poll-interval",
                "agent.runtime.reconnect-poll-interval",
            )
        }
        return usages
    }

    private fun defaultRuntime(): AgentRuntimeProperties = AgentRuntimeProperties()

    private fun <T> pick(newValue: T, legacyValue: T, usesNewPrefix: Boolean, defaultValue: T): T =
        if (usesNewPrefix && newValue != defaultValue) newValue else legacyValue

    private fun pickDuration(newValue: Duration, legacyValue: Duration, usesNewPrefix: Boolean, defaultValue: Duration): Duration =
        if (usesNewPrefix && newValue != defaultValue) newValue else legacyValue
}
