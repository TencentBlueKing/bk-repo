/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.api.user

import com.tencent.bkrepo.agent.constant.AGENT_API_PREFIX
import com.tencent.bkrepo.agent.pojo.AgentMessageInfo
import com.tencent.bkrepo.agent.pojo.AgentSessionCreateResult
import com.tencent.bkrepo.agent.pojo.AgentSessionDeleteRequest
import com.tencent.bkrepo.agent.pojo.AgentSessionInfo
import com.tencent.bkrepo.agent.pojo.AgentSessionUpdateRequest
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * Agent 会话 HTTP 契约。
 *
 * 所有接口均需登录；[projectId] 通过 query 传递，权限复用项目 `project_view`。
 */
@Tag(name = "Agent会话接口")
@RequestMapping(AGENT_API_PREFIX)
interface UserAgentSessionResource {

    @Operation(summary = "创建会话")
    @PostMapping("/session/create")
    fun createSession(
        @RequestAttribute userId: String,
        @Parameter(name = "项目ID", required = true)
        @RequestParam projectId: String,
    ): Response<AgentSessionCreateResult>

    @Operation(summary = "查询会话列表")
    @GetMapping("/session/list")
    fun listSessions(
        @RequestAttribute userId: String,
        @Parameter(name = "项目ID", required = true)
        @RequestParam projectId: String,
        @Parameter(name = "页码，从1开始")
        @RequestParam(defaultValue = "1") pageNumber: Int,
        @Parameter(name = "分页大小")
        @RequestParam(defaultValue = "20") pageSize: Int,
    ): Response<Page<AgentSessionInfo>>

    @Operation(summary = "查询会话消息历史")
    @GetMapping("/session/messages")
    fun listMessages(
        @RequestAttribute userId: String,
        @Parameter(name = "项目ID", required = true)
        @RequestParam projectId: String,
        @Parameter(name = "AG-UI threadId", required = true)
        @RequestParam threadId: String,
        @Parameter(name = "页码，从1开始")
        @RequestParam(defaultValue = "1") pageNumber: Int,
        @Parameter(name = "分页大小")
        @RequestParam(defaultValue = "50") pageSize: Int,
    ): Response<Page<AgentMessageInfo>>

    @Operation(summary = "更新会话标题")
    @PostMapping("/session/update")
    fun updateSession(
        @RequestAttribute userId: String,
        @Parameter(name = "项目ID", required = true)
        @RequestParam projectId: String,
        @RequestBody request: AgentSessionUpdateRequest,
    ): Response<Boolean>

    @Operation(summary = "删除会话")
    @PostMapping("/session/delete")
    fun deleteSession(
        @RequestAttribute userId: String,
        @Parameter(name = "项目ID", required = true)
        @RequestParam projectId: String,
        @RequestBody request: AgentSessionDeleteRequest,
    ): Response<Boolean>
}
