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

package com.tencent.bkrepo.replication.replica.type.edge

import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.common.service.cluster.CommitEdgeEdgeCondition
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.common.service.otel.util.AsyncUtils.trace
import com.tencent.bkrepo.replication.api.cluster.ClusterReplicaTaskClient
import com.tencent.bkrepo.replication.dao.ReplicaTaskDao
import com.tencent.bkrepo.replication.model.TReplicaTask
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.ReplicaStatus
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.replica.executor.EdgePullThreadPoolExecutor
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockingTaskExecutor
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
@Conditional(CommitEdgeEdgeCondition::class)
class EdgePullReplicaTaskJob(
    private val clusterProperties: ClusterProperties,
    private val replicaTaskDao: ReplicaTaskDao,
    private val edgePullReplicaExecutor: EdgePullReplicaExecutor,
    private val lockingTaskExecutor: LockingTaskExecutor
) {

    private val centerReplicaTaskClient: ClusterReplicaTaskClient
        by lazy { FeignClientFactory.create(clusterProperties.center) }
    private val executor = EdgePullThreadPoolExecutor.instance

    @Scheduled(initialDelay = INIT_DELAY, fixedRate = FIXED_RATE)
    fun getReplicaTask() {
        if (executor.activeCount == Runtime.getRuntime().availableProcessors()) {
            return
        }
        val atLeastDuration = Duration.ofMillis(FIXED_RATE)
        val atMostDuration = Duration.ofMillis(2 * FIXED_RATE)
        val lockConfiguration = LockConfiguration(javaClass.simpleName, atMostDuration, atLeastDuration)
        lockingTaskExecutor.executeWithLock(run(), lockConfiguration)
    }

    private fun run(): Runnable {
        return Runnable {
            val task = getLocalReplicaTask() ?: getCenterReplicaTask()
            if (task != null) {
                executor.execute(Runnable { edgePullReplicaExecutor.pullReplica(task.id!!) }.trace())
            }
        }.trace()
    }

    private fun getLocalReplicaTask(): TReplicaTask? {
        val query = Query(
            where(TReplicaTask::replicaType).isEqualTo(ReplicaType.EDGE_PULL)
                .and(TReplicaTask::status).isEqualTo(ReplicaStatus.WAITING)
        ).with(Sort.by(Sort.Direction.ASC, TReplicaTask::id.name))
        return replicaTaskDao.find(query.limit(1)).firstOrNull()
    }

    private fun getCenterReplicaTask(): TReplicaTask? {
        val query = Query(where(TReplicaTask::replicaType).isEqualTo(ReplicaType.EDGE_PULL))
            .with(Sort.by(Sort.Direction.DESC, TReplicaTask::id.name)).limit(1)
        val latestTask = replicaTaskDao.find(query)
        val lastId = if (latestTask.isEmpty()) MIN_OBJECT_ID else latestTask.first().id!!
        val newTaskList = centerReplicaTaskClient.list(
            replicaType = ReplicaType.EDGE_PULL,
            lastId = lastId,
            size = 10,
        ).data?.filter { it.id != lastId }?.map { convert(it) }.orEmpty()
        if (newTaskList.isNotEmpty()) {
            replicaTaskDao.insert(newTaskList)
        }
        return newTaskList.firstOrNull()
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

    companion object {
        private val logger = LoggerFactory.getLogger(EdgePullReplicaTaskJob::class.java)
        private const val INIT_DELAY = 60 * 1000L
        private const val FIXED_RATE = 30 * 1000L
    }
}
