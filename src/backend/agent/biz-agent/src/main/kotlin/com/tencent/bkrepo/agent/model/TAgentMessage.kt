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

package com.tencent.bkrepo.agent.model

import com.tencent.bkrepo.agent.pojo.AgentMessageRole
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/** 会话消息归档，对应 Mongo 集合 `agent_message`。 */
@Document("agent_message")
@CompoundIndexes(
    CompoundIndex(
        name = "threadId_messageId_idx",
        def = "{'threadId': 1, 'messageId': 1}",
        unique = true,
        background = true,
    ),
    CompoundIndex(
        name = "threadId_createdAt_idx",
        def = "{'threadId': 1, 'createdAt': 1}",
        background = true,
    ),
    CompoundIndex(
        name = "runId_idx",
        def = "{'runId': 1}",
        background = true,
    ),
)
data class TAgentMessage(
    var id: String? = null,
    var messageId: String,
    var threadId: String,
    var runId: String?,
    var role: AgentMessageRole,
    /** textContent 投影，供历史 API 与标题生成。 */
    var content: String,
    /** AG-UI MessageContent 结构化快照。 */
    var structuredContent: Map<String, Any>? = null,
    var agentId: String? = null,
    var toolCallId: String? = null,
    var createdAt: LocalDateTime,
    var metadata: Map<String, Any>? = null,
    var redactionVersion: Int? = null,
)
