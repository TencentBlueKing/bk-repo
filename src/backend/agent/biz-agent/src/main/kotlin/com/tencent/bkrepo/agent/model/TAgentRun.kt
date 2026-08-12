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

import com.tencent.bkrepo.agent.pojo.AgentRunStatus
import com.tencent.bkrepo.agent.pojo.AgentRunTriggerType
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/** 一次 HTTP run 请求的运行元数据，对应 Mongo 集合 `agent_run`。 */
@Document("agent_run")
@CompoundIndexes(
    CompoundIndex(
        name = "runId_idx",
        def = "{'runId': 1}",
        unique = true,
        background = true,
    ),
    CompoundIndex(
        name = "threadId_startedAt_idx",
        def = "{'threadId': 1, 'startedAt': -1}",
        background = true,
    ),
    CompoundIndex(
        name = "userId_projectId_startedAt_idx",
        def = "{'userId': 1, 'projectId': 1, 'startedAt': -1}",
        background = true,
    ),
)
data class TAgentRun(
    var id: String? = null,
    var runId: String,
    var executionId: String? = null,
    var threadId: String,
    var userId: String,
    var projectId: String,
    var deviceId: String? = null,
    var status: AgentRunStatus = AgentRunStatus.RUNNING,
    var entryAgentId: String? = null,
    var triggerType: AgentRunTriggerType = AgentRunTriggerType.USER_INPUT,
    var startedAt: LocalDateTime,
    var finishedAt: LocalDateTime? = null,
    var cancelReason: String? = null,
    var errorCode: String? = null,
    var traceId: String? = null,
    var durationMs: Long? = null,
)
