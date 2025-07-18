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
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordInfo
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.replica.executor.AbstractReplicaJobExecutor
import com.tencent.bkrepo.replication.replica.type.ReplicaService
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import org.slf4j.LoggerFactory

/**
 * 基于事件消息的实时同步通用逻辑
 */
open class CommonBasedReplicaJobExecutor(
    clusterNodeService: ClusterNodeService,
    localDataManager: LocalDataManager,
    replicaService: ReplicaService,
    replicationProperties: ReplicationProperties,
    val replicaRecordService: ReplicaRecordService
) : AbstractReplicaJobExecutor(clusterNodeService, localDataManager, replicaService, replicationProperties) {

    /**
     * 执行同步
     */
    fun execute(taskDetail: ReplicaTaskDetail, event: ArtifactEvent) {
        if (!replicaObjectCheck(taskDetail, event)) return
        val task = taskDetail.task
        val taskRecord: ReplicaRecordInfo = replicaRecordService.findOrCreateLatestRecord(task.key)
        try {
            val results = task.remoteClusters.map { submit(taskDetail, taskRecord, it, event) }.map { it.get() }
            val replicaOverview = getResultsSummary(results).replicaOverview
            taskRecord.replicaOverview?.let { overview ->
                replicaOverview.success += overview.success
                replicaOverview.failed += overview.failed
                replicaOverview.conflict += overview.conflict
            }
            replicaRecordService.updateRecordReplicaOverview(taskRecord.id, replicaOverview)
            logger.info("Replica ${event.getFullResourceKey()} completed.")
        } catch (exception: Exception) {
            logger.error("Replica ${event.getFullResourceKey()}} failed: $exception", exception)
        }
    }


    /**
     * 判断分发配置内容是否与待分发事件匹配
     */
    open fun replicaObjectCheck(task: ReplicaTaskDetail, event: ArtifactEvent): Boolean {
        return true
    }


    companion object {
        private val logger = LoggerFactory.getLogger(CommonBasedReplicaJobExecutor::class.java)
    }
}
