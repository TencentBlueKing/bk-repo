/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.runtime

import com.tencent.bkrepo.agent.runtime.store.ActiveRunStateStore
import io.agentscope.core.agent.RuntimeContext
import io.agentscope.core.state.AgentStateStore
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import java.util.concurrent.ConcurrentHashMap

/**
 * 运行态唯一入口：分布式 lock/active/stop + 本机 handle + 早到 stop + AgentState 清理。
 */
class ActiveRunManager(
    private val stateStore: ActiveRunStateStore,
    private val agentStateStore: AgentStateStore,
    private val eventPublisher: ApplicationEventPublisher,
) {

    data class LocalHandle(
        val userId: String,
        val threadId: String,
        val runId: String,
        val runtimeContext: RuntimeContext,
        val abort: () -> Unit,
    )

    private val localHandles = ConcurrentHashMap<String, LocalHandle>()
    private val pendingStopRunIds = ConcurrentHashMap<String, String>()

    fun tryAcquire(scope: ActiveRunScope): Boolean {
        return stateStore.tryAcquireLock(scope.userId, scope.threadId)
    }

    fun isRunning(scope: ActiveRunScope): Boolean {
        return stateStore.isLockHeld(scope.userId, scope.threadId)
    }

    fun bindActiveRun(scope: ActiveRunScope, runId: String) {
        stateStore.bindActiveRun(scope.userId, scope.threadId, runId)
        stateStore.clearStop(runId)
        pendingStopRunIds.remove(scopeKey(scope))
    }

    fun getActiveRunId(scope: ActiveRunScope): String? {
        return stateStore.getActiveRunId(scope.userId, scope.threadId)
    }

    fun releaseRun(scope: ActiveRunScope, runId: String) {
        if (stateStore.getActiveRunId(scope.userId, scope.threadId) != runId) {
            return
        }
        stateStore.clearActiveRun(scope.userId, scope.threadId)
        stateStore.clearStop(runId)
        stateStore.releaseLock(scope.userId, scope.threadId)
        pendingStopRunIds.remove(scopeKey(scope))
    }

    fun requestStop(scope: ActiveRunScope, runId: String) {
        stateStore.requestStop(runId)
        eventPublisher.publishEvent(AgentRunStopBroadcastEvent(scope, runId))
        abortLocalHandle(scope, runId)
        if (!hasLocalHandle(scope, runId)) {
            pendingStopRunIds[scopeKey(scope)] = runId
        }
    }

    fun isStopRequested(runId: String): Boolean {
        return stateStore.isStopRequested(runId)
    }

    fun registerHandle(
        scope: ActiveRunScope,
        runId: String,
        runtimeContext: RuntimeContext,
        abort: () -> Unit,
    ) {
        localHandles[scopeKey(scope)] = LocalHandle(
            userId = scope.userId,
            threadId = scope.threadId,
            runId = runId,
            runtimeContext = runtimeContext,
            abort = abort,
        )
        if (stateStore.isStopRequested(runId) || pendingStopRunIds[scopeKey(scope)] == runId) {
            abort()
        }
    }

    fun removeHandle(scope: ActiveRunScope) {
        localHandles.remove(scopeKey(scope))
    }

    fun clearActiveRunBinding(scope: ActiveRunScope) {
        stateStore.clearActiveRun(scope.userId, scope.threadId)
        pendingStopRunIds.remove(scopeKey(scope))
    }

    fun hasLocalHandle(scope: ActiveRunScope, runId: String): Boolean {
        return localHandles[scopeKey(scope)]?.runId == runId
    }

    fun clearAgentRuntimeState(userId: String, threadId: String) {
        try {
            agentStateStore.delete(userId, threadId)
        } catch (exception: Exception) {
            logger.warn("failed to clear agent runtime state for user[$userId] thread[$threadId]", exception)
        }
    }

    @EventListener
    fun onStopBroadcast(event: AgentRunStopBroadcastEvent) {
        abortLocalHandle(event.scope, event.runId)
    }

    private fun abortLocalHandle(scope: ActiveRunScope, runId: String) {
        localHandles[scopeKey(scope)]
            ?.takeIf { it.runId == runId }
            ?.abort()
    }

    private fun scopeKey(scope: ActiveRunScope): String = "${scope.userId}:${scope.threadId}"

    companion object {
        private val logger = LoggerFactory.getLogger(ActiveRunManager::class.java)
    }
}
