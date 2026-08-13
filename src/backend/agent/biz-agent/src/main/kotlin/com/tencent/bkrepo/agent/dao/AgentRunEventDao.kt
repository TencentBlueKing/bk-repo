/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.agent.dao

import com.tencent.bkrepo.agent.model.TAgentRunEvent
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository

@Repository
class AgentRunEventDao : SimpleMongoDao<TAgentRunEvent>() {

    /** @return true 表示新插入，false 表示 (runId, eventIndex) 已存在 */
    fun insertIfAbsent(event: TAgentRunEvent): Boolean {
        return try {
            insert(event)
            true
        } catch (_: DuplicateKeyException) {
            false
        }
    }

    fun listByRunId(runId: String): List<TAgentRunEvent> {
        val query = Query(Criteria.where(TAgentRunEvent::runId.name).`is`(runId))
            .with(Sort.by(Sort.Direction.ASC, TAgentRunEvent::eventIndex.name))
        return find(query)
    }

    fun listByRunIdAfterIndex(runId: String, afterIndex: Long): List<TAgentRunEvent> {
        val query = Query(
            Criteria.where(TAgentRunEvent::runId.name).`is`(runId)
                .and(TAgentRunEvent::eventIndex.name).gt(afterIndex),
        ).with(Sort.by(Sort.Direction.ASC, TAgentRunEvent::eventIndex.name))
        return find(query)
    }

    fun maxEventIndex(runId: String): Long? {
        val query = Query(Criteria.where(TAgentRunEvent::runId.name).`is`(runId))
            .with(Sort.by(Sort.Direction.DESC, TAgentRunEvent::eventIndex.name))
            .limit(1)
        return findOne(query)?.eventIndex
    }

    fun removeByRunId(runId: String) {
        remove(Query(Criteria.where(TAgentRunEvent::runId.name).`is`(runId)))
    }
}
