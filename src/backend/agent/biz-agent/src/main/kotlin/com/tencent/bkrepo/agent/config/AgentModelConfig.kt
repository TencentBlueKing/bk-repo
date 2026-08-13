/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.config

import com.tencent.bkrepo.agent.config.properties.AgentLlmAuthMode
import com.tencent.bkrepo.agent.config.properties.EffectiveAgentLlmProperties
import io.agentscope.core.model.GenerateOptions
import io.agentscope.core.model.Model
import io.agentscope.extensions.model.openai.OpenAIChatModel
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class AgentModelConfig {

    @Bean
    fun agentChatModel(properties: EffectiveAgentLlmProperties): Model {
        require(properties.baseUrl.isNotBlank()) { "agent.llm.base-url is required" }
        require(properties.modelName.isNotBlank()) { "agent.llm.model-name is required" }

        val reasoningEffort = properties.effectiveReasoningEffort()
        logger.info(
            "Initializing agent chat model: baseUrl={}, modelName={}, authMode={}, reasoningEffort={}",
            properties.baseUrl,
            properties.modelName,
            properties.authMode,
            reasoningEffort ?: "<unset>",
        )

        val builder = OpenAIChatModel.builder()
            .baseUrl(properties.baseUrl)
            .modelName(properties.modelName)
            .stream(properties.stream)

        when (properties.authMode) {
            AgentLlmAuthMode.BK_GATEWAY -> {
                require(properties.bkAppCode.isNotBlank()) {
                    "agent.llm.bk-app-code is required when using bk gateway auth"
                }
                require(properties.bkAppSecret.isNotBlank()) {
                    "agent.llm.bk-app-secret is required when using bk gateway auth"
                }
                val authJson =
                    """{"bk_app_code":"${properties.bkAppCode}","bk_app_secret":"${properties.bkAppSecret}"}"""
                applyGenerateOptions(builder, gatewayAuthJson = authJson, reasoningEffort = reasoningEffort)
                builder.endpointPath("")
            }
            AgentLlmAuthMode.API_KEY -> {
                require(properties.apiKey.isNotBlank()) {
                    "agent.llm.api-key is required when bk-app-code is empty"
                }
                builder.apiKey(properties.apiKey)
                applyGenerateOptions(builder, reasoningEffort = reasoningEffort)
            }
        }
        return builder.build()
    }

    private fun applyGenerateOptions(
        builder: OpenAIChatModel.Builder,
        gatewayAuthJson: String? = null,
        reasoningEffort: String? = null,
    ) {
        if (gatewayAuthJson == null && reasoningEffort == null) return
        val optionsBuilder = GenerateOptions.builder()
        gatewayAuthJson?.let { optionsBuilder.additionalHeader("X-Bkapi-Authorization", it) }
        reasoningEffort?.let { optionsBuilder.reasoningEffort(it) }
        builder.generateOptions(optionsBuilder.build())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AgentModelConfig::class.java)
    }
}
