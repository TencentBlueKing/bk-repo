/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.service.run

import com.tencent.bkrepo.agent.agui.AguiMessageArchiveHandler
import com.tencent.bkrepo.agent.hitl.AguiInterruptTracker
import com.tencent.bkrepo.agent.hitl.AguiResumeValidator
import com.tencent.bkrepo.agent.tool.frontend.FrontendToolSanitizer
import com.tencent.bkrepo.agent.context.AgentChatContext
import com.tencent.bkrepo.agent.context.AgentChatContextResolver
import com.tencent.bkrepo.agent.config.properties.EffectiveAgentRuntimeProperties
import com.tencent.bkrepo.agent.constant.AGENT_RUN_ID_PREFIX
import com.tencent.bkrepo.agent.model.TAgentRun
import com.tencent.bkrepo.agent.pojo.AgentRunStatus
import com.tencent.bkrepo.agent.pojo.AgentRunTriggerType
import com.tencent.bkrepo.agent.service.AgentRunRecordService
import com.tencent.bkrepo.agent.service.AgentSessionService
import com.tencent.bkrepo.agent.runtime.ActiveRunManager
import com.tencent.bkrepo.agent.runtime.ActiveRunScope
import com.tencent.bkrepo.agent.runtime.AgentRunReplaySinkRegistry
import com.tencent.bkrepo.agent.session.AgentSessionStore
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.TooManyRequestsException
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import io.agentscope.core.agui.model.RunAgentInput
import io.agentscope.core.agui.processor.AguiRequestProcessor
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * AG-UI run 编排：校验 → 幂等 → 加锁 → 启动记录 → 订阅事件流。
 *
 * 每个步骤方法保持短小，复杂收尾逻辑委托 [AgentRunLifecycleManager]。
 */
@Component
class AgentRunOrchestrator(
    private val properties: EffectiveAgentRuntimeProperties,
    private val permissionManager: PermissionManager,
    private val inputValidator: RunAgentInputValidator,
    private val aguiResumeValidator: AguiResumeValidator,
    private val frontendToolSanitizer: FrontendToolSanitizer,
    private val agentSessionService: AgentSessionService,
    private val agentRunRecordService: AgentRunRecordService,
    private val activeRunManager: ActiveRunManager,
    private val replaySinkRegistry: AgentRunReplaySinkRegistry,
    private val agentSessionStore: AgentSessionStore,
    private val agentChatContextResolver: AgentChatContextResolver,
    private val aguiRequestProcessor: AguiRequestProcessor,
    private val messageArchiveHandler: AguiMessageArchiveHandler,
    private val agentRunStreamOrchestrator: AgentRunStreamOrchestrator,
    private val lifecycleManager: AgentRunLifecycleManager,
    private val eventPipeline: AgentRunEventPipeline,
) {

    fun run(userId: String, projectId: String, input: RunAgentInput): SseEmitter {
        val prepared = prepareInput(userId, projectId, input)
        resolveExistingRun(prepared.input)?.let { return it }
        val runScope = ActiveRunScope(userId, projectId, prepared.threadId)
        acquireRunLock(runScope)
        val scope = startRun(userId, projectId, prepared, runScope)
        return attachEventStream(scope)
    }

    fun replayTerminal(input: RunAgentInput, existingRun: TAgentRun): SseEmitter {
        return agentRunStreamOrchestrator.replayTerminalForRun(input, existingRun)
    }

    private data class PreparedInput(
        val input: RunAgentInput,
        val threadId: String,
        val runId: String,
    )

    private fun prepareInput(userId: String, projectId: String, input: RunAgentInput): PreparedInput {
        inputValidator.validate(input)
        aguiResumeValidator.validateAndPrepare(userId, projectId, input)
        val processed = frontendToolSanitizer.sanitize(input)
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId, userId)
        agentSessionService.assertActiveSession(userId, projectId, processed.threadId)
        return PreparedInput(processed, processed.threadId, processed.runId)
    }

    private fun resolveExistingRun(input: RunAgentInput): SseEmitter? {
        val existingRun = agentRunRecordService.findByRunId(input.runId) ?: return null
        return when (existingRun.status) {
            AgentRunStatus.RUNNING -> throw TooManyRequestsException("Run[${input.runId}] is already running")
            else -> agentRunStreamOrchestrator.replayTerminalForRun(input, existingRun)
        }
    }

    private fun acquireRunLock(scope: ActiveRunScope) {
        if (!activeRunManager.tryAcquire(scope)) {
            throw TooManyRequestsException("Session[${scope.threadId}] is already running")
        }
    }

    private fun startRun(
        userId: String,
        projectId: String,
        prepared: PreparedInput,
        runScope: ActiveRunScope,
    ): AgentRunScope {
        val executionId = AGENT_RUN_ID_PREFIX + StringPool.uniqueId()
        val triggerType = if (prepared.input.hasResume()) {
            AgentRunTriggerType.AGUI_RESUME
        } else {
            AgentRunTriggerType.USER_INPUT
        }
        val chatContext = agentChatContextResolver.resolve(userId, projectId, prepared.input, executionId)
        recordRunStart(
            userId,
            projectId,
            prepared,
            executionId,
            chatContext.deviceId,
            triggerType,
            runScope,
        )
        return buildRunScope(prepared, chatContext)
    }

    private fun recordRunStart(
        userId: String,
        projectId: String,
        prepared: PreparedInput,
        executionId: String,
        deviceId: String?,
        triggerType: AgentRunTriggerType,
        runScope: ActiveRunScope,
    ) {
        agentRunRecordService.startRun(
            runId = prepared.runId,
            executionId = executionId,
            threadId = prepared.threadId,
            userId = userId,
            projectId = projectId,
            deviceId = deviceId,
            entryAgentId = properties.name,
            triggerType = triggerType,
        )
        agentSessionService.touchSession(prepared.threadId, prepared.runId)
        agentSessionStore.touchSessionOwner(userId, projectId, prepared.threadId)
        activeRunManager.bindActiveRun(runScope, prepared.runId)
        replaySinkRegistry.open(prepared.runId)
    }

    private fun buildRunScope(
        prepared: PreparedInput,
        chatContext: AgentChatContext,
    ): AgentRunScope {
        val archiveState = AguiMessageArchiveHandler.State()
        messageArchiveHandler.archiveIncomingUserMessages(prepared.input, prepared.threadId, prepared.runId)
        val interruptState = AguiInterruptTracker.State()
        val runtimeContext = chatContext.toRuntimeContext()
        val processResult = aguiRequestProcessor.process(prepared.input, null, null, runtimeContext)
        val emitter = SseEmitter(properties.sseTimeout.toMillis())
        return AgentRunScope(
            userId = chatContext.userId,
            projectId = chatContext.projectId,
            input = prepared.input,
            threadId = prepared.threadId,
            runId = prepared.runId,
            executionId = chatContext.executionId,
            runtimeContext = runtimeContext,
            eventFlux = processResult.events(),
            archiveState = archiveState,
            interruptState = interruptState,
            emitter = emitter,
        )
    }

    private fun attachEventStream(scope: AgentRunScope): SseEmitter {
        lifecycleManager.registerHandle(scope)
        lifecycleManager.attachEmitterCallbacks(scope)
        eventPipeline.subscribe(scope)
        return scope.emitter
    }
}
