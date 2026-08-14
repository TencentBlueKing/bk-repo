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

@DisplayName("Agent 配置绑定与迁移")
class AgentPropertiesBindingTest {

    @Test
    fun `legacy model 应单向迁移到 effective llm`() {
        val legacy = AgentModelProperties(
            baseUrl = "https://gateway.example/v1",
            apiKey = "secret-api-key",
            modelName = "qwen-max",
            reasoningEffort = "high",
        )
        val effective = AgentLlmPropertiesResolver.resolve(AgentLlmProperties(), legacy)

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
        val effective = AgentLlmPropertiesResolver.resolve(llm, AgentModelProperties())

        assertEquals(AgentLlmAuthMode.BK_GATEWAY, effective.authMode)
    }

    @Test
    fun `agent llm 优先于 legacy model`() {
        val llm = AgentLlmProperties(
            baseUrl = "https://new.example/v1",
            modelName = "new-model",
        )
        val legacy = AgentModelProperties(
            baseUrl = "https://old.example/v1",
            modelName = "old-model",
        )
        val effective = AgentLlmPropertiesResolver.resolve(llm, legacy)

        assertEquals("https://new.example/v1", effective.baseUrl)
        assertEquals("new-model", effective.modelName)
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
    fun `legacy compaction 应迁移到 effective memory`() {
        val memory = AgentMemoryPropertiesResolver.resolve(
            memory = AgentMemoryProperties(),
            legacyCompaction = AgentCompactionProperties(enabled = false, reserved = 15_000),
            legacyEviction = AgentToolResultEvictionProperties(enabled = false),
            legacyModel = AgentModelProperties(contextWindowSize = 64_000),
        )

        assertFalse(memory.compactionEnabled)
        assertFalse(memory.toolResultEvictionEnabled)
        assertEquals(64_000, memory.contextWindowSize)
        assertEquals(15_000, memory.reserved)
    }

    @Test
    fun `legacy agent 应迁移到 effective runtime`() {
        val runtime = AgentRuntimePropertiesResolver.resolve(
            runtime = AgentRuntimeProperties(),
            legacy = AgentProperties().apply {
                localToolsEnabled = false
                runLockTtl = java.time.Duration.ofMinutes(15)
                maxMessageLength = 16_000
            },
            legacyState = AgentStateProperties(keyPrefix = "bkrepo:agent:custom:"),
        )

        assertFalse(runtime.frontendToolsEnabled)
        assertEquals(java.time.Duration.ofMinutes(15), runtime.activeRunTtl)
        assertEquals(16_000, runtime.maxMessageLength)
        assertEquals("bkrepo:agent:custom:", runtime.stateKeyPrefix)
    }

    @Test
    fun `未配置 agent runtime 时应默认使用 AgentSystemPrompts`() {
        val runtime = AgentRuntimePropertiesResolver.resolve(
            runtime = AgentRuntimeProperties(),
            legacy = AgentProperties(),
            legacyState = AgentStateProperties(),
        )

        assertEquals(AgentSystemPrompts.DEFAULT, runtime.sysPrompt)
    }

    @Test
    fun `agent runtime sys-prompt 等于代码默认值时仍应生效而非回退 legacy 空串`() {
        val runtime = AgentRuntimePropertiesResolver.resolve(
            runtime = AgentRuntimeProperties().apply {
                topology = AgentRuntimeProperties.Topology(
                    coordinator = AgentRuntimeProperties.Topology.Coordinator(taskListEnabled = false),
                )
            },
            legacy = AgentProperties().apply { sysPrompt = "" },
            legacyState = AgentStateProperties(),
        )

        assertEquals(AgentSystemPrompts.DEFAULT, runtime.sysPrompt)
    }

    @Test
    fun `agent runtime sys-prompt 在 Consul 占位为空时应回退代码默认`() {
        val runtime = AgentRuntimePropertiesResolver.resolve(
            runtime = AgentRuntimeProperties().apply { sysPrompt = "" },
            legacy = AgentProperties(),
            legacyState = AgentStateProperties(),
        )

        assertEquals(AgentSystemPrompts.DEFAULT, runtime.sysPrompt)
    }

    @Test
    fun `legacy agent sys-prompt 显式配置时应优先于新默认`() {
        val runtime = AgentRuntimePropertiesResolver.resolve(
            runtime = AgentRuntimeProperties(),
            legacy = AgentProperties().apply { sysPrompt = "legacy-custom-prompt" },
            legacyState = AgentStateProperties(),
        )

        assertEquals("legacy-custom-prompt", runtime.sysPrompt)
    }
}
