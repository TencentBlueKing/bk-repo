/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.config.properties

import com.tencent.bkrepo.agent.config.AgentSystemPrompts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Agent 配置绑定")
class AgentPropertiesBindingTest {

    @Test
    fun `agent llm 绑定应解析为 effective llm`() {
        val llm = AgentLlmProperties(
            baseUrl = "https://gateway.example/v1",
            apiKey = "secret-api-key",
            modelName = "qwen-max",
            reasoningEffort = "high",
        )
        val effective = AgentLlmPropertiesResolver.resolve(llm)

        assertEquals("https://gateway.example/v1", effective.baseUrl)
        assertEquals("qwen-max", effective.modelName)
        assertEquals("high", effective.effectiveReasoningEffort())
        assertEquals(AgentLlmAuthMode.API_KEY, effective.authMode)
    }

    @Test
    fun `bkAppCode 非空时应推导为网关认证`() {
        val llm = AgentLlmProperties(
            baseUrl = "https://gateway.example/v1",
            bkAppCode = "bk-repo",
            bkAppSecret = "secret-value",
            modelName = "qwen-max",
        )
        val effective = AgentLlmPropertiesResolver.resolve(llm)

        assertEquals(AgentLlmAuthMode.BK_GATEWAY, effective.authMode)
    }

    @Test
    fun `effective llm toString 应脱敏密钥`() {
        val effective = EffectiveAgentLlmProperties(
            baseUrl = "https://gateway.example/v1",
            apiKey = "super-secret-api-key",
            bkAppCode = "bk-repo",
            bkAppSecret = "super-secret",
            modelName = "qwen-max",
            reasoningEffort = null,
            stream = true,
            authMode = AgentLlmAuthMode.API_KEY,
        )

        val text = effective.toString()
        assertFalse(text.contains("super-secret-api-key"))
        assertFalse(text.contains("super-secret"))
        assertTrue(text.contains("qwen-max"))
    }

    @Test
    fun `agent memory 绑定应解析为 effective memory`() {
        val memory = AgentMemoryPropertiesResolver.resolve(
            AgentMemoryProperties(
                contextWindowSize = 64_000,
                compactionEnabled = false,
                reserved = 15_000,
                toolResultEvictionEnabled = false,
            ),
        )

        assertFalse(memory.compactionEnabled)
        assertFalse(memory.toolResultEvictionEnabled)
        assertEquals(64_000, memory.contextWindowSize)
        assertEquals(15_000, memory.reserved)
    }

    @Test
    fun `agent runtime 绑定应解析为 effective runtime`() {
        val runtime = AgentRuntimePropertiesResolver.resolve(
            AgentRuntimeProperties().apply {
                features = AgentRuntimeProperties.Features(frontendToolsEnabled = false)
                activeRunTtl = java.time.Duration.ofMinutes(15)
                maxMessageLength = 16_000
                state = AgentRuntimeProperties.State(keyPrefix = "bkrepo:agent:custom:")
            },
        )

        assertFalse(runtime.frontendToolsEnabled)
        assertEquals(java.time.Duration.ofMinutes(15), runtime.activeRunTtl)
        assertEquals(16_000, runtime.maxMessageLength)
        assertEquals("bkrepo:agent:custom:", runtime.stateKeyPrefix)
    }

    @Test
    fun `未配置 sys-prompt 时应默认使用 AgentSystemPrompts`() {
        val runtime = AgentRuntimePropertiesResolver.resolve(AgentRuntimeProperties())

        assertEquals(AgentSystemPrompts.DEFAULT, runtime.sysPrompt)
    }

    @Test
    fun `agent runtime sys-prompt 在 Consul 占位为空时应回退代码默认`() {
        val runtime = AgentRuntimePropertiesResolver.resolve(
            AgentRuntimeProperties().apply { sysPrompt = "" },
        )

        assertEquals(AgentSystemPrompts.DEFAULT, runtime.sysPrompt)
    }

    @Test
    fun `agent runtime topology 应透传到 effective topology`() {
        val runtime = AgentRuntimePropertiesResolver.resolve(
            AgentRuntimeProperties().apply {
                topology = AgentRuntimeProperties.Topology(
                    coordinator = AgentRuntimeProperties.Topology.Coordinator(taskListEnabled = false),
                )
            },
        )

        assertFalse(runtime.topology.coordinator.taskListEnabled)
    }
}
