/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.session

import com.tencent.bkrepo.agent.config.properties.EffectiveAgentRuntimeProperties
import io.agentscope.core.agui.AguiException
import io.agentscope.core.agui.processor.AgentResolver
import io.agentscope.core.agui.registry.AguiAgentRegistry

/**
 * 无实例缓存的 [AgentResolver]：会话状态由 [io.agentscope.core.state.AgentStateStore] 承载，
 * 单例 HarnessAgent 即可服务所有 thread。不引入 bk-ci ThreadSessionManager。
 */
class HarnessAgentResolver(
    private val registry: AguiAgentRegistry,
    private val properties: EffectiveAgentRuntimeProperties,
) : AgentResolver {

    override fun resolveAgent(agentId: String, threadId: String?) =
        registry.getAgent(agentId).orElseThrow {
            AguiException.AgentNotFoundException(agentId)
        }

    /**
     * 启用 server-side memory：历史由 AgentStateStore 恢复，请求侧只保留最新 USER 消息或 resume[]。
     */
    override fun hasMemory(threadId: String?): Boolean = !threadId.isNullOrBlank()

    fun defaultAgentId(): String = properties.name
}
