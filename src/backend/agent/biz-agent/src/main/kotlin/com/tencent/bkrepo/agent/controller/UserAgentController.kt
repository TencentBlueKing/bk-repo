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
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.agent.controller

import com.tencent.bkrepo.agent.constant.HEADER_DEVICE_ID
import com.tencent.bkrepo.agent.constant.LOG_OPERATE_RUN
import com.tencent.bkrepo.agent.constant.LOG_OPERATE_SESSION_CREATE
import com.tencent.bkrepo.agent.pojo.AgentRunRequest
import com.tencent.bkrepo.agent.pojo.AgentSessionInfo
import com.tencent.bkrepo.agent.service.AgentRunService
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.annotation.LogOperate
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Tag(name = "Agent会话接口")
@RestController
@RequestMapping("/api/agent")
@Principal(PrincipalType.GENERAL)
class UserAgentController(
    private val agentRunService: AgentRunService,
) {

    @Operation(summary = "创建会话")
    @PostMapping("/session/create")
    @LogOperate(type = LOG_OPERATE_SESSION_CREATE)
    fun createSession(@RequestAttribute userId: String): Response<AgentSessionInfo> {
        return ResponseBuilder.success(agentRunService.createSession(userId, currentDeviceId()))
    }

    @Operation(summary = "发起一轮对话，以SSE返回agent事件流")
    @PostMapping("/run", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @LogOperate(type = LOG_OPERATE_RUN, desensitize = true)
    fun run(
        @RequestAttribute userId: String,
        @RequestBody request: AgentRunRequest,
    ): SseEmitter {
        return agentRunService.run(userId, currentDeviceId(), request)
    }

    private fun currentDeviceId(): String? {
        return HeaderUtils.getHeader(HEADER_DEVICE_ID)?.takeIf { it.isNotBlank() }
    }
}
