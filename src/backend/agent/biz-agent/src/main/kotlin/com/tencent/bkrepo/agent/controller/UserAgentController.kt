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

package com.tencent.bkrepo.agent.controller

import com.tencent.bkrepo.agent.constant.HEADER_DEVICE_ID
import com.tencent.bkrepo.agent.constant.LOG_OPERATE_RUN
import com.tencent.bkrepo.agent.constant.LOG_OPERATE_SESSION_CREATE
import com.tencent.bkrepo.agent.constant.LOG_OPERATE_SESSION_DELETE
import com.tencent.bkrepo.agent.constant.LOG_OPERATE_SESSION_LIST
import com.tencent.bkrepo.agent.constant.LOG_OPERATE_SESSION_MESSAGES
import com.tencent.bkrepo.agent.constant.LOG_OPERATE_SESSION_UPDATE
import com.tencent.bkrepo.agent.pojo.AgentMessageInfo
import com.tencent.bkrepo.agent.pojo.AgentRunRequest
import com.tencent.bkrepo.agent.pojo.AgentSessionDeleteRequest
import com.tencent.bkrepo.agent.pojo.AgentSessionCreateResult
import com.tencent.bkrepo.agent.pojo.AgentSessionInfo
import com.tencent.bkrepo.agent.pojo.AgentSessionUpdateRequest
import com.tencent.bkrepo.agent.service.AgentRunService
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.annotation.LogOperate
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * Agent 会话与对话 HTTP 接口。
 *
 * 所有接口均需登录；[projectId] 通过 query 传递，权限复用项目 `project_view`。
 */
@Tag(name = "Agent会话接口")
@RestController
@RequestMapping("/api/agent")
@Principal(PrincipalType.GENERAL)
class UserAgentController(
    private val agentRunService: AgentRunService,
) {

    /**
     * 创建会话。
     *
     * `POST /api/agent/session/create?projectId=`，返回 [AgentSessionCreateResult]。
     */
    @Operation(summary = "创建会话")
    @PostMapping("/session/create")
    @LogOperate(type = LOG_OPERATE_SESSION_CREATE)
    fun createSession(
        @RequestAttribute userId: String,
        @Parameter(name = "项目ID", required = true)
        @RequestParam projectId: String,
    ): Response<AgentSessionCreateResult> {
        return ResponseBuilder.success(agentRunService.createSession(userId, projectId))
    }

    /**
     * 分页查询当前用户在项目下的会话列表。
     *
     * `GET /api/agent/session/list?projectId=&pageNumber=&pageSize=`
     */
    @Operation(summary = "查询会话列表")
    @GetMapping("/session/list")
    @LogOperate(type = LOG_OPERATE_SESSION_LIST)
    fun listSessions(
        @RequestAttribute userId: String,
        @Parameter(name = "项目ID", required = true)
        @RequestParam projectId: String,
        @Parameter(name = "页码，从1开始")
        @RequestParam(defaultValue = "1") pageNumber: Int,
        @Parameter(name = "分页大小")
        @RequestParam(defaultValue = "20") pageSize: Int,
    ): Response<Page<AgentSessionInfo>> {
        return ResponseBuilder.success(agentRunService.listSessions(userId, projectId, pageNumber, pageSize))
    }

    /**
     * 分页查询会话消息历史（Mongo 归档原文）。
     *
     * `GET /api/agent/session/messages?projectId=&sessionId=&pageNumber=&pageSize=`
     */
    @Operation(summary = "查询会话消息历史")
    @GetMapping("/session/messages")
    @LogOperate(type = LOG_OPERATE_SESSION_MESSAGES)
    fun listMessages(
        @RequestAttribute userId: String,
        @Parameter(name = "项目ID", required = true)
        @RequestParam projectId: String,
        @Parameter(name = "会话ID", required = true)
        @RequestParam sessionId: String,
        @Parameter(name = "页码，从1开始")
        @RequestParam(defaultValue = "1") pageNumber: Int,
        @Parameter(name = "分页大小")
        @RequestParam(defaultValue = "50") pageSize: Int,
    ): Response<Page<AgentMessageInfo>> {
        return ResponseBuilder.success(
            agentRunService.listMessages(userId, projectId, sessionId, pageNumber, pageSize),
        )
    }

    /**
     * 更新会话标题。
     *
     * `POST /api/agent/session/update?projectId=`
     */
    @Operation(summary = "更新会话标题")
    @PostMapping("/session/update")
    @LogOperate(type = LOG_OPERATE_SESSION_UPDATE)
    fun updateSession(
        @RequestAttribute userId: String,
        @Parameter(name = "项目ID", required = true)
        @RequestParam projectId: String,
        @RequestBody request: AgentSessionUpdateRequest,
    ): Response<Boolean> {
        agentRunService.updateSessionTitle(userId, projectId, request)
        return ResponseBuilder.success(true)
    }

    /**
     * 删除会话及关联的 Mongo 消息、Redis 归属与 AgentState。
     *
     * `POST /api/agent/session/delete?projectId=`
     */
    @Operation(summary = "删除会话")
    @PostMapping("/session/delete")
    @LogOperate(type = LOG_OPERATE_SESSION_DELETE)
    fun deleteSession(
        @RequestAttribute userId: String,
        @Parameter(name = "项目ID", required = true)
        @RequestParam projectId: String,
        @RequestBody request: AgentSessionDeleteRequest,
    ): Response<Boolean> {
        agentRunService.deleteSession(userId, projectId, request)
        return ResponseBuilder.success(true)
    }

    /**
     * 发起一轮对话，响应为 SSE 事件流。
     *
     * `POST /api/agent/run?projectId=`；支持用户输入与本地工具续跑（externalExecutionResults）。
     */
    @Operation(summary = "发起一轮对话，以SSE返回agent事件流")
    @PostMapping("/run", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @LogOperate(type = LOG_OPERATE_RUN, desensitize = true)
    fun run(
        @RequestAttribute userId: String,
        @Parameter(name = "项目ID", required = true)
        @RequestParam projectId: String,
        @RequestHeader(HEADER_DEVICE_ID, required = false) deviceId: String?,
        @RequestBody request: AgentRunRequest,
    ): SseEmitter {
        return agentRunService.run(userId, projectId, deviceId, request)
    }
}
