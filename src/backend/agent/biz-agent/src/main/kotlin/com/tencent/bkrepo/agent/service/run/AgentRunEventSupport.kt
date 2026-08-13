/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.service.run

import io.agentscope.core.agui.event.AguiEvent

internal object AgentRunEventSupport {

    fun eventType(event: AguiEvent): String = when (event) {
        is AguiEvent.RunStarted -> "RUN_STARTED"
        is AguiEvent.RunFinished -> "RUN_FINISHED"
        is AguiEvent.RunError -> "RUN_ERROR"
        is AguiEvent.TextMessageStart -> "TEXT_MESSAGE_START"
        is AguiEvent.TextMessageContent -> "TEXT_MESSAGE_CONTENT"
        is AguiEvent.TextMessageEnd -> "TEXT_MESSAGE_END"
        is AguiEvent.ToolCallStart -> "TOOL_CALL_START"
        is AguiEvent.ToolCallArgs -> "TOOL_CALL_ARGS"
        is AguiEvent.ToolCallEnd -> "TOOL_CALL_END"
        is AguiEvent.ToolCallResult -> "TOOL_CALL_RESULT"
        is AguiEvent.Interrupt -> "INTERRUPT"
        else -> event.javaClass.simpleName
    }

    fun isTerminal(event: AguiEvent): Boolean = when (event) {
        is AguiEvent.RunFinished, is AguiEvent.RunError -> true
        else -> false
    }
}
