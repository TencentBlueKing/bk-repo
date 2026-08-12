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

package com.tencent.bkrepo.agent.service

/**
 * 会话消息 Mongo 归档，与 AgentScope AgentState 分离。
 *
 * 写入失败不向上抛出，由实现层记录日志并重试。
 */
interface AgentMessageArchiveService {

    /** 归档用户消息；[messageId] 来自客户端 [RunAgentInput.messages] 的 canonical id。 */
    fun archiveUserMessage(
        threadId: String,
        runId: String,
        messageId: String,
        textContent: String,
        structuredContent: Map<String, Any>? = null,
    )

    /** 归档 assistant 消息；[messageId] 来自 AG-UI TEXT_MESSAGE_START。 */
    fun archiveAssistantMessage(
        threadId: String,
        runId: String,
        messageId: String,
        textContent: String,
        agentId: String? = null,
        structuredContent: Map<String, Any>? = null,
    )
}
