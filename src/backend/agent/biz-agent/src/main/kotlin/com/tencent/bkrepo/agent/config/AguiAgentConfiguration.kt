/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.agent.config

import com.tencent.bkrepo.agent.session.HarnessAgentResolver
import com.tencent.bkrepo.agent.config.properties.EffectiveAgentLlmProperties
import com.tencent.bkrepo.agent.config.properties.EffectiveAgentRuntimeProperties
import io.agentscope.core.agui.adapter.AguiAdapterConfig
import io.agentscope.core.agui.model.ToolMergeMode
import io.agentscope.core.agui.processor.AguiRequestProcessor
import io.agentscope.core.agui.registry.AguiAgentRegistry
import io.agentscope.harness.agent.HarnessAgent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class AguiAgentConfiguration {

    @Bean
    fun aguiAgentRegistry(harnessAgent: HarnessAgent, properties: EffectiveAgentRuntimeProperties): AguiAgentRegistry {
        val registry = AguiAgentRegistry()
        registry.register(properties.name, harnessAgent)
        return registry
    }

    @Bean
    fun harnessAgentResolver(
        registry: AguiAgentRegistry,
        properties: EffectiveAgentRuntimeProperties,
    ): HarnessAgentResolver = HarnessAgentResolver(registry, properties)

    /**
     * AG-UI 适配配置。
     *
     * 错误终态：AgentScope 2.0.1 的 [AguiAgentAdapter.errorEvents] 会在 RUN_ERROR 后无条件追加
     * RUN_FINISHED（legacy 行为）。#2646 起可通过 [AguiAdapterConfig.Builder.emitRunFinishedAfterError]
     * 关闭；升级后应显式设为 false。
     */
    @Bean
    fun aguiAdapterConfig(
        properties: EffectiveAgentRuntimeProperties,
        llmProperties: EffectiveAgentLlmProperties,
    ): AguiAdapterConfig {
        // frontend tools 经 SchemaOnlyTool 注册到 toolkit，Coordinator live toolkit 在 HarnessAgent 构建后剥离；
        // AG-UI 使用 AGENT_ONLY，RunAgentInput.tools[] 仅做 allowlist 校验（§17.5 / Phase G-26）。
        val toolMergeMode = ToolMergeMode.AGENT_ONLY
        val enableReasoning = llmProperties.effectiveReasoningEffort() != null
        return AguiAdapterConfig.builder()
            .defaultAgentId(properties.name)
            .runTimeout(properties.sseTimeout)
            .enableReasoning(enableReasoning)
            .emitTokenUsage(false)
            .emitToolCallArgs(true)
            .toolMergeMode(toolMergeMode)
            .build()
    }

    @Bean
    fun aguiRequestProcessor(
        agentResolver: HarnessAgentResolver,
        config: AguiAdapterConfig,
    ): AguiRequestProcessor = AguiRequestProcessor.builder()
        .agentResolver(agentResolver)
        .config(config)
        .build()
}
