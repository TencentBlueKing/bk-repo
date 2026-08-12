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

package com.tencent.bkrepo.agent.service.impl

import com.tencent.bkrepo.agent.dao.AgentRunDao
import com.tencent.bkrepo.agent.model.TAgentRun
import com.tencent.bkrepo.agent.pojo.AgentRunStatus
import com.tencent.bkrepo.agent.pojo.AgentRunTriggerType
import com.tencent.bkrepo.agent.service.AgentRunRecordService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@Service
class AgentRunRecordServiceImpl(
    private val agentRunDao: AgentRunDao,
) : AgentRunRecordService {

    private val startedAtByRunId = ConcurrentHashMap<String, LocalDateTime>()
    private val finishedRunIds = ConcurrentHashMap.newKeySet<String>()

    override fun findByRunId(runId: String): TAgentRun? = agentRunDao.findByRunId(runId)

    override fun findLatestBySessionId(sessionId: String): TAgentRun? =
        agentRunDao.findLatestBySessionId(sessionId)

    override fun startRun(
        runId: String,
        executionId: String,
        sessionId: String,
        userId: String,
        projectId: String,
        deviceId: String?,
        entryAgentId: String,
        triggerType: AgentRunTriggerType,
    ) {
        val startedAt = LocalDateTime.now()
        startedAtByRunId[runId] = startedAt
        try {
            agentRunDao.insertRun(
                TAgentRun(
                    runId = runId,
                    executionId = executionId,
                    sessionId = sessionId,
                    userId = userId,
                    projectId = projectId,
                    deviceId = deviceId,
                    status = AgentRunStatus.RUNNING,
                    entryAgentId = entryAgentId,
                    triggerType = triggerType,
                    startedAt = startedAt,
                ),
            )
        } catch (ex: Exception) {
            startedAtByRunId.remove(runId)
            logger.warn("failed to insert agent run[$runId]", ex)
        }
    }

    override fun finishRun(
        runId: String,
        status: AgentRunStatus,
        cancelReason: String?,
        errorCode: String?,
    ) {
        if (!finishedRunIds.add(runId)) return
        val startedAt = startedAtByRunId.remove(runId) ?: LocalDateTime.now()
        val finishedAt = LocalDateTime.now()
        try {
            val updated = agentRunDao.finishRun(
                runId = runId,
                status = status,
                finishedAt = finishedAt,
                startedAt = startedAt,
                cancelReason = cancelReason,
                errorCode = errorCode,
            )
            if (!updated) {
                logger.debug("agent run[$runId] already finished or missing")
            }
        } catch (ex: Exception) {
            finishedRunIds.remove(runId)
            logger.warn("failed to finish agent run[$runId]", ex)
        }
    }

    override fun removeBySessionId(sessionId: String) {
        agentRunDao.removeBySessionId(sessionId)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AgentRunRecordServiceImpl::class.java)
    }
}
