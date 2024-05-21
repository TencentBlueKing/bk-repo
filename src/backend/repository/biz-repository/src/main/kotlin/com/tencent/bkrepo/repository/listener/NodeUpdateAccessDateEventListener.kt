/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.repository.listener

import com.tencent.bkrepo.common.artifact.event.ArtifactEventProperties
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.artifact.event.node.NodeUpdateAccessDateEvent
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TNode
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


/**
 * 用于更新节点访问时间
 */
@Component
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


    @Async
    @EventListener(ArtifactEvent::class)
    fun handle(event: ArtifactEvent) {
        if (!acceptTypes.contains(event.type)) {
            return
        }
        if (!filter(event)) return
        try {
            updateAccessDate(event)
        } catch (ignore: Exception) {
            logger.warn("update access date $event error: ${ignore.message}")
        }
    }

    private fun filter(event: ArtifactEvent): Boolean {
        if (!artifactEventProperties.consumeAccessDateEvent) return false
        // 当为空的情况下更新所有事件
        val projectRepoKey = "${event.projectId}/${event.repoName}"
        artifactEventProperties.consumeProjectRepoKey.forEach {
            val regex = Regex(it.replace("*", ".*"))
            if (regex.matches(projectRepoKey)) {
                return false
            }
        }
        return true
    }

    private fun updateAccessDate(event: ArtifactEvent) {
        require(event is NodeUpdateAccessDateEvent)
        val query = Query(where(TNode::id).isEqualTo(event.resourceKey))
        val accessDate = LocalDateTime.parse(event.accessDate, DateTimeFormatter.ISO_DATE_TIME)
        val update = Update().set(TNode::lastAccessDate.name, accessDate)
        nodeDao.updateFirst(query, update)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeUpdateAccessDateEventListener::class.java)
    }
}
