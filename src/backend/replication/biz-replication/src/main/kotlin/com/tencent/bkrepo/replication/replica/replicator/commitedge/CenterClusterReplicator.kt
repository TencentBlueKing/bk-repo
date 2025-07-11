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

package com.tencent.bkrepo.replication.replica.replicator.commitedge

import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.service.cluster.condition.CommitEdgeCenterCondition
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.replicator.base.internal.ClusterArtifactReplicationHandler
import com.tencent.bkrepo.replication.replica.replicator.standalone.ClusterReplicator
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
        if (node.sha256 == FAKE_SHA256) {
            logger.warn("Node ${node.fullPath} in repo ${node.projectId}|${node.repoName} is link node.")
            return false
        }
        node.clusterNames?.firstOrNull { it != clusterProperties.self.name }
            ?: return super.replicaFile(context, node)
        val edgeReplicaTaskRecord = edgeReplicaTaskRecordService.createNodeReplicaTaskRecord(
            context = context,
            nodeDetail = NodeDetail(node)
        )
        EdgeReplicaContextHolder.setEdgeReplicaTask(edgeReplicaTaskRecord)
        val estimateTime = EdgeReplicaContextHolder.getEstimatedTime(
            timoutCheckHosts = replicationProperties.timoutCheckHosts,
            url = context.remoteCluster.url,
            size = node.size
        )
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
        val estimateTime = EdgeReplicaContextHolder.getEstimatedTime(
            timoutCheckHosts = replicationProperties.timoutCheckHosts,
            url = context.remoteCluster.url,
            size = packageVersion.size
        )
        edgeReplicaTaskRecordService.waitTaskFinish(edgeReplicaTaskRecord.id!!, estimateTime, ChronoUnit.SECONDS)
        return true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CenterClusterReplicator::class.java)
    }
}
