/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.service

import com.tencent.bkrepo.agent.pojo.AgentMessageInfo
import com.tencent.bkrepo.agent.pojo.AgentSessionCreateResult
import com.tencent.bkrepo.agent.pojo.AgentSessionDeleteRequest
import com.tencent.bkrepo.agent.pojo.AgentSessionInfo
import com.tencent.bkrepo.agent.pojo.AgentSessionUpdateRequest
import com.tencent.bkrepo.common.api.pojo.Page

/**
 * 会话与消息业务服务。
 *
 * 对外入口校验项目 [project_view] 权限；[userId] 来自已认证请求上下文。
 */
interface AgentSessionService {

    /** 创建新会话（Redis 归属 + Mongo 元数据）。 */
    fun createSession(userId: String, projectId: String): AgentSessionCreateResult

    /** 分页列出用户在项目下的 ACTIVE 会话。 */
    fun listSessions(userId: String, projectId: String, pageNumber: Int, pageSize: Int): Page<AgentSessionInfo>

    /** 分页列出会话消息归档。 */
    fun listMessages(
        userId: String,
        projectId: String,
        threadId: String,
        pageNumber: Int,
        pageSize: Int,
    ): Page<AgentMessageInfo>

    /** 更新会话标题。 */
    fun updateSession(userId: String, projectId: String, request: AgentSessionUpdateRequest)

    /** 删除会话及关联的运行态与持久化数据。 */
    fun deleteSession(userId: String, projectId: String, request: AgentSessionDeleteRequest)

    /**
     * 校验会话可被当前用户在本项目下使用（run 链路内部调用）。
     */
    fun assertActiveSession(userId: String, projectId: String, threadId: String)

    /** 记录最近一次 runId，并刷新会话 updatedAt（run 链路内部调用）。 */
    fun touchSession(threadId: String, runId: String)
}
