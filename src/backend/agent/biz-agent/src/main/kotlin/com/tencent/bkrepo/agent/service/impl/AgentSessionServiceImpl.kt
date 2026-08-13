/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.service.impl

import com.tencent.bkrepo.agent.config.properties.EffectiveAgentRuntimeProperties
import com.tencent.bkrepo.agent.constant.AGENT_THREAD_ID_PREFIX
import com.tencent.bkrepo.agent.dao.AgentMessageDao
import com.tencent.bkrepo.agent.dao.AgentSessionDao
import com.tencent.bkrepo.agent.model.TAgentMessage
import com.tencent.bkrepo.agent.model.TAgentSession
import com.tencent.bkrepo.agent.pojo.AgentMessageInfo
import com.tencent.bkrepo.agent.pojo.AgentSessionCreateResult
import com.tencent.bkrepo.agent.pojo.AgentSessionDeleteRequest
import com.tencent.bkrepo.agent.pojo.AgentSessionInfo
import com.tencent.bkrepo.agent.pojo.AgentSessionStatus
import com.tencent.bkrepo.agent.pojo.AgentSessionUpdateRequest
import com.tencent.bkrepo.agent.runtime.ActiveRunManager
import com.tencent.bkrepo.agent.runtime.ActiveRunScope
import com.tencent.bkrepo.agent.service.AgentRunRecordService
import com.tencent.bkrepo.agent.service.AgentSessionService
import com.tencent.bkrepo.agent.session.AgentPendingInterruptStore
import com.tencent.bkrepo.agent.session.AgentSessionStore
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.exception.TooManyRequestsException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.security.exception.PermissionException
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class AgentSessionServiceImpl(
    private val permissionManager: PermissionManager,
    private val agentSessionDao: AgentSessionDao,
    private val agentMessageDao: AgentMessageDao,
    private val agentRunRecordService: AgentRunRecordService,
    private val agentSessionStore: AgentSessionStore,
    private val activeRunManager: ActiveRunManager,
    private val pendingInterruptStore: AgentPendingInterruptStore,
    private val properties: EffectiveAgentRuntimeProperties,
) : AgentSessionService {

    override fun createSession(userId: String, projectId: String): AgentSessionCreateResult {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        val threadId = AGENT_THREAD_ID_PREFIX + StringPool.uniqueId()
        agentSessionStore.bindSession(userId, projectId, threadId)
        return try {
            insertSessionRecord(threadId, userId, projectId)
        } catch (ex: Exception) {
            agentSessionStore.removeSession(projectId, threadId)
            throw ex
        }
    }

    override fun listSessions(
        userId: String,
        projectId: String,
        pageNumber: Int,
        pageSize: Int,
    ): Page<AgentSessionInfo> {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        val page = agentSessionDao.pageByUserAndProject(userId, projectId, pageNumber, pageSize)
        return Page(page.pageNumber, page.pageSize, page.totalRecords, page.records.map { toInfo(it) })
    }

    override fun listMessages(
        userId: String,
        projectId: String,
        threadId: String,
        pageNumber: Int,
        pageSize: Int,
    ): Page<AgentMessageInfo> {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        assertActiveSession(userId, projectId, threadId)
        val page = agentMessageDao.pageByThreadId(threadId, pageNumber, pageSize)
        return Page(page.pageNumber, page.pageSize, page.totalRecords, page.records.map { toMessageInfo(it) })
    }

    override fun updateSession(userId: String, projectId: String, request: AgentSessionUpdateRequest) {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        assertActiveSession(userId, projectId, request.threadId)
        val normalizedTitle = request.title.trim()
        Preconditions.checkNotBlank(normalizedTitle, "title")
        Preconditions.checkArgument(normalizedTitle.length <= properties.maxMessageLength, "title")
        agentSessionDao.updateTitle(request.threadId, normalizedTitle, LocalDateTime.now())
    }

    override fun deleteSession(userId: String, projectId: String, request: AgentSessionDeleteRequest) {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        deleteSessionInternal(userId, projectId, request.threadId)
    }

    override fun assertActiveSession(userId: String, projectId: String, threadId: String) {
        val session = agentSessionDao.findByThreadId(threadId)
            ?: agentSessionDao.insertSession(threadId, userId, projectId)
        if (session.status != AgentSessionStatus.ACTIVE) {
            throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, "Thread[$threadId]")
        }
        if (session.userId != userId || session.projectId != projectId) {
            throw PermissionException("Thread[$threadId] does not belong to user[$userId] in project[$projectId]")
        }
        agentSessionStore.bindSession(userId, projectId, threadId)
    }

    override fun touchSession(threadId: String, runId: String) {
        agentSessionDao.touchSession(threadId, runId, LocalDateTime.now())
    }

    private fun insertSessionRecord(
        threadId: String,
        userId: String,
        projectId: String,
    ): AgentSessionCreateResult {
        val session = agentSessionDao.insertSession(threadId, userId, projectId)
        if (session.status != AgentSessionStatus.ACTIVE) {
            throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, "Thread[$threadId]")
        }
        return AgentSessionCreateResult(
            threadId = session.threadId,
            title = session.title,
            createdAt = session.createdAt,
        )
    }

    private fun deleteSessionInternal(userId: String, projectId: String, threadId: String) {
        assertActiveSession(userId, projectId, threadId)
        if (activeRunManager.isRunning(ActiveRunScope(userId, projectId, threadId))) {
            throw TooManyRequestsException("Thread[$threadId] is running")
        }
        pendingInterruptStore.clear(threadId)
        activeRunManager.clearActiveRunBinding(ActiveRunScope(userId, projectId, threadId))
        val now = LocalDateTime.now()
        agentSessionDao.markDeleted(threadId, now)
        agentMessageDao.removeByThreadId(threadId)
        agentRunRecordService.removeByThreadId(threadId)
        agentSessionStore.removeSession(projectId, threadId)
        activeRunManager.clearAgentRuntimeState(userId, threadId)
    }

    private fun toInfo(session: TAgentSession): AgentSessionInfo {
        return AgentSessionInfo(
            threadId = session.threadId,
            userId = session.userId,
            projectId = session.projectId,
            title = session.title,
            status = session.status,
            createdDate = session.createdAt,
            updatedDate = session.updatedAt,
        )
    }

    private fun toMessageInfo(message: TAgentMessage): AgentMessageInfo {
        return AgentMessageInfo(
            messageId = message.messageId,
            threadId = message.threadId,
            runId = message.runId,
            role = message.role,
            content = message.content,
            agentId = message.agentId,
            toolCallId = message.toolCallId,
            createdDate = message.createdAt,
        )
    }
}
