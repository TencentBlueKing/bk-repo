/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.service.impl

import com.tencent.bkrepo.agent.config.properties.EffectiveAgentRuntimeProperties
import com.tencent.bkrepo.agent.dao.AgentRunEventDao
import com.tencent.bkrepo.agent.model.TAgentRunEvent
import com.tencent.bkrepo.agent.runtime.AgentRunReplaySinkRegistry
import com.tencent.bkrepo.agent.service.AgentRunEventService
import com.tencent.bkrepo.agent.service.run.AgentRunEventSupport
import com.tencent.bkrepo.agent.service.run.AgentRunScope
import com.tencent.bkrepo.agent.service.run.AguiSseEventWriter
import io.agentscope.core.agui.event.AguiEvent
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant
import java.time.LocalDateTime

@Service
class AgentRunEventServiceImpl(
    private val properties: EffectiveAgentRuntimeProperties,
    private val runEventDao: AgentRunEventDao,
    private val sseEventWriter: AguiSseEventWriter,
    private val replaySinkRegistry: AgentRunReplaySinkRegistry,
) : AgentRunEventService {

    override fun append(scope: AgentRunScope, event: AguiEvent): Long {
        val eventIndex = scope.nextEventIndex()
        val now = LocalDateTime.now()
        val eventJson = sseEventWriter.encode(event)
        val terminal = AgentRunEventSupport.isTerminal(event)
        val record = TAgentRunEvent(
            userId = scope.userId,
            projectId = scope.projectId,
            threadId = scope.threadId,
            runId = scope.runId,
            agentId = properties.name,
            agentExecutionId = scope.executionId,
            eventIndex = eventIndex,
            eventType = AgentRunEventSupport.eventType(event),
            eventData = eventJson,
            terminal = terminal,
            createdAt = now,
            expiresAt = Instant.now().plus(properties.runEventTtl),
        )
        runEventDao.insertIfAbsent(record)
        replaySinkRegistry.publish(
            scope.runId,
            AgentRunReplaySinkRegistry.ReplayEvent(
                eventIndex = eventIndex,
                eventJson = eventJson,
                terminal = terminal,
            ),
        )
        return eventIndex
    }

    override fun hasEvents(runId: String): Boolean {
        return runEventDao.maxEventIndex(runId) != null
    }

    override fun listEvents(runId: String): List<TAgentRunEvent> {
        return runEventDao.listByRunId(runId)
    }

    override fun listAfterIndex(runId: String, afterIndex: Long): List<TAgentRunEvent> {
        return runEventDao.listByRunIdAfterIndex(runId, afterIndex)
    }

    override fun replayTo(emitter: SseEmitter, runId: String, afterIndex: Long): Long {
        val events = if (afterIndex < 0) {
            runEventDao.listByRunId(runId)
        } else {
            runEventDao.listByRunIdAfterIndex(runId, afterIndex)
        }
        events.forEach { sseEventWriter.sendJson(emitter, it.eventData) }
        return events.lastOrNull()?.eventIndex ?: afterIndex
    }

    override fun closeRun(runId: String) {
        replaySinkRegistry.close(runId)
    }

    override fun cleanup(runId: String) {
        replaySinkRegistry.close(runId)
        runEventDao.removeByRunId(runId)
    }
}
