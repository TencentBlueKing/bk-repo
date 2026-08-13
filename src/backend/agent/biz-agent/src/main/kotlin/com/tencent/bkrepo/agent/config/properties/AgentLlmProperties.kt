/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Agent LLM 网关配置（`agent.llm`）。
 *
 * 迁移期仍可从 legacy `agent.model` 回退；业务代码应注入 [EffectiveAgentLlmProperties]。
 */
@ConfigurationProperties("agent.llm")
data class AgentLlmProperties(
    var baseUrl: String = "",
    var apiKey: String = "",
    var bkAppCode: String = "",
    var bkAppSecret: String = "",
    var modelName: String = "",
    var reasoningEffort: String? = null,
    var stream: Boolean = DEFAULT_STREAM,
) {
    override fun toString(): String =
        "AgentLlmProperties(baseUrl=$baseUrl, modelName=$modelName, stream=$stream, " +
            "apiKey=${AgentPropertiesRedaction.redactSecret(apiKey)}, " +
            "bkAppCode=${AgentPropertiesRedaction.redactSecret(bkAppCode)}, " +
            "bkAppSecret=${AgentPropertiesRedaction.redactSecret(bkAppSecret)}, " +
            "reasoningEffort=${reasoningEffort ?: "<unset>"})"

    companion object {
        const val DEFAULT_STREAM = true
    }
}

/**
 * 启动时解析后的 LLM 配置，供 Model Bean 与 AG-UI 层使用。
 */
data class EffectiveAgentLlmProperties(
    val baseUrl: String,
    val apiKey: String,
    val bkAppCode: String,
    val bkAppSecret: String,
    val modelName: String,
    val reasoningEffort: String?,
    val stream: Boolean,
    val authMode: AgentLlmAuthMode,
) {
    fun effectiveReasoningEffort(): String? = reasoningEffort?.takeIf { it.isNotBlank() }

    override fun toString(): String =
        "EffectiveAgentLlmProperties(baseUrl=$baseUrl, modelName=$modelName, authMode=$authMode, " +
            "stream=$stream, apiKey=${AgentPropertiesRedaction.redactSecret(apiKey)}, " +
            "bkAppCode=${AgentPropertiesRedaction.redactSecret(bkAppCode)}, " +
            "bkAppSecret=${AgentPropertiesRedaction.redactSecret(bkAppSecret)}, " +
            "reasoningEffort=${reasoningEffort ?: "<unset>"})"

    companion object {
        fun defaults(): EffectiveAgentLlmProperties = AgentLlmPropertiesResolver.resolve(
            llm = AgentLlmProperties(),
            legacy = AgentModelProperties(),
        )
    }
}

object AgentLlmPropertiesResolver {

    fun resolve(
        llm: AgentLlmProperties,
        legacy: AgentModelProperties,
    ): EffectiveAgentLlmProperties {
        val baseUrl = llm.baseUrl.ifBlank { legacy.baseUrl }
        val apiKey = llm.apiKey.ifBlank { legacy.apiKey }
        val bkAppCode = llm.bkAppCode.ifBlank { legacy.bkAppCode }
        val bkAppSecret = llm.bkAppSecret.ifBlank { legacy.bkAppSecret }
        val modelName = llm.modelName.ifBlank { legacy.modelName }
        val reasoningEffort = llm.reasoningEffort ?: legacy.reasoningEffort
        val stream = if (llm.baseUrl.isNotBlank() || llm.modelName.isNotBlank()) {
            llm.stream
        } else {
            legacy.stream
        }
        val authMode = resolveAuthMode(
            bkAppCode = bkAppCode,
            bkAppSecret = bkAppSecret,
            apiKey = apiKey,
            legacyAuthType = legacy.authType,
            usingNewLlmPrefix = llm.baseUrl.isNotBlank() || llm.modelName.isNotBlank() || llm.bkAppCode.isNotBlank(),
        )
        return EffectiveAgentLlmProperties(
            baseUrl = baseUrl,
            apiKey = apiKey,
            bkAppCode = bkAppCode,
            bkAppSecret = bkAppSecret,
            modelName = modelName,
            reasoningEffort = reasoningEffort,
            stream = stream,
            authMode = authMode,
        )
    }

    fun detectLegacyUsage(
        llm: AgentLlmProperties,
        legacy: AgentModelProperties,
    ): List<AgentLegacyConfigurationUsage> {
        if (llm.baseUrl.isNotBlank() || llm.modelName.isNotBlank()) {
            return emptyList()
        }
        val usages = mutableListOf<AgentLegacyConfigurationUsage>()
        if (legacy.baseUrl.isNotBlank()) {
            usages += AgentLegacyConfigurationUsage("agent.model.base-url", "agent.llm.base-url")
        }
        if (legacy.apiKey.isNotBlank()) {
            usages += AgentLegacyConfigurationUsage("agent.model.api-key", "agent.llm.api-key")
        }
        if (legacy.bkAppCode.isNotBlank()) {
            usages += AgentLegacyConfigurationUsage("agent.model.bk-app-code", "agent.llm.bk-app-code")
        }
        if (legacy.bkAppSecret.isNotBlank()) {
            usages += AgentLegacyConfigurationUsage("agent.model.bk-app-secret", "agent.llm.bk-app-secret")
        }
        if (legacy.modelName.isNotBlank()) {
            usages += AgentLegacyConfigurationUsage("agent.model.model-name", "agent.llm.model-name")
        }
        if (legacy.reasoningEffort != null) {
            usages += AgentLegacyConfigurationUsage("agent.model.reasoning-effort", "agent.llm.reasoning-effort")
        }
        if (legacy.authType != AgentModelAuthType.BK_GATEWAY) {
            usages += AgentLegacyConfigurationUsage("agent.model.auth-type", "agent.llm.bk-app-code (derive auth mode)")
        }
        return usages
    }

    private fun resolveAuthMode(
        bkAppCode: String,
        bkAppSecret: String,
        apiKey: String,
        legacyAuthType: AgentModelAuthType,
        usingNewLlmPrefix: Boolean,
    ): AgentLlmAuthMode = when {
        bkAppCode.isNotBlank() -> AgentLlmAuthMode.BK_GATEWAY
        usingNewLlmPrefix -> AgentLlmAuthMode.API_KEY
        legacyAuthType == AgentModelAuthType.API_KEY -> AgentLlmAuthMode.API_KEY
        legacyAuthType == AgentModelAuthType.BK_GATEWAY && bkAppSecret.isNotBlank() -> AgentLlmAuthMode.BK_GATEWAY
        apiKey.isNotBlank() -> AgentLlmAuthMode.API_KEY
        else -> AgentLlmAuthMode.BK_GATEWAY
    }
}
