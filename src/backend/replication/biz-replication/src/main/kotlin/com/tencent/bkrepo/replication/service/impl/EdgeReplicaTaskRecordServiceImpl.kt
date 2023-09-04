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

package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.replication.dao.EdgeReplicaTaskRecordDao
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TEdgeReplicaTaskRecord
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.task.EdgeReplicaTaskRecord
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.service.EdgeReplicaTaskRecordService
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.TemporalUnit

@Service
class EdgeReplicaTaskRecordServiceImpl(
    private val edgeReplicaTaskRecordDao: EdgeReplicaTaskRecordDao,
    private val clusterProperties: ClusterProperties
) : EdgeReplicaTaskRecordService {
    override fun createNodeReplicaTaskRecord(context: ReplicaContext, nodeDetail: NodeDetail): EdgeReplicaTaskRecord {
        with(nodeDetail) {
            val clusterName = clusterNames?.firstOrNull { it != clusterProperties.self.name }
                ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_CLUSTER_NOT_FOUND)
            return edgeReplicaTaskRecordDao.insert(
                TEdgeReplicaTaskRecord(
                    taskId = context.taskDetail.task.id,
                    execClusterName = clusterName,
                    destClusterName = context.remoteCluster.name,
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = fullPath,
                    sha256 = sha256,
                    status = ExecutionStatus.RUNNING,
                    startTime = LocalDateTime.now()
                )
            ).convert(context)
        }
    }

    override fun createPackageVersionReplicaTaskRecord(
        context: ReplicaContext,
        packageSummary: PackageSummary,
        packageVersion: PackageVersion
    ): EdgeReplicaTaskRecord {
        with(packageVersion) {
            val clusterName = clusterNames?.firstOrNull { it != clusterProperties.self.name }
                ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_CLUSTER_NOT_FOUND)
            return edgeReplicaTaskRecordDao.insert(
                TEdgeReplicaTaskRecord(
                    taskId = context.taskDetail.task.id,
                    execClusterName = clusterName,
                    destClusterName = context.remoteCluster.name,
                    projectId = packageSummary.projectId,
                    repoName = packageSummary.repoName,
                    packageKey = packageSummary.key,
                    packageVersion = name,
                    status = ExecutionStatus.RUNNING,
                    startTime = LocalDateTime.now()
                )
            ).convert(context)
        }
    }

    override fun updateStatus(id: String, status: ExecutionStatus, errorReason: String?) {
        val query = Query(Criteria.where(ID).isEqualTo(id))
        val update = Update().set(TEdgeReplicaTaskRecord::status.name, status)
            .set(TEdgeReplicaTaskRecord::errorReason.name, errorReason)
            .set(TEdgeReplicaTaskRecord::endTime.name, LocalDateTime.now())
        edgeReplicaTaskRecordDao.updateFirst(query, update)
        logger.info("update task[$id] success, status[$status], errorReason[$errorReason]")
    }

    override fun delete(id: String) {
        edgeReplicaTaskRecordDao.removeById(id)
    }

    override fun waitTaskFinish(id: String, timeout: Long, timeUnit: TemporalUnit) {
        val startTime = System.currentTimeMillis()
        val timeoutMillis = Duration.of(timeout, timeUnit).toMillis()
        var record = edgeReplicaTaskRecordDao.findById(id)!!
        while (record.status == ExecutionStatus.RUNNING) {
            if (System.currentTimeMillis() - startTime > timeoutMillis) {
                logger.error("wait edge cluster executing task[${record.taskId}] timeout")
                throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_TIMEOUT, record.taskId)
            }
            Thread.sleep(5000)
            record = edgeReplicaTaskRecordDao.findById(id)!!
        }
        if (record.status == ExecutionStatus.FAILED) {
            logger.error("edge execute task[${record.taskId}] failed: ${record.errorReason}")
            throw ErrorCodeException(
                ReplicationMessageCode.REPLICA_TASK_FAILED,
                record.taskId, record.errorReason.orEmpty()
            )
        }
    }

    fun TEdgeReplicaTaskRecord.convert(context: ReplicaContext): EdgeReplicaTaskRecord {
        return EdgeReplicaTaskRecord(
            id = id,
            taskDetail = context.taskDetail,
            taskObject = context.taskObject,
            taskRecord = context.taskRecord,
            localRepo = context.localRepo,
            remoteCluster = context.remoteCluster,
            execClusterName = execClusterName,
            projectId = projectId,
            repoName = repoName,
            fullPath = fullPath,
            sha256 = sha256,
            packageKey = packageKey,
            packageVersion = packageVersion,
            status = status,
            errorReason = errorReason,
            startTime = startTime,
            endTime = endTime
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EdgeReplicaTaskRecordServiceImpl::class.java)
    }
}
