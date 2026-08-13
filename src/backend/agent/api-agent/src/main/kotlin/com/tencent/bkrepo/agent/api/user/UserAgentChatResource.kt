/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.api.user

import com.tencent.bkrepo.agent.constant.AGENT_API_PREFIX
import com.tencent.bkrepo.agent.pojo.AgentRunReconnectRequest
import com.tencent.bkrepo.agent.pojo.AgentRunStatusInfo
import com.tencent.bkrepo.agent.pojo.AgentRunStopRequest
import com.tencent.bkrepo.common.api.pojo.Response
import io.agentscope.core.agui.model.RunAgentInput
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * Agent 对话 run HTTP 契约。
 *
 * 所有接口均需登录；[projectId] 通过 query 传递，权限复用项目 `project_view`。
 */
@Tag(name = "Agent对话接口")
@RequestMapping(AGENT_API_PREFIX)
interface UserAgentChatResource {

    @Operation(summary = "发起一轮 AG-UI run，以SSE返回AguiEvent事件流")
    @PostMapping("/run", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun run(
        @RequestAttribute userId: String,
        @Parameter(name = "项目ID", required = true)
        @RequestParam projectId: String,
        @RequestBody input: RunAgentInput,
    ): SseEmitter

    @Operation(summary = "查询 run 运行状态")
    @GetMapping("/run/status")
    fun getRunStatus(
        @RequestAttribute userId: String,
        @Parameter(name = "项目ID", required = true)
        @RequestParam projectId: String,
        @Parameter(name = "AG-UI threadId", required = true)
        @RequestParam threadId: String,
    ): Response<AgentRunStatusInfo>

    @Operation(summary = "停止 active run")
    @PostMapping("/run/stop")
    fun stopRun(
        @RequestAttribute userId: String,
        @Parameter(name = "项目ID", required = true)
        @RequestParam projectId: String,
        @RequestBody request: AgentRunStopRequest,
    ): Response<Boolean>

    @Operation(summary = "重连并重放 run 事件")
    @PostMapping("/run/reconnect", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun reconnectRun(
        @RequestAttribute userId: String,
        @Parameter(name = "项目ID", required = true)
        @RequestParam projectId: String,
        @RequestBody request: AgentRunReconnectRequest,
    ): SseEmitter
}
