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

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.common.service.cluster.CommitEdgeEdgeCondition
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.replication.api.cluster.ClusterReplicaTaskClient
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.task.EdgeReplicaTaskRecord
import com.tencent.bkrepo.replication.replica.base.context.ReplicaContext
import com.tencent.bkrepo.repository.api.NodeClient
import feign.FeignException
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Conditional(CommitEdgeEdgeCondition::class)
class EdgeReplicaTaskJob(
    private val clusterProperties: ClusterProperties,
    private val replicationProperties: ReplicationProperties,
    private val nodeClient: NodeClient
) {

    private val centerReplicaTaskClient: ClusterReplicaTaskClient
        by lazy { FeignClientFactory.create(clusterProperties.center, "replication", clusterProperties.self.name) }

    @Suppress("LoopWithTooManyJumpStatements")
    @Scheduled(fixedDelay = 1000L)
    fun run() {
        while (true) {
            logger.info("start to get edge replica task")
            val deferredResult = try {
                centerReplicaTaskClient.getEdgeReplicaTask(clusterProperties.self.name!!)
            } catch (e: FeignException) {
                if (e.status() != HttpStatus.NOT_MODIFIED.value) {
                    logger.error("get edge replica task error: ", e)
                }
                continue
            }
            catch (ignore: Exception) {
                logger.error("get edge replica task error: ", ignore)
                continue
            }
            if (!deferredResult.hasResult()) {
                continue
            }
            val taskRecord = deferredResult.result.toString().readJsonString<Response<EdgeReplicaTaskRecord>>().data!!
            logger.info("get edge replica task: $taskRecord")
            if (!taskRecord.fullPath.isNullOrEmpty()) {
                replicaFile(taskRecord)
            }
            if (!taskRecord.packageName.isNullOrEmpty() && !taskRecord.packageVersion.isNullOrEmpty()) {
                replicaPackageVersion(taskRecord)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun replicaFile(edgeReplicaTaskRecord: EdgeReplicaTaskRecord) {
        with(edgeReplicaTaskRecord) {
            val replicaContext = ReplicaContext(
                taskDetail = taskDetail,
                taskObject = taskObject,
                taskRecord = taskRecord,
                localRepo = localRepo,
                remoteCluster = remoteCluster,
                replicationProperties = replicationProperties
            )
            try {
                val nodeInfo = nodeClient.getNodeDetail(projectId, repoName, fullPath!!).data?.nodeInfo
                    ?: throw NodeNotFoundException(fullPath!!)
                replicaContext.replicator.replicaFile(replicaContext, nodeInfo)
                status = ExecutionStatus.SUCCESS
            } catch (e: Exception) {
                status = ExecutionStatus.FAILED
                errorReason = e.localizedMessage
            } finally {
                logger.info("edge replica task: $edgeReplicaTaskRecord")
                centerReplicaTaskClient.reportEdgeReplicaTaskResult(this)
            }
        }
    }

    private fun replicaPackageVersion(taskRecord: EdgeReplicaTaskRecord) {

    }

    companion object {
        private val logger = LoggerFactory.getLogger(EdgeReplicaTaskJob::class.java)
    }
}
