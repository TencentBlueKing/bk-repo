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

package com.tencent.bkrepo.agent.service.impl

import com.tencent.bkrepo.agent.config.properties.AgentProperties
import com.tencent.bkrepo.agent.dao.AgentMessageDao
import com.tencent.bkrepo.agent.dao.AgentSessionDao
import com.tencent.bkrepo.agent.model.TAgentSession
import com.tencent.bkrepo.agent.pojo.AgentMessageInfo
import com.tencent.bkrepo.agent.pojo.AgentSessionInfo
import com.tencent.bkrepo.agent.pojo.AgentSessionStatus
import com.tencent.bkrepo.agent.service.AgentSessionService
import com.tencent.bkrepo.agent.session.AgentRuntimeStateCleaner
import com.tencent.bkrepo.agent.session.AgentSessionStore
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.security.exception.PermissionException
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class AgentSessionServiceImpl(
    private val agentSessionDao: AgentSessionDao,
    private val agentMessageDao: AgentMessageDao,
    private val agentSessionStore: AgentSessionStore,
    private val agentRuntimeStateCleaner: AgentRuntimeStateCleaner,
    private val properties: AgentProperties,
) : AgentSessionService {

    override fun createSessionRecord(
        sessionId: String,
        userId: String,
        projectId: String,
        deviceId: String?,
    ): AgentSessionInfo {
        val now = LocalDateTime.now()
        val session = TAgentSession(
            sessionId = sessionId,
            userId = userId,
            projectId = projectId,
            title = null,
            status = AgentSessionStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
        )
        agentSessionDao.insert(session)
        return toInfo(session, deviceId)
    }

    override fun assertActiveSession(userId: String, projectId: String, sessionId: String) {
        agentSessionStore.assertSessionOwner(userId, projectId, sessionId)
        val session = agentSessionDao.findBySessionId(sessionId)
            ?: backfillSessionRecord(userId, projectId, sessionId)
        if (session.userId != userId || session.projectId != projectId) {
            throw PermissionException("Session[$sessionId] does not belong to user[$userId] in project[$projectId]")
        }
        if (session.status != AgentSessionStatus.ACTIVE) {
            throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, "Session[$sessionId]")
        }
    }

    override fun listSessions(
        userId: String,
        projectId: String,
        pageNumber: Int,
        pageSize: Int,
    ): Page<AgentSessionInfo> {
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val records = agentSessionDao.listByUserAndProject(
            userId = userId,
            projectId = projectId,
            pageNumber = pageRequest.pageNumber + 1,
            pageSize = pageRequest.pageSize,
        )
        val total = agentSessionDao.countByUserAndProject(userId, projectId)
        return Pages.ofResponse(pageRequest, total, records.map { toInfo(it, deviceId = null) })
    }

    override fun listMessages(
        userId: String,
        projectId: String,
        sessionId: String,
        pageNumber: Int,
        pageSize: Int,
    ): Page<AgentMessageInfo> {
        assertActiveSession(userId, projectId, sessionId)
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val records = agentMessageDao.listBySessionId(
            sessionId = sessionId,
            pageNumber = pageRequest.pageNumber + 1,
            pageSize = pageRequest.pageSize,
        )
        val total = agentMessageDao.countBySessionId(sessionId)
        return Pages.ofResponse(pageRequest, total, records.map { toMessageInfo(it) })
    }

    override fun updateTitle(userId: String, projectId: String, sessionId: String, title: String) {
        assertActiveSession(userId, projectId, sessionId)
        val normalizedTitle = title.trim()
        Preconditions.checkNotBlank(normalizedTitle, "title")
        Preconditions.checkArgument(normalizedTitle.length <= properties.maxMessageLength, "title")
        agentSessionDao.updateTitle(sessionId, normalizedTitle, LocalDateTime.now())
    }

    override fun deleteSession(userId: String, projectId: String, sessionId: String) {
        assertActiveSession(userId, projectId, sessionId)
        val now = LocalDateTime.now()
        agentSessionDao.markDeleted(sessionId, now)
        agentMessageDao.removeBySessionId(sessionId)
        agentSessionStore.removeSession(projectId, sessionId)
        agentRuntimeStateCleaner.clear(userId, sessionId)
    }

    override fun touchSession(sessionId: String, runId: String) {
        agentSessionDao.touchSession(sessionId, runId, LocalDateTime.now())
    }

    private fun backfillSessionRecord(userId: String, projectId: String, sessionId: String): TAgentSession {
        val now = LocalDateTime.now()
        val session = TAgentSession(
            sessionId = sessionId,
            userId = userId,
            projectId = projectId,
            status = AgentSessionStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
        )
        agentSessionDao.insert(session)
        return session
    }

    private fun toInfo(session: TAgentSession, deviceId: String?): AgentSessionInfo {
        return AgentSessionInfo(
            sessionId = session.sessionId,
            userId = session.userId,
            projectId = session.projectId,
            deviceId = deviceId,
            title = session.title,
            status = session.status,
            createdDate = session.createdAt,
            updatedDate = session.updatedAt,
        )
    }

    private fun toMessageInfo(message: com.tencent.bkrepo.agent.model.TAgentMessage): AgentMessageInfo {
        return AgentMessageInfo(
            messageId = message.messageId,
            sessionId = message.sessionId,
            runId = message.runId,
            role = message.role,
            content = message.content,
            agentId = message.agentId,
            toolCallId = message.toolCallId,
            createdDate = message.createdAt,
        )
    }
}
