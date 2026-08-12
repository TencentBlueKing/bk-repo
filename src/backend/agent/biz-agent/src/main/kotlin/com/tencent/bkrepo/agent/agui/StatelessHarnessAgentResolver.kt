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

package com.tencent.bkrepo.agent.agui

import com.tencent.bkrepo.agent.config.properties.AgentProperties
import io.agentscope.core.agui.AguiException
import io.agentscope.core.agui.processor.AgentResolver
import io.agentscope.core.agui.registry.AguiAgentRegistry
import io.agentscope.harness.agent.HarnessAgent

/**
 * 无实例缓存的 [AgentResolver]：会话状态由 [io.agentscope.core.state.AgentStateStore] 承载，
 * 单例 [HarnessAgent] 即可服务所有 thread。
 */
class StatelessHarnessAgentResolver(
    private val registry: AguiAgentRegistry,
    private val properties: AgentProperties,
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
