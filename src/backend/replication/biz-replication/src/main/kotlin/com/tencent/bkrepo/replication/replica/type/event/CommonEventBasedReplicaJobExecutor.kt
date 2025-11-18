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

package com.tencent.bkrepo.replication.replica.type.event

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.dao.EventRecordDao
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordInfo
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.replica.executor.AbstractReplicaJobExecutor
import com.tencent.bkrepo.replication.dao.ReplicaFailureRecordDao
import com.tencent.bkrepo.replication.replica.type.ReplicaService
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import org.slf4j.LoggerFactory

/**
 * 基于事件消息的实时同步通用逻辑
 */
open class CommonEventBasedReplicaJobExecutor(
    clusterNodeService: ClusterNodeService,
    localDataManager: LocalDataManager,
    replicaService: ReplicaService,
    replicationProperties: ReplicationProperties,
    val replicaRecordService: ReplicaRecordService,
    replicaFailureRecordDao: ReplicaFailureRecordDao,
    protected val eventRecordDao: EventRecordDao?
) : AbstractReplicaJobExecutor(
    clusterNodeService, localDataManager, replicaService, replicationProperties, replicaFailureRecordDao
) {

    /**
     * 执行同步
     */
    fun execute(taskDetail: ReplicaTaskDetail, event: ArtifactEvent, eventId: String? = null) {
        val task = taskDetail.task
        val taskKey = task.key
        if (!replicaObjectCheck(taskDetail, event)) {
            eventId?.let {
                // 如果任务不匹配，认为任务已完成（跳过），但不算失败
                updateEventRecordAfterTaskCompletion(eventId, taskKey, true)
            }
            return
        }
        val taskRecord: ReplicaRecordInfo = replicaRecordService.findOrCreateLatestRecord(task.key)
        var taskSucceeded = true

        try {
            val results = task.remoteClusters.map { submit(taskDetail, taskRecord, it, event) }.map { it.get() }
            val replicaOverview = getResultsSummary(results).replicaOverview
            taskRecord.replicaOverview?.let { overview ->
                replicaOverview.success += overview.success
                replicaOverview.failed += overview.failed
                replicaOverview.conflict += overview.conflict
                replicaOverview.fileSuccess += overview.fileSuccess
                replicaOverview.fileFailed += overview.fileFailed
            }
            replicaRecordService.updateRecordReplicaOverview(taskRecord.id, replicaOverview)
            taskSucceeded = !results.any { it?.progress?.failed != 0L || it?.progress?.fileFailed != 0L}
            logger.info("Replica ${event.getFullResourceKey()} completed.")
        } catch (exception: Exception) {
            logger.error("Replica ${event.getFullResourceKey()}} failed: $exception", exception)
            taskSucceeded = false
        } finally {
            eventId?.let {
                // 更新事件记录状态
                updateEventRecordAfterTaskCompletion(eventId, taskKey, taskSucceeded)
            }
        }
    }


    /**
     * 判断分发配置内容是否与待分发事件匹配
     */
    open fun replicaObjectCheck(task: ReplicaTaskDetail, event: ArtifactEvent): Boolean {
        return true
    }

    /**
     * 更新事件记录的任务完成状态
     * 当任务完成时，如果成功则删除记录，如果失败则保留记录用于重试并增加重试次数
     * @param eventId 事件ID
     * @param taskKey 任务key
     * @param taskSucceeded 任务是否成功执行
     */
    private fun updateEventRecordAfterTaskCompletion(
        eventId: String,
        taskKey: String,
        taskSucceeded: Boolean
    ) {
        if (eventRecordDao == null) {
            return
        }
        try {
            // 使用原子操作更新任务完成状态
            val eventRecord = eventRecordDao.updateTaskStatus(eventId, taskKey, taskSucceeded)
            if (eventRecord == null) {
                logger.warn("Event record not found for eventId: $eventId")
                return
            }

            // 如果任务完成
            if (eventRecord.taskCompleted) {
                if (eventRecord.taskSucceeded) {
                    // 任务成功，删除记录
                    eventRecordDao.deleteByEventId(eventId)
                } else {
                    // 任务失败，增加重试次数并保留记录用于重试
                    eventRecordDao.incrementRetryCount(eventId)
                    logger.info(
                        "Task completed for eventId: $eventId, taskKey: $taskKey, but failed. " +
                            "Event record kept for retry. Retry count incremented."
                    )
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to update event record for eventId: $eventId, taskKey: $taskKey", e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CommonEventBasedReplicaJobExecutor::class.java)
    }
}
