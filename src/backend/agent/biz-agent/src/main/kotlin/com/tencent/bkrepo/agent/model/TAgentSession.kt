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

import com.tencent.bkrepo.agent.pojo.AgentSessionStatus
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/** 会话元数据，对应 Mongo 集合 `agent_session`。 */
@Document("agent_session")
@CompoundIndexes(
    CompoundIndex(
        name = "threadId_idx",
        def = "{'threadId': 1}",
        unique = true,
        background = true,
    ),
    CompoundIndex(
        name = "userId_projectId_updatedAt_idx",
        def = "{'userId': 1, 'projectId': 1, 'updatedAt': -1}",
        background = true,
    ),
)
data class TAgentSession(
    var id: String? = null,
    var threadId: String,
    var userId: String,
    var projectId: String,
    var title: String? = null,
    var status: AgentSessionStatus = AgentSessionStatus.ACTIVE,
    var createdAt: LocalDateTime,
    var updatedAt: LocalDateTime,
    var lastRunId: String? = null,
    var promptVersion: String? = null,
)
