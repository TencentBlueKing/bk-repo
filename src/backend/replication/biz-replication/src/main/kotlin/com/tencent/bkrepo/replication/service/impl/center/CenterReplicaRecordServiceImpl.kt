/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.service.impl.center

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.service.cluster.condition.CommitEdgeCenterCondition
import com.tencent.bkrepo.replication.dao.ReplicaRecordDao
import com.tencent.bkrepo.replication.dao.ReplicaRecordDetailDao
import com.tencent.bkrepo.replication.dao.ReplicaTaskDao
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TReplicaRecord
import com.tencent.bkrepo.replication.model.TReplicaTask
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordInfo
import com.tencent.bkrepo.replication.pojo.task.ReplicaStatus
import com.tencent.bkrepo.replication.replica.base.process.ProgressListener
import com.tencent.bkrepo.replication.service.impl.ReplicaRecordServiceImpl
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service

@Service
@Conditional(CommitEdgeCenterCondition::class)
class CenterReplicaRecordServiceImpl(
    private val replicaRecordDao: ReplicaRecordDao,
    private val replicaRecordDetailDao: ReplicaRecordDetailDao,
    private val replicaTaskDao: ReplicaTaskDao,
    private val clusterProperties: ClusterProperties,
    private val progressListener: ProgressListener
) : ReplicaRecordServiceImpl(
    replicaRecordDao,
    replicaRecordDetailDao,
    replicaTaskDao,
    clusterProperties,
    progressListener
) {

    override fun writeBack(replicaRecordInfo: ReplicaRecordInfo) {
        val task = replicaTaskDao.findByKey(replicaRecordInfo.taskKey)
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, replicaRecordInfo.taskKey)
        replicaRecordDao.insert(convert(replicaRecordInfo))
        logger.info("write back record success: $replicaRecordInfo")

        val query = Query(where(TReplicaTask::key).isEqualTo(task.key))
        val update = Update().set(TReplicaTask::lastExecutionStatus.name, replicaRecordInfo.status)
            .set(TReplicaTask::lastExecutionTime.name, replicaRecordInfo.startTime)
        if (replicaRecordInfo.status == ExecutionStatus.SUCCESS) {
            val srcCluster = SecurityUtils.getClusterName()
            task.remoteClusters.find { it.name == srcCluster }?.run {
                update.pull(TReplicaTask::remoteClusters.name, this)
                if (task.remoteClusters.size == 1) {
                    update.set(TReplicaTask::status.name, ReplicaStatus.COMPLETED)
                }
            }
        }
        replicaTaskDao.upsert(query, update)
    }

    companion object {

        private val logger = LoggerFactory.getLogger(CenterReplicaRecordServiceImpl::class.java)
        private fun convert(replicaRecordInfo: ReplicaRecordInfo): TReplicaRecord {
            return TReplicaRecord(
                taskKey = replicaRecordInfo.taskKey,
                status = replicaRecordInfo.status,
                startTime = replicaRecordInfo.startTime,
                endTime = replicaRecordInfo.endTime,
                errorReason = replicaRecordInfo.errorReason
            )
        }
    }
}
