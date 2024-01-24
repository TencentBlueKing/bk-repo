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

package com.tencent.bkrepo.replication.replica.replicator.standalone

import com.google.common.base.Throwables
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.storage.innercos.retry
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.constant.DELAY_IN_SECONDS
import com.tencent.bkrepo.replication.constant.RETRY_COUNT
import com.tencent.bkrepo.replication.enums.WayOfPushArtifact
import com.tencent.bkrepo.replication.exception.ArtifactPushException
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.replica.replicator.base.internal.ClusterArtifactReplicationHandler
import com.tencent.bkrepo.replication.replica.repository.internal.PackageNodeMappings
import com.tencent.bkrepo.replication.replica.context.FilePushContext
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.replicator.Replicator
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 边缘节点数据同步实现类
 * 中心节点 同步到 边缘节点 的同步实现类
 */
@Component
class EdgeNodeReplicator(
    private val localDataManager: LocalDataManager,
    private val artifactReplicationHandler: ClusterArtifactReplicationHandler,
    private val replicationProperties: ReplicationProperties
) : Replicator {

    override fun checkVersion(context: ReplicaContext) {
        // do nothing
    }

    override fun replicaProject(context: ReplicaContext) {
        // do nothing
    }

    override fun replicaRepo(context: ReplicaContext) {
        // do nothing
    }

    override fun replicaPackage(context: ReplicaContext, packageSummary: PackageSummary) {
        // do nothing
    }

    override fun replicaDir(context: ReplicaContext, node: NodeInfo) {
        // do nothing
    }

    override fun replicaPackageVersion(
        context: ReplicaContext,
        packageSummary: PackageSummary,
        packageVersion: PackageVersion
    ): Boolean {
        with(context) {
            if (remoteProjectId.isNullOrBlank() || remoteRepoName.isNullOrBlank()) return true
            var affected = false
            // 文件数据
            PackageNodeMappings.map(
                packageSummary = packageSummary,
                packageVersion = packageVersion,
                type = localRepoType
            ).forEach {
                val node = localDataManager.findNodeDetail(
                    projectId = localProjectId,
                    repoName = localRepoName,
                    fullPath = it
                )
                if (replicaFile(context, node.nodeInfo)) {
                    affected = true
                }
            }
            return affected
        }
    }

    override fun replicaFile(context: ReplicaContext, node: NodeInfo): Boolean {
        with(context) {
            val sha256 = node.sha256.orEmpty()
            var type: String = replicationProperties.pushType
            var downGrade = false
            val remoteRepositoryType = context.remoteRepoType
            retry(times = RETRY_COUNT, delayInSeconds = DELAY_IN_SECONDS) { retry ->
                if (blobReplicaClient?.check(sha256 = sha256, repoType = remoteRepositoryType)?.data != true) {
                    try {
                        artifactReplicationHandler.blobPush(
                            filePushContext = FilePushContext(
                                context = context,
                                name = node.fullPath,
                                size = node.size,
                                sha256 = node.sha256,
                                md5 = node.md5
                            ),
                            pushType = type,
                            downGrade = downGrade
                        )
                    } catch (throwable: Throwable) {
                        logger.warn(
                            "File replica push from edge error $throwable, trace is " +
                                "${Throwables.getStackTraceAsString(throwable)}!"
                        )
                        // 当不支持分块上传时，降级为普通上传
                        if (
                            throwable is ArtifactPushException &&
                            (
                                throwable.code == HttpStatus.METHOD_NOT_ALLOWED.value ||
                                    throwable.code == HttpStatus.UNAUTHORIZED.value
                                )
                        ) {
                            type = WayOfPushArtifact.PUSH_WITH_DEFAULT.value
                            downGrade = true
                        }
                        throw throwable
                    }
                    return true
                }
                return false
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EdgeNodeReplicator::class.java)
    }
}
