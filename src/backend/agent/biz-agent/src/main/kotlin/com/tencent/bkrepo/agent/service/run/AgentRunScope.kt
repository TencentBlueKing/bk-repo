/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.service.run

import com.tencent.bkrepo.agent.hitl.AguiInterruptTracker
import com.tencent.bkrepo.agent.agui.AguiMessageArchiveHandler
import com.tencent.bkrepo.agent.pojo.AgentRunStatus
import io.agentscope.core.agui.event.AguiEvent
import io.agentscope.core.agui.model.RunAgentInput
import io.agentscope.core.agent.RuntimeContext
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import reactor.core.Disposable
import reactor.core.publisher.Flux
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** 单次 AG-UI run 的内存上下文，供编排、事件管道与生命周期组件共享。 */
class AgentRunScope(
    val userId: String,
    val projectId: String,
    val input: RunAgentInput,
    val threadId: String,
    val runId: String,
    val executionId: String,
    val runtimeContext: RuntimeContext,
    val eventFlux: Flux<AguiEvent>,
    val archiveState: AguiMessageArchiveHandler.State,
    val interruptState: AguiInterruptTracker.State,
    val emitter: SseEmitter,
    val terminalStatus: AtomicReference<AgentRunStatus> = AtomicReference(AgentRunStatus.COMPLETED),
    val runFinished: AtomicBoolean = AtomicBoolean(false),
    val primaryClientDisconnected: AtomicBoolean = AtomicBoolean(false),
) {
    private val eventIndexSeq = AtomicLong(0)

    val subscriptionRef = AtomicReference<Disposable>()

    fun nextEventIndex(): Long = eventIndexSeq.getAndIncrement()
}
