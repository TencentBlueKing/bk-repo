/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.resources

import com.tencent.bkrepo.agent.api.user.UserAgentChatResource
import com.tencent.bkrepo.agent.constant.LOG_OPERATE_RUN
import com.tencent.bkrepo.agent.constant.LOG_OPERATE_RUN_RECONNECT
import com.tencent.bkrepo.agent.constant.LOG_OPERATE_RUN_STREAM
import com.tencent.bkrepo.agent.constant.LOG_OPERATE_RUN_STATUS
import com.tencent.bkrepo.agent.constant.LOG_OPERATE_RUN_STOP
import com.tencent.bkrepo.agent.pojo.AgentRunReconnectRequest
import com.tencent.bkrepo.agent.pojo.AgentRunStatusInfo
import com.tencent.bkrepo.agent.pojo.AgentRunStopRequest
import com.tencent.bkrepo.agent.service.AgentChatService
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.annotation.LogOperate
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.agentscope.core.agui.model.RunAgentInput
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@Principal(PrincipalType.GENERAL)
class UserAgentChatResourceImpl(
    private val agentChatService: AgentChatService,
) : UserAgentChatResource {

    @LogOperate(type = LOG_OPERATE_RUN, desensitize = true)
    override fun run(userId: String, projectId: String, input: RunAgentInput): SseEmitter {
        return agentChatService.run(userId, projectId, input)
    }

    @LogOperate(type = LOG_OPERATE_RUN_STATUS)
    override fun getRunStatus(userId: String, projectId: String, threadId: String): Response<AgentRunStatusInfo> {
        return ResponseBuilder.success(agentChatService.getRunStatus(userId, projectId, threadId))
    }

    @LogOperate(type = LOG_OPERATE_RUN_STOP)
    override fun stopRun(userId: String, projectId: String, request: AgentRunStopRequest): Response<Boolean> {
        return ResponseBuilder.success(agentChatService.stopRun(userId, projectId, request))
    }

    @LogOperate(type = LOG_OPERATE_RUN_STREAM)
    override fun streamRun(
        userId: String,
        projectId: String,
        threadId: String,
        runId: String?,
        lastEventIndex: Long?,
    ): SseEmitter {
        return agentChatService.streamRun(userId, projectId, threadId, runId, lastEventIndex)
    }

    @Deprecated("Use GET /run/stream")
    @LogOperate(type = LOG_OPERATE_RUN_RECONNECT)
    override fun reconnectRun(
        userId: String,
        projectId: String,
        request: AgentRunReconnectRequest,
    ): SseEmitter {
        return agentChatService.reconnectRun(userId, projectId, request)
    }
}
