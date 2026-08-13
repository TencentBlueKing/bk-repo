/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.service.impl

import com.tencent.bkrepo.agent.pojo.AgentRunReconnectRequest
import com.tencent.bkrepo.agent.pojo.AgentRunStatus
import com.tencent.bkrepo.agent.pojo.AgentRunStatusInfo
import com.tencent.bkrepo.agent.pojo.AgentRunStopRequest
import com.tencent.bkrepo.agent.runtime.ActiveRunManager
import com.tencent.bkrepo.agent.runtime.ActiveRunScope
import com.tencent.bkrepo.agent.service.AgentChatService
import com.tencent.bkrepo.agent.service.AgentRunRecordService
import com.tencent.bkrepo.agent.service.AgentSessionService
import com.tencent.bkrepo.agent.service.run.AgentRunOrchestrator
import com.tencent.bkrepo.agent.service.run.AgentRunReconnectOrchestrator
import com.tencent.bkrepo.agent.session.AgentPendingInterruptStore
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.exception.ParameterInvalidException
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import io.agentscope.core.agui.model.RunAgentInput
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class AgentChatServiceImpl(
    private val permissionManager: PermissionManager,
    private val agentSessionService: AgentSessionService,
    private val agentRunOrchestrator: AgentRunOrchestrator,
    private val agentRunReconnectOrchestrator: AgentRunReconnectOrchestrator,
    private val agentRunRecordService: AgentRunRecordService,
    private val activeRunManager: ActiveRunManager,
    private val pendingInterruptStore: AgentPendingInterruptStore,
) : AgentChatService {

    override fun run(userId: String, projectId: String, input: RunAgentInput): SseEmitter {
        return agentRunOrchestrator.run(userId, projectId, input)
    }

    override fun getRunStatus(userId: String, projectId: String, threadId: String): AgentRunStatusInfo {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        agentSessionService.assertActiveSession(userId, projectId, threadId)
        val scope = ActiveRunScope(userId, projectId, threadId)
        val running = activeRunManager.isRunning(scope)
        val activeRunId = activeRunManager.getActiveRunId(scope)
        val latestRun = agentRunRecordService.findLatestByThreadId(threadId)
        val runId = activeRunId ?: latestRun?.runId
        val status = when {
            running -> AgentRunStatus.RUNNING
            latestRun != null -> latestRun.status
            else -> null
        }
        return AgentRunStatusInfo(
            threadId = threadId,
            runId = runId,
            status = status,
            running = running,
            hasPendingInterrupt = pendingInterruptStore.get(threadId) != null,
        )
    }

    override fun stopRun(userId: String, projectId: String, request: AgentRunStopRequest): Boolean {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        agentSessionService.assertActiveSession(userId, projectId, request.threadId)
        val scope = ActiveRunScope(userId, projectId, request.threadId)
        val activeRunId = activeRunManager.getActiveRunId(scope)
            ?: agentRunRecordService.findLatestByThreadId(request.threadId)
                ?.takeIf { it.status == AgentRunStatus.RUNNING }
                ?.runId
        if (activeRunId == null) {
            return false
        }
        if (request.runId != null && request.runId != activeRunId) {
            throw ParameterInvalidException("runId: run[${request.runId}] is not active")
        }
        activeRunManager.requestStop(scope, activeRunId)
        return true
    }

    override fun reconnectRun(
        userId: String,
        projectId: String,
        request: AgentRunReconnectRequest,
    ): SseEmitter {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        agentSessionService.assertActiveSession(userId, projectId, request.threadId)
        Preconditions.checkNotBlank(request.runId, "runId")
        val existingRun = agentRunRecordService.findByRunId(request.runId)
            ?: throw ParameterInvalidException("runId: run[${request.runId}] not found")
        if (existingRun.threadId != request.threadId) {
            throw ParameterInvalidException("threadId: run does not belong to thread[${request.threadId}]")
        }
        return agentRunReconnectOrchestrator.reconnect(userId, projectId, request, existingRun)
    }
}
