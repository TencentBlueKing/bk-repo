/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.replication.job.replicator

import com.tencent.bkrepo.common.artifact.stream.rateLimit
import com.tencent.bkrepo.replication.job.ReplicaContext
import com.tencent.bkrepo.replication.mapping.PackageNodeMappings
import com.tencent.bkrepo.replication.pojo.blob.InputStreamMultipartFile
import com.tencent.bkrepo.replication.pojo.request.PackageVersionExistCheckRequest
import com.tencent.bkrepo.replication.pojo.task.setting.ConflictStrategy
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 集群间数据同步类
 */
@Component
class ClusterReplicator : ScheduledReplicator() {
    override fun replicaProject(context: ReplicaContext) {
        with(context) {
            val localProject = localDataManager.findProjectById(localProjectId)
            val request = ProjectCreateRequest(
                name = remoteProjectId,
                displayName = remoteProjectId,
                description = localProject.description,
                operator = localProject.createdBy
            )
            artifactReplicaClient.replicaProjectCreateRequest(request)
        }
    }

    override fun replicaRepo(context: ReplicaContext) {
        with(context) {
            val localRepo = localDataManager.findRepoByName(localProjectId, localRepoName, localRepoType.name)
            val request = RepoCreateRequest(
                projectId = remoteProjectId,
                name = remoteRepoName,
                type = remoteRepoType,
                category = localRepo.category,
                public = localRepo.public,
                description = localRepo.description,
                configuration = localRepo.configuration,
                operator = localRepo.createdBy
            )
            context.remoteRepo = artifactReplicaClient.replicaRepoCreateRequest(request).data!!
        }
    }

    override fun replicaPackage(context: ReplicaContext, packageSummary: PackageSummary) {
        // do nothing
    }

    override fun replicaPackageVersion(
        context: ReplicaContext,
        packageSummary: PackageSummary,
        packageVersion: PackageVersion
    ): Boolean {
        with(context) {
            // 包版本冲突检查
            val fullPath = "${packageSummary.name}-${packageVersion.name}"
            val checkRequest = PackageVersionExistCheckRequest(
                projectId = remoteProjectId,
                repoName = remoteRepoName,
                packageKey = packageSummary.key,
                versionName = packageVersion.name
            )
            if (artifactReplicaClient.checkPackageVersionExist(checkRequest).data == true) {
                when (task.setting.conflictStrategy) {
                    ConflictStrategy.SKIP -> return false
                    ConflictStrategy.FAST_FAIL -> throw IllegalArgumentException("Package [$fullPath] conflict.")
                    else -> {
                        // do nothing
                    }
                }
            }
            // 文件数据
            PackageNodeMappings.map(
                type = localRepoType,
                key = packageSummary.key,
                version = packageVersion.name,
                extension = packageVersion.extension
            ).forEach {
                val node = localDataManager.findNodeDetail(
                    projectId = localProjectId,
                    repoName = localRepoName,
                    fullPath = it
                )
                replicaFile(context, node.nodeInfo)
            }
            // 包数据
            val request = PackageVersionCreateRequest(
                projectId = remoteProjectId,
                repoName = remoteRepoName,
                packageName = packageSummary.name,
                packageKey = packageSummary.key,
                packageType = packageSummary.type,
                packageDescription = packageSummary.description,
                versionName = packageVersion.name,
                size = packageVersion.size,
                manifestPath = null,
                artifactPath = packageVersion.contentPath,
                stageTag = packageVersion.stageTag,
                metadata = packageVersion.metadata,
                extension = packageVersion.extension,
                overwrite = true,
                createdBy = packageVersion.createdBy
            )
            artifactReplicaClient.replicaPackageVersionCreatedRequest(request)
        }
        return true
    }

    override fun replicaFile(context: ReplicaContext, node: NodeInfo): Boolean {
        with(context) {
            return buildNodeCreateRequest(this, node)?.let {
                val artifactInputStream = localDataManager.getBlobData(it.sha256!!, it.size!!, localRepo)
                val rateLimitInputStream = artifactInputStream.rateLimit(replicationProperties.rateLimit.toBytes())
                val file = InputStreamMultipartFile(rateLimitInputStream, it.size!!)
                // 1. 同步文件数据
                blobReplicaClient.push(
                    file = file,
                    sha256 = it.sha256.orEmpty(),
                    storageKey = remoteRepo.storageCredentials?.key
                )
                // 2. 同步节点信息
                artifactReplicaClient.replicaNodeCreateRequest(it)
                true
            } ?: false
        }
    }

    override fun replicaDir(context: ReplicaContext, node: NodeInfo) {
        with(context) {
            buildNodeCreateRequest(this, node)?.let {
                artifactReplicaClient.replicaNodeCreateRequest(it)
            }
        }
    }

    private fun buildNodeCreateRequest(context: ReplicaContext, node: NodeInfo): NodeCreateRequest? {
        with(context) {
            val fullPath = "${node.projectId}/${node.repoName}${node.fullPath}"
            // 节点冲突检查
            if (artifactReplicaClient.checkNodeExist(remoteProjectId, remoteRepoName, node.fullPath).data == true) {
                when (task.setting.conflictStrategy) {
                    ConflictStrategy.SKIP -> return null
                    ConflictStrategy.FAST_FAIL -> throw IllegalArgumentException("File[$fullPath] conflict.")
                    else -> {
                        // do nothing
                    }
                }
            }
            // 查询元数据
            val metadata = if (task.setting.includeMetadata) node.metadata else emptyMap()
            return NodeCreateRequest(
                projectId = remoteProjectId,
                repoName = remoteRepoName,
                fullPath = node.fullPath,
                folder = node.folder,
                overwrite = true,
                size = node.size,
                sha256 = node.sha256!!,
                md5 = node.md5!!,
                metadata = metadata,
                operator = node.createdBy,
                createdBy = node.createdBy,
                createdDate = LocalDateTime.parse(node.createdDate, DateTimeFormatter.ISO_DATE_TIME),
                lastModifiedBy = node.lastModifiedBy,
                lastModifiedDate = LocalDateTime.parse(node.lastModifiedDate, DateTimeFormatter.ISO_DATE_TIME)
            )
        }
    }
}
