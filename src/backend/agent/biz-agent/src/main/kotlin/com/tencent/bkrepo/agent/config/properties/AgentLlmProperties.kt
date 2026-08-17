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
 * 业务代码应注入 [EffectiveAgentLlmProperties]。
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
        fun defaults(): EffectiveAgentLlmProperties = AgentLlmPropertiesResolver.resolve(AgentLlmProperties())
    }
}

object AgentLlmPropertiesResolver {

    fun resolve(llm: AgentLlmProperties): EffectiveAgentLlmProperties = EffectiveAgentLlmProperties(
        baseUrl = llm.baseUrl,
        apiKey = llm.apiKey,
        bkAppCode = llm.bkAppCode,
        bkAppSecret = llm.bkAppSecret,
        modelName = llm.modelName,
        reasoningEffort = llm.reasoningEffort,
        stream = llm.stream,
        authMode = resolveAuthMode(llm.bkAppCode, llm.apiKey),
    )

    private fun resolveAuthMode(bkAppCode: String, apiKey: String): AgentLlmAuthMode = when {
        bkAppCode.isNotBlank() -> AgentLlmAuthMode.BK_GATEWAY
        apiKey.isNotBlank() -> AgentLlmAuthMode.API_KEY
        else -> AgentLlmAuthMode.BK_GATEWAY
    }
}
