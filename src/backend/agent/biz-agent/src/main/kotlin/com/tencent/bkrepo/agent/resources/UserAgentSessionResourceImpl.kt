/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.resources

import com.tencent.bkrepo.agent.api.user.UserAgentSessionResource
import com.tencent.bkrepo.agent.constant.LOG_OPERATE_SESSION_CREATE
import com.tencent.bkrepo.agent.constant.LOG_OPERATE_SESSION_DELETE
import com.tencent.bkrepo.agent.constant.LOG_OPERATE_SESSION_LIST
import com.tencent.bkrepo.agent.constant.LOG_OPERATE_SESSION_MESSAGES
import com.tencent.bkrepo.agent.constant.LOG_OPERATE_SESSION_UPDATE
import com.tencent.bkrepo.agent.pojo.AgentMessageInfo
import com.tencent.bkrepo.agent.pojo.AgentSessionCreateResult
import com.tencent.bkrepo.agent.pojo.AgentSessionDeleteRequest
import com.tencent.bkrepo.agent.pojo.AgentSessionInfo
import com.tencent.bkrepo.agent.pojo.AgentSessionUpdateRequest
import com.tencent.bkrepo.agent.service.AgentSessionService
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.annotation.LogOperate
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.web.bind.annotation.RestController

@RestController
@Principal(PrincipalType.GENERAL)
class UserAgentSessionResourceImpl(
    private val agentSessionService: AgentSessionService,
) : UserAgentSessionResource {

    @LogOperate(type = LOG_OPERATE_SESSION_CREATE)
    override fun createSession(userId: String, projectId: String): Response<AgentSessionCreateResult> {
        return ResponseBuilder.success(agentSessionService.createSession(userId, projectId))
    }

    @LogOperate(type = LOG_OPERATE_SESSION_LIST)
    override fun listSessions(
        userId: String,
        projectId: String,
        pageNumber: Int,
        pageSize: Int,
    ): Response<Page<AgentSessionInfo>> {
        return ResponseBuilder.success(agentSessionService.listSessions(userId, projectId, pageNumber, pageSize))
    }

    @LogOperate(type = LOG_OPERATE_SESSION_MESSAGES)
    override fun listMessages(
        userId: String,
        projectId: String,
        threadId: String,
        pageNumber: Int,
        pageSize: Int,
    ): Response<Page<AgentMessageInfo>> {
        return ResponseBuilder.success(
            agentSessionService.listMessages(userId, projectId, threadId, pageNumber, pageSize),
        )
    }

    @LogOperate(type = LOG_OPERATE_SESSION_UPDATE)
    override fun updateSession(
        userId: String,
        projectId: String,
        request: AgentSessionUpdateRequest,
    ): Response<Boolean> {
        agentSessionService.updateSession(userId, projectId, request)
        return ResponseBuilder.success(true)
    }

    @LogOperate(type = LOG_OPERATE_SESSION_DELETE)
    override fun deleteSession(
        userId: String,
        projectId: String,
        request: AgentSessionDeleteRequest,
    ): Response<Boolean> {
        agentSessionService.deleteSession(userId, projectId, request)
        return ResponseBuilder.success(true)
    }
}
