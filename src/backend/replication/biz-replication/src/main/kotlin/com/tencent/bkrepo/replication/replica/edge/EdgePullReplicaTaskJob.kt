/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.replica.edge

import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.common.service.cluster.CommitEdgeEdgeCondition
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.replication.api.cluster.ClusterReplicaTaskClient
import com.tencent.bkrepo.replication.dao.ReplicaTaskDao
import com.tencent.bkrepo.replication.model.TReplicaTask
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.ReplicaStatus
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Conditional
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
@Conditional(CommitEdgeEdgeCondition::class)
class EdgePullReplicaTaskJob(
    private val clusterProperties: ClusterProperties,
    private val replicaTaskDao: ReplicaTaskDao,
    private val applicationEventPublisher: ApplicationEventPublisher
) {

    private val centerReplicaTaskClient: ClusterReplicaTaskClient
        by lazy { FeignClientFactory.create(clusterProperties.center) }

    @Scheduled(fixedRate = 60000)
    @SchedulerLock(name = "pullReplicaTask", lockAtMostFor = "PT1M")
    fun pullReplicaTask() {
        val event = getLocalReplicaTask() ?: getCenterReplicaTask()
        if (event != null) {
            applicationEventPublisher.publishEvent(event)
        }
    }

    private fun getLocalReplicaTask(): EdgePullReplicaTaskEvent? {
        val query = Query(
            where(TReplicaTask::replicaType).isEqualTo(ReplicaType.EDGE_PULL)
                .and(TReplicaTask::status).isEqualTo(ReplicaStatus.WAITING)
        ).with(Sort.by(Sort.Direction.DESC, TReplicaTask::id.name))
        val latestTask = replicaTaskDao.find(query.limit(1)).firstOrNull()
        if (latestTask != null) {
            val size = replicaTaskDao.count(query).toInt()
            return EdgePullReplicaTaskEvent(latestTask.id!!, size)
        }
        return null
    }

    private fun getCenterReplicaTask(): EdgePullReplicaTaskEvent? {
        val query = Query(where(TReplicaTask::replicaType).isEqualTo(ReplicaType.EDGE_PULL))
            .with(Sort.by(Sort.Direction.DESC, TReplicaTask::id.name)).limit(1)
        val latestTask = replicaTaskDao.find(query)
        val lastId = if (latestTask.isEmpty()) MIN_OBJECT_ID else latestTask.first().id!!
        val newTaskList = centerReplicaTaskClient.list(
            replicaType = ReplicaType.EDGE_PULL,
            lastId = lastId,
            size = 100
        ).data?.map { convert(it) }.orEmpty()
        if (newTaskList.isNotEmpty()) {
            replicaTaskDao.insert(newTaskList)
            return EdgePullReplicaTaskEvent(lastId, newTaskList.size)
        }
        return null
    }

    private fun convert(replicaTaskInfo: ReplicaTaskInfo): TReplicaTask {
        with(replicaTaskInfo) {
            return TReplicaTask(
                id = id,
                key = key,
                name = name,
                projectId = projectId,
                replicaObjectType = replicaObjectType,
                replicaType = replicaType,
                setting = setting,
                remoteClusters = remoteClusters,
                description = description,
                status = status ?: ReplicaStatus.WAITING,
                replicatedBytes = replicatedBytes,
                totalBytes = totalBytes,
                lastExecutionStatus = lastExecutionStatus,
                lastExecutionTime = lastExecutionTime,
                nextExecutionTime = nextExecutionTime,
                executionTimes = executionTimes,
                enabled = enabled,
                createdBy = createdBy,
                createdDate = LocalDateTime.parse(createdDate, DateTimeFormatter.ISO_DATE_TIME),
                lastModifiedBy = lastModifiedBy,
                lastModifiedDate = LocalDateTime.parse(lastModifiedDate, DateTimeFormatter.ISO_DATE_TIME)
            )
        }
    }
}
