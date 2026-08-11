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

import com.tencent.bkrepo.agent.constant.AGENT_MESSAGE_ID_PREFIX
import com.tencent.bkrepo.agent.dao.AgentMessageDao
import com.tencent.bkrepo.agent.model.TAgentMessage
import com.tencent.bkrepo.agent.pojo.AgentMessageRole
import com.tencent.bkrepo.agent.service.AgentMessageArchiveService
import com.tencent.bkrepo.common.api.constant.StringPool
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class AgentMessageArchiveServiceImpl(
    private val agentMessageDao: AgentMessageDao,
) : AgentMessageArchiveService {

    override fun archiveUserMessage(sessionId: String, runId: String, content: String) {
        archiveMessage(
            sessionId = sessionId,
            runId = runId,
            role = AgentMessageRole.USER,
            content = content,
        )
    }

    override fun archiveAssistantMessage(sessionId: String, runId: String, content: String, agentId: String?) {
        archiveMessage(
            sessionId = sessionId,
            runId = runId,
            role = AgentMessageRole.ASSISTANT,
            content = content,
            agentId = agentId,
        )
    }

    private fun archiveMessage(
        sessionId: String,
        runId: String,
        role: AgentMessageRole,
        content: String,
        agentId: String? = null,
    ) {
        if (content.isBlank()) {
            return
        }
        val message = TAgentMessage(
            messageId = AGENT_MESSAGE_ID_PREFIX + StringPool.uniqueId(),
            sessionId = sessionId,
            runId = runId,
            role = role,
            content = content,
            agentId = agentId,
            createdAt = LocalDateTime.now(),
        )
        insertWithRetry(message)
    }

    private fun insertWithRetry(message: TAgentMessage) {
        try {
            agentMessageDao.insert(message)
        } catch (first: Exception) {
            logger.warn("agent message archive failed once, retrying message[${message.messageId}]", first)
            try {
                agentMessageDao.insert(message)
            } catch (second: Exception) {
                logger.error("agent message archive failed, message[${message.messageId}]", second)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AgentMessageArchiveServiceImpl::class.java)
    }
}
