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

package com.tencent.bkrepo.agent.identity

import com.tencent.bkrepo.agent.constant.RUNTIME_CONTEXT_DEVICE_ID
import com.tencent.bkrepo.agent.constant.RUNTIME_CONTEXT_PROJECT_ID
import com.tencent.bkrepo.agent.constant.RUNTIME_CONTEXT_RUN_ID
import io.agentscope.core.agent.RuntimeContext
import org.springframework.stereotype.Component

/**
 * 统一构造 AgentScope [RuntimeContext]，保证 userId 只来自已认证请求。
 */
@Component
class RuntimeContextFactory {

    fun create(
        userId: String,
        projectId: String,
        sessionId: String,
        deviceId: String? = null,
        runId: String? = null,
    ): RuntimeContext {
        val builder = RuntimeContext.builder()
            .userId(userId)
            .sessionId(sessionId)
            .put(RUNTIME_CONTEXT_PROJECT_ID, projectId)
        deviceId?.takeIf { it.isNotBlank() }?.let {
            builder.put(RUNTIME_CONTEXT_DEVICE_ID, it)
        }
        runId?.takeIf { it.isNotBlank() }?.let {
            builder.put(RUNTIME_CONTEXT_RUN_ID, it)
        }
        return builder.build()
    }
}
