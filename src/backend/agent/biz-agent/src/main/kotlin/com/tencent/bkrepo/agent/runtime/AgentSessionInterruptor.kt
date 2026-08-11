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

package com.tencent.bkrepo.agent.runtime

import io.agentscope.core.agent.RuntimeContext
import io.agentscope.harness.agent.HarnessAgent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 按 [RuntimeContext] 中断 AgentScope 进行中的 run。
 *
 * AgentScope 2.0.0 的 [HarnessAgent] 尚未转发 per-session interrupt，需经 [HarnessAgent.getDelegate]。
 */
@Component
class AgentSessionInterruptor(
    private val agent: HarnessAgent,
) {

    fun interrupt(runtimeContext: RuntimeContext) {
        try {
            agent.delegate.interrupt(runtimeContext)
        } catch (exception: Exception) {
            logger.debug(
                "failed to interrupt agent run for user[${runtimeContext.userId}] " +
                    "session[${runtimeContext.sessionId}]",
                exception,
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AgentSessionInterruptor::class.java)
    }
}
