/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.service

import com.tencent.bkrepo.agent.pojo.AgentRunReconnectRequest
import com.tencent.bkrepo.agent.pojo.AgentRunStatusInfo
import com.tencent.bkrepo.agent.pojo.AgentRunStopRequest
import io.agentscope.core.agui.model.RunAgentInput
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * AG-UI 对话 run 业务编排。
 *
 * 负责 run、status、stop 与 stream/reconnect 的业务入口；不直接操作 Redis、Mongo 或 SSE 编码细节。
 * [userId] 来自已认证请求上下文，[projectId] 为 bk-repo 权限域。
 */
interface AgentChatService {

    /** 执行一轮 AG-UI run，将 [io.agentscope.core.agui.event.AguiEvent] 编码为 SSE 推送。 */
    fun run(userId: String, projectId: String, input: RunAgentInput): SseEmitter

    /** 查询 thread 当前 run 状态（active run、持久化终态与 pending interrupt）。 */
    fun getRunStatus(userId: String, projectId: String, threadId: String): AgentRunStatusInfo

    /** 请求停止 active run；跨副本通过 stop 广播 + 本机 handle 中断。 */
    fun stopRun(userId: String, projectId: String, request: AgentRunStopRequest): Boolean

    /**
     * 衔接活跃 run 事件流或从 Mongo 增量/全量重放 AG-UI 事件；不重新执行 Agent。
     */
    fun streamRun(
        userId: String,
        projectId: String,
        threadId: String,
        runId: String? = null,
        lastEventIndex: Long? = null,
    ): SseEmitter

    @Deprecated("Use streamRun", ReplaceWith("streamRun(userId, projectId, request.threadId, request.runId, request.lastEventIndex)"))
    fun reconnectRun(userId: String, projectId: String, request: AgentRunReconnectRequest): SseEmitter
}
