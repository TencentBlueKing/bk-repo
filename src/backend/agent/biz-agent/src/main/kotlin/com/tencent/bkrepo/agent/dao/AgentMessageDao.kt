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

package com.tencent.bkrepo.agent.dao

import com.tencent.bkrepo.agent.model.TAgentMessage
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository

/**
 * 会话消息持久化。保存 USER/ASSISTANT 原文，供历史查询；
 * 与 AgentScope [io.agentscope.core.state.AgentStateStore] 的运行时上下文分离，后者会被压缩。
 */
@Repository
class AgentMessageDao : SimpleMongoDao<TAgentMessage>() {

    fun findBySessionAndMessageId(sessionId: String, messageId: String): TAgentMessage? {
        val query = Query(
            Criteria.where(TAgentMessage::sessionId.name).`is`(sessionId)
                .and(TAgentMessage::messageId.name).`is`(messageId),
        )
        return findOne(query)
    }

    /** @return true 表示新插入，false 表示 (sessionId, messageId) 已存在 */
    fun insertIfAbsent(message: TAgentMessage): Boolean {
        if (findBySessionAndMessageId(message.sessionId, message.messageId) != null) {
            return false
        }
        return try {
            insert(message)
            true
        } catch (_: DuplicateKeyException) {
            false
        }
    }

    fun pageBySessionId(sessionId: String, pageNumber: Int, pageSize: Int): Page<TAgentMessage> {
        val query = Query(Criteria.where(TAgentMessage::sessionId.name).`is`(sessionId))
            .with(Sort.by(Sort.Direction.ASC, TAgentMessage::createdAt.name))
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val totalRecords = count(query)
        val records = find(query.with(pageRequest))
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    fun removeBySessionId(sessionId: String) {
        remove(Query(Criteria.where(TAgentMessage::sessionId.name).`is`(sessionId)))
    }
}
