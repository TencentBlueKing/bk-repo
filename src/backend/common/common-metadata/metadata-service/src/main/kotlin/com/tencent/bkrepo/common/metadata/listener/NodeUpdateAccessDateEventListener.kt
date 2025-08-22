/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.metadata.listener

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.artifact.properties.ArtifactEventProperties
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.mongo.constant.ID
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


/**
 * 消费基于MQ传递的事件去更新对应access date
 */
@Component
@Conditional(SyncCondition::class)
class NodeUpdateAccessDateEventListener(
    private val nodeDao: NodeDao,
    private val artifactEventProperties: ArtifactEventProperties,
) {

    /**
     * 允许接收的事件类型
     */
    private val acceptTypes = setOf(
        EventType.NODE_UPDATE_ACCESS_DATE,
    )

    fun accept(message: Message<ArtifactEvent>) {
        if (!acceptTypes.contains(message.payload.type)) {
            return
        }
        doUpdateAccessDate(message.payload)
    }

    private fun doUpdateAccessDate(event: ArtifactEvent) {
        if (!filter(event)) return
        try {
            updateAccessDate(event)
        } catch (ignore: Exception) {
            logger.warn("update access date $event error: ${ignore.message}")
        }
    }

    private fun filter(event: ArtifactEvent): Boolean {
        if (!artifactEventProperties.consumeAccessDateEvent) return false
        if (artifactEventProperties.consumeProjectRepoKey.isEmpty()) return true
        // 当为空的情况下更新所有事件
        val projectRepoKey = "${event.projectId}/${event.repoName}"
        var result = false
        artifactEventProperties.consumeProjectRepoKey.forEach {
            val regex = Regex(it.replace("*", ".*"))
            if (regex.matches(projectRepoKey)) {
                result = true
            }
        }
        return result
    }

    private fun updateAccessDate(event: ArtifactEvent) {
        val accessDateStr = event.data["accessDate"].toString()
        val query = Query(
            where(TNode::projectId).isEqualTo(event.projectId)
                .and(ID).isEqualTo(event.resourceKey)
                .and(TNode::deleted.name).isEqualTo(null)
        )
        val node = nodeDao.findOne(query) ?: return
        val accessDate = LocalDateTime.parse(accessDateStr, DateTimeFormatter.ISO_DATE_TIME)
        // 避免消息堆积过多导致同一个节点同一时间出现多个更新事件
        if (!durationCheck(node.lastAccessDate, node.lastModifiedDate, accessDate)) return
        logger.info("update node [${node.fullPath}] access time in [${node.projectId}/${node.repoName}]")
        val update = Update().set(TNode::lastAccessDate.name, accessDate)
        nodeDao.updateFirst(query, update)
    }

    private fun durationCheck(
        lastAccessDate: LocalDateTime?,
        lastModifiedDate: LocalDateTime,
        accessDate: LocalDateTime
    ): Boolean {
        val temp = lastAccessDate ?: lastModifiedDate
        return accessDate.minusMinutes(artifactEventProperties.accessDateDuration.toMinutes()).isAfter(temp)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeUpdateAccessDateEventListener::class.java)
    }
}
