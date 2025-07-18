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

package com.tencent.bkrepo.replication.replica.type.federation

import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.record.ReplicaOverview
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.replica.executor.AbstractReplicaJobExecutor
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 手动调用仅执行一次的任务
 * 仅针对remote类型节点同步
 */
@Component
class FederationManualReplicaJobExecutor(
    clusterNodeService: ClusterNodeService,
    localDataManager: LocalDataManager,
    replicaService: FederationBasedReplicaService,
    replicationProperties: ReplicationProperties,
    private val replicaRecordService: ReplicaRecordService,
) : AbstractReplicaJobExecutor(clusterNodeService, localDataManager, replicaService, replicationProperties) {

    /**
     * 执行仅执行一次的同步任务，仅限remote类型节点同步
     */
    fun execute(taskDetail: ReplicaTaskDetail) {
        logger.info("The federation full sync task[${taskDetail.task.key}] will be manually executed.")
        var replicaOverview: ReplicaOverview? = null
        val taskRecord = replicaRecordService.findOrCreateLatestRecord(taskDetail.task.key)
            .copy(startTime = LocalDateTime.now())
        try {
            val result = taskDetail.task.remoteClusters.map { submit(taskDetail, taskRecord, it) }.map { it.get() }
            replicaOverview = getResultsSummary(result).replicaOverview
            taskRecord.replicaOverview?.let { overview ->
                replicaOverview.success += overview.success
                replicaOverview.failed += overview.failed
                replicaOverview.conflict += overview.conflict
            }
            replicaRecordService.updateRecordReplicaOverview(taskRecord.id, replicaOverview)
        } catch (ignore: Exception) {
            // 记录异常
            logger.error(
                "Federation full sync task[${taskDetail.task.key}]," +
                    " record[${taskRecord.id}] failed: $ignore", ignore
            )
        } finally {
            replicaOverview?.let {
                replicaRecordService.updateRecordReplicaOverview(taskRecord.id, replicaOverview)
            }
            logger.info("Federation full sync task[${taskDetail.task.key}], record[${taskRecord.id}] finished")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FederationManualReplicaJobExecutor::class.java)
    }
}
