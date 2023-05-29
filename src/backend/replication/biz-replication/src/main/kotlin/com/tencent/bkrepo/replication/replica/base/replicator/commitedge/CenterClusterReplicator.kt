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

package com.tencent.bkrepo.replication.replica.base.replicator.commitedge

import com.tencent.bkrepo.common.api.net.speedtest.Counter
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.common.service.cluster.CommitEdgeCenterCondition
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.replica.base.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.base.handler.ClusterArtifactReplicationHandler
import com.tencent.bkrepo.replication.replica.base.replicator.ClusterReplicator
import com.tencent.bkrepo.replication.service.EdgeReplicaTaskRecordService
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Component
import java.time.temporal.ChronoUnit

@Component
@Conditional(CommitEdgeCenterCondition::class)
class CenterClusterReplicator(
    localDataManager: LocalDataManager,
    clusterArtifactReplicationHandler: ClusterArtifactReplicationHandler,
    private val replicationProperties: ReplicationProperties,
    private val clusterProperties: ClusterProperties,
    private val edgeReplicaTaskRecordService: EdgeReplicaTaskRecordService
): ClusterReplicator(localDataManager, clusterArtifactReplicationHandler, replicationProperties) {

    override fun replicaFile(context: ReplicaContext, node: NodeInfo): Boolean {
        node.clusterNames?.firstOrNull { it != clusterProperties.self.name }
            ?: return super.replicaFile(context, node)
        val edgeReplicaTaskRecord = edgeReplicaTaskRecordService.createNodeReplicaTaskRecord(
            context = context,
            nodeDetail = NodeDetail(node)
        )
        EdgeReplicaContextHolder.setEdgeReplicaTask(edgeReplicaTaskRecord)
        val estimateTime = getEstimatedTime(context.remoteCluster.url, node.size)
        edgeReplicaTaskRecordService.waitTaskFinish(edgeReplicaTaskRecord.id!!, estimateTime, ChronoUnit.SECONDS)
        return true
    }

    override fun replicaPackageVersion(
        context: ReplicaContext,
        packageSummary: PackageSummary,
        packageVersion: PackageVersion
    ): Boolean {
        packageVersion.clusterNames?.firstOrNull()
            ?: return super.replicaPackageVersion(context, packageSummary, packageVersion)
        val edgeReplicaTaskRecord = edgeReplicaTaskRecordService.createPackageVersionReplicaTaskRecord(
            context = context,
            packageSummary = packageSummary,
            packageVersion = packageVersion
        )
        EdgeReplicaContextHolder.setEdgeReplicaTask(edgeReplicaTaskRecord)
        val estimateTime = getEstimatedTime(context.remoteCluster.url, packageVersion.size)
        edgeReplicaTaskRecordService.waitTaskFinish(edgeReplicaTaskRecord.id!!, estimateTime, ChronoUnit.SECONDS)
        return true
    }

    private fun getEstimatedTime(url: String, size: Long): Long {
        val timeoutCheckHosts = replicationProperties.timoutCheckHosts
        val rate = timeoutCheckHosts.firstOrNull { url.contains(it[HOST_KEY].toString()) }
            ?.get(RATE_KEY)?.toDouble() ?: DEFAULT_RATE
        val estimatedTime = if (size <= MIN_TIMEOUT * Counter.MB * rate) {
            MIN_TIMEOUT
        } else {
            size / Counter.MB / rate
        } * 1.5
        logger.info("replica to $url maybe will cost $estimatedTime seconds to transfer, size is $size")
        return estimatedTime.toLong()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CenterClusterReplicator::class.java)
        private const val HOST_KEY = "host"
        private const val RATE_KEY = "rate"
        private const val MIN_TIMEOUT = 60.0
        private const val DEFAULT_RATE = 1.0
    }
}
