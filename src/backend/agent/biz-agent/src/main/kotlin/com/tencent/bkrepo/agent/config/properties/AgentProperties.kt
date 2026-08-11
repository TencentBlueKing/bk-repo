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

package com.tencent.bkrepo.agent.config.properties

import com.tencent.bkrepo.agent.config.AgentSystemPrompts
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Agent Harness 运行时配置
 */
@ConfigurationProperties("agent")
data class AgentProperties(
    /**
     * HarnessAgent 名称，用于日志与 trace 标识
     */
    var name: String = DEFAULT_NAME,
    /**
     * 系统提示词。Workspace 上下文会在每轮推理前追加到该提示词之后
     */
    var sysPrompt: String = AgentSystemPrompts.DEFAULT,
    /**
     * ReAct 循环最大迭代次数
     */
    var maxIters: Int = DEFAULT_MAX_ITERS,
    /**
     * HarnessAgent 工作目录。当前阶段不开放文件与 shell 工具，仅用于框架自身的上下文文件
     */
    var workspace: String = DEFAULT_WORKSPACE,
    /**
     * SSE 连接的最大保持时长，超过后客户端需要重新发起请求
     */
    var sseTimeout: Duration = DEFAULT_SSE_TIMEOUT,
    /**
     * 单次用户输入允许的最大字符数
     */
    var maxMessageLength: Int = DEFAULT_MAX_MESSAGE_LENGTH,
    /**
     * 会话 ID 允许的最大长度
     */
    var maxSessionIdLength: Int = DEFAULT_MAX_SESSION_ID_LENGTH,
    /**
     * 会话归属记录在 Redis 中的 TTL；未配置 Redis 时退化为进程内存储
     */
    var sessionTtl: Duration = DEFAULT_SESSION_TTL,
    /**
     * 同会话 run 互斥锁 TTL，应覆盖 SSE 超时并留余量
     */
    var runLockTtl: Duration = DEFAULT_RUN_LOCK_TTL,
    /**
     * 是否注册客户端本地工具 schema（由客户端在 REQUIRE_EXTERNAL_EXECUTION 中执行）。
     */
    var localToolsEnabled: Boolean = DEFAULT_LOCAL_TOOLS_ENABLED,
) {
    companion object {
        const val DEFAULT_NAME = "bkrepo-assistant"
        const val DEFAULT_MAX_ITERS = 10
        const val DEFAULT_WORKSPACE = "/data/workspace/agent"
        val DEFAULT_SSE_TIMEOUT: Duration = Duration.ofMinutes(10)
        const val DEFAULT_MAX_MESSAGE_LENGTH = 32 * 1024
        const val DEFAULT_MAX_SESSION_ID_LENGTH = 128
        val DEFAULT_SESSION_TTL: Duration = Duration.ofDays(30)
        val DEFAULT_RUN_LOCK_TTL: Duration = Duration.ofMinutes(11)
        const val DEFAULT_LOCAL_TOOLS_ENABLED = true
    }
}
