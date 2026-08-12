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
import com.tencent.bkrepo.agent.pojo.AgentSessionCreateResult
import com.tencent.bkrepo.agent.pojo.AgentSessionInfo
import com.tencent.bkrepo.common.api.pojo.Page

/**
 * 会话与消息的业务持久化。
 *
 * 权限边界依赖 [com.tencent.bkrepo.agent.session.AgentSessionStore]（Redis 归属）；
 * 本服务负责 Mongo 元数据、历史归档及删除时的级联清理。
 */
interface AgentSessionService {

    /**
     * 将已生成 [threadId] 的会话元数据写入 Mongo。
     *
     * 调用方须已写入 Redis 归属；返回 DAO insert 结果中的 threadId。
     */
    fun createSessionRecord(
        threadId: String,
        userId: String,
        projectId: String,
    ): AgentSessionCreateResult

    /**
     * 校验会话可被当前用户在本项目下使用。
     *
     * 先查 Mongo 状态与归属，再同步 Redis；若仅有 Redis 记录则补写 Mongo 元数据。
     *
     * @throws com.tencent.bkrepo.common.security.exception.PermissionException 归属不匹配
     * @throws com.tencent.bkrepo.common.api.exception.NotFoundException 会话已删除或不存在
     */
    fun assertActiveSession(userId: String, projectId: String, threadId: String)

    /**
     * 分页列出用户在项目下的 ACTIVE 会话。
     */
    fun listSessions(userId: String, projectId: String, pageNumber: Int, pageSize: Int): Page<AgentSessionInfo>

    /**
     * 分页列出会话消息归档；内部会先 [assertActiveSession]。
     */
    fun listMessages(
        userId: String,
        projectId: String,
        threadId: String,
        pageNumber: Int,
        pageSize: Int,
    ): Page<AgentMessageInfo>

    /**
     * 更新会话标题；内部会先 [assertActiveSession]。
     */
    fun updateTitle(userId: String, projectId: String, threadId: String, title: String)

    /**
     * 删除会话并级联清理 Mongo 消息、Redis 归属与 AgentState。
     */
    fun deleteSession(userId: String, projectId: String, threadId: String)

    /**
     * 记录最近一次 runId，并刷新 [com.tencent.bkrepo.agent.model.TAgentSession.updatedAt] 供列表排序。
     */
    fun touchSession(threadId: String, runId: String)
}
