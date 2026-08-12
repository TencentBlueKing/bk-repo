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

package com.tencent.bkrepo.agent.service

import com.tencent.bkrepo.agent.pojo.AgentMessageInfo
import io.agentscope.core.agui.model.RunAgentInput
import com.tencent.bkrepo.agent.pojo.AgentSessionDeleteRequest
import com.tencent.bkrepo.agent.pojo.AgentSessionCreateResult
import com.tencent.bkrepo.agent.pojo.AgentSessionInfo
import com.tencent.bkrepo.agent.pojo.AgentSessionUpdateRequest
import com.tencent.bkrepo.common.api.pojo.Page
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * Agent 对外业务能力：会话生命周期与对话 run。
 *
 * 入口均校验项目 [project_view] 权限；[userId] 来自已认证请求上下文，不可由客户端指定。
 */
interface AgentRunService {

    /**
     * 创建归属于 [userId] 与 [projectId] 的新会话。
     *
     * 写入 Redis 归属映射与 Mongo 会话元数据，返回 [AgentSessionCreateResult.sessionId] 供后续 run 使用。
     */
    fun createSession(userId: String, projectId: String): AgentSessionCreateResult

    /**
     * 执行一轮 AG-UI run，将官方 [io.agentscope.core.agui.event.AguiEvent] 编码为 SSE 推送。
     */
    fun run(
        userId: String,
        projectId: String,
        deviceId: String?,
        traceId: String?,
        input: RunAgentInput,
    ): SseEmitter

    /**
     * 分页查询当前用户在指定项目下的活跃会话列表，按最近更新时间倒序。
     */
    fun listSessions(userId: String, projectId: String, pageNumber: Int, pageSize: Int): Page<AgentSessionInfo>

    /**
     * 分页查询指定会话的消息历史（Mongo 归档原文），按创建时间正序。
     */
    fun listMessages(
        userId: String,
        projectId: String,
        sessionId: String,
        pageNumber: Int,
        pageSize: Int,
    ): Page<AgentMessageInfo>

    /**
     * 更新会话标题；会话须处于 ACTIVE 且归属当前用户。
     */
    fun updateSessionTitle(userId: String, projectId: String, request: AgentSessionUpdateRequest)

    /**
     * 删除会话：软删 Mongo 元数据、清除消息归档、Redis 归属与 AgentState 运行时状态。
     */
    fun deleteSession(userId: String, projectId: String, request: AgentSessionDeleteRequest)
}
