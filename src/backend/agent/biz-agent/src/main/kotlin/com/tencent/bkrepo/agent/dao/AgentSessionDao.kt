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

import com.tencent.bkrepo.agent.model.TAgentSession
import com.tencent.bkrepo.agent.pojo.AgentSessionStatus
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/** 会话元数据访问层，对应 Mongo 集合 `agent_session`。 */
@Repository
class AgentSessionDao : SimpleMongoDao<TAgentSession>() {

    /**
     * 写入会话元数据。返回 Mongo [insert] 结果（含 `_id`）。
     *
     * 命中 `sessionId` 唯一索引冲突时，仅在同归属下返回已有记录以支持幂等重试；
     * 否则重新抛出异常，避免误返回他人会话。
     */
    fun insertSession(sessionId: String, userId: String, projectId: String): TAgentSession {
        val now = LocalDateTime.now()
        val session = TAgentSession(
            sessionId = sessionId,
            userId = userId,
            projectId = projectId,
            title = null,
            status = AgentSessionStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
        )
        return try {
            insert(session)
        } catch (exception: DuplicateKeyException) {
            logger.warn("duplicate agent session insert, sessionId[$sessionId]", exception)
            val existing = findBySessionId(sessionId)
            if (existing != null && existing.userId == userId && existing.projectId == projectId) {
                existing
            } else {
                throw exception
            }
        }
    }

    fun findBySessionId(sessionId: String): TAgentSession? {
        return findOne(Query(Criteria.where(TAgentSession::sessionId.name).`is`(sessionId)))
    }

    fun pageByUserAndProject(userId: String, projectId: String, pageNumber: Int, pageSize: Int): Page<TAgentSession> {
        val query = Query(
            Criteria.where(TAgentSession::userId.name).`is`(userId)
                .and(TAgentSession::projectId.name).`is`(projectId)
                .and(TAgentSession::status.name).`is`(AgentSessionStatus.ACTIVE),
        ).with(Sort.by(Sort.Direction.DESC, TAgentSession::updatedAt.name))
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val totalRecords = count(query)
        val records = find(query.with(pageRequest))
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    fun updateTitle(sessionId: String, title: String, updatedAt: LocalDateTime) {
        val query = Query(Criteria.where(TAgentSession::sessionId.name).`is`(sessionId))
        val update = Update()
            .set(TAgentSession::title.name, title)
            .set(TAgentSession::updatedAt.name, updatedAt)
        updateFirst(query, update)
    }

    /** 记录最近一次 run，并刷新列表排序用的 [TAgentSession.updatedAt]。 */
    fun touchSession(sessionId: String, lastRunId: String, updatedAt: LocalDateTime) {
        val query = Query(Criteria.where(TAgentSession::sessionId.name).`is`(sessionId))
        val update = Update()
            .set(TAgentSession::lastRunId.name, lastRunId)
            .set(TAgentSession::updatedAt.name, updatedAt)
        updateFirst(query, update)
    }

    fun markDeleted(sessionId: String, updatedAt: LocalDateTime) {
        val query = Query(Criteria.where(TAgentSession::sessionId.name).`is`(sessionId))
        val update = Update()
            .set(TAgentSession::status.name, AgentSessionStatus.DELETED)
            .set(TAgentSession::updatedAt.name, updatedAt)
        updateFirst(query, update)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AgentSessionDao::class.java)
    }
}
