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

import com.tencent.bkrepo.agent.dao.AgentMessageDao
import com.tencent.bkrepo.agent.dao.AgentSessionDao
import com.tencent.bkrepo.agent.model.TAgentMessage
import com.tencent.bkrepo.agent.pojo.AgentMessageRole
import com.tencent.bkrepo.agent.service.AgentMessageArchiveService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 将 USER/ASSISTANT 原文写入 Mongo，供历史 API 查询。
 *
 * 以 (sessionId, messageId) 幂等写入；归档失败不阻塞主流程。
 */
@Service
class AgentMessageArchiveServiceImpl(
    private val agentMessageDao: AgentMessageDao,
    private val agentSessionDao: AgentSessionDao,
) : AgentMessageArchiveService {

    override fun archiveUserMessage(
        sessionId: String,
        runId: String,
        messageId: String,
        textContent: String,
        structuredContent: Map<String, Any>?,
    ) {
        if (textContent.isBlank()) return
        val inserted = archiveMessage(
            sessionId = sessionId,
            runId = runId,
            messageId = messageId,
            role = AgentMessageRole.USER,
            textContent = textContent,
            structuredContent = structuredContent,
        )
        if (!inserted) return
        try {
            agentSessionDao.updateTitleIfBlank(
                sessionId = sessionId,
                title = deriveSessionTitle(textContent),
                updatedAt = LocalDateTime.now(),
            )
        } catch (exception: Exception) {
            logger.warn("auto-set session title failed, session[$sessionId]", exception)
        }
    }

    override fun archiveAssistantMessage(
        sessionId: String,
        runId: String,
        messageId: String,
        textContent: String,
        agentId: String?,
        structuredContent: Map<String, Any>?,
    ) {
        archiveMessage(
            sessionId = sessionId,
            runId = runId,
            messageId = messageId,
            role = AgentMessageRole.ASSISTANT,
            textContent = textContent,
            agentId = agentId,
            structuredContent = structuredContent,
        )
    }

    private fun archiveMessage(
        sessionId: String,
        runId: String,
        messageId: String,
        role: AgentMessageRole,
        textContent: String,
        agentId: String? = null,
        structuredContent: Map<String, Any>? = null,
    ): Boolean {
        if (textContent.isBlank()) return false
        val message = TAgentMessage(
            messageId = messageId,
            sessionId = sessionId,
            runId = runId,
            role = role,
            content = textContent,
            structuredContent = structuredContent,
            agentId = agentId,
            createdAt = LocalDateTime.now(),
        )
        return insertWithRetry(message)
    }

    /** 归档为尽力而为：失败记录日志并补偿重试，不向上抛出以免中断 SSE。 */
    private fun insertWithRetry(message: TAgentMessage): Boolean {
        try {
            return agentMessageDao.insertIfAbsent(message)
        } catch (first: Exception) {
            logger.warn("agent message archive failed once, retrying message[${message.messageId}]", first)
            try {
                return agentMessageDao.insertIfAbsent(message)
            } catch (second: Exception) {
                logger.error("agent message archive failed, message[${message.messageId}]", second)
                return false
            }
        }
    }

    companion object {
        private const val MAX_TITLE_LENGTH = 40
        private val logger = LoggerFactory.getLogger(AgentMessageArchiveServiceImpl::class.java)

        private fun deriveSessionTitle(text: String): String {
            val normalized = text.trim().replace(Regex("\\s+"), " ")
            if (normalized.isBlank()) {
                return "新对话"
            }
            return if (normalized.length > MAX_TITLE_LENGTH) {
                normalized.take(MAX_TITLE_LENGTH) + "…"
            } else {
                normalized
            }
        }
    }
}
