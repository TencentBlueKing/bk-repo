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

import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.common.service.cluster.CommitEdgeEdgeCondition
import com.tencent.bkrepo.common.service.exception.RemoteErrorCodeException
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.replication.api.cluster.ClusterBlobReplicaClient
import com.tencent.bkrepo.replication.api.cluster.ClusterReplicaTaskClient
import com.tencent.bkrepo.replication.pojo.blob.BlobPullRequest
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.ReplicaStatus
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.pojo.task.setting.ConflictStrategy
import com.tencent.bkrepo.replication.pojo.task.setting.ErrorStrategy
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.api.cluster.ClusterNodeClient
import com.tencent.bkrepo.repository.api.cluster.ClusterRepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
@Conditional(CommitEdgeEdgeCondition::class)
class EdgePullReplicaExecutor(
    private val clusterProperties: ClusterProperties,
    private val clusterBlobReplicaClient: ClusterBlobReplicaClient,
    private val projectClient: ProjectClient,
    private val repositoryClient: RepositoryClient,
    private val replicaTaskService: ReplicaTaskService,
    private val storageManager: StorageManager
) {

    private val centerReplicaTaskClient: ClusterReplicaTaskClient
        by lazy { FeignClientFactory.create(clusterProperties.center) }
    private val centerNodeClient: ClusterNodeClient
        by lazy { FeignClientFactory.create(clusterProperties.center, "repository", clusterProperties.self.name) }
    private val centerRepoClient: ClusterRepositoryClient
        by lazy { FeignClientFactory.create(clusterProperties.center, "repository", clusterProperties.self.name) }

    @EventListener(EdgePullReplicaTaskEvent::class)
    fun pullReplica(event: EdgePullReplicaTaskEvent) {
        replicaTaskService.listTaskByType(ReplicaType.EDGE_PULL, event.lastId, event.size, ReplicaStatus.WAITING)
            .forEach { task ->
                val taskObjectList = centerReplicaTaskClient.listObject(task.key).data.orEmpty()
                taskObjectList.forEach { taskObject ->
                    try {
                        val centerProjectId = task.projectId
                        val centerRepoName = taskObject.localRepoName
                        val localProjectId = taskObject.remoteProjectId ?: task.projectId
                        val localRepoName = taskObject.remoteRepoName ?: taskObject.localRepoName
                        val fullPath = taskObject.pathConstraints!!.first().path!!
                        val centerRepo = centerRepoClient.getRepoDetail(centerProjectId, centerRepoName).data
                            ?: throw RepoNotFoundException(centerRepoName)
                        val nodeDetail = centerNodeClient.getNodeDetail(
                            projectId = centerProjectId,
                            repoName = centerRepoName,
                            fullPath = fullPath
                        ).data ?: throw NodeNotFoundException(fullPath)

                        val localRepo = repositoryClient.getRepoDetail(localProjectId, localRepoName).data
                            ?: createProjectAndRepo(centerRepo, localProjectId, localRepoName)
                        val pullRequest = BlobPullRequest(
                            sha256 = nodeDetail.sha256!!,
                            range = Range.full(nodeDetail.size),
                            storageKey = centerRepo.storageCredentials?.key
                        )
                        val inputStream = clusterBlobReplicaClient.pull(pullRequest).body!!.inputStream
                        val artifactFile = ArtifactFileFactory.build(inputStream, nodeDetail.size)
                        val nodeCreateRequest = buildNodeCreateRequest(taskObject, task, nodeDetail)
                        storageManager.storeArtifactFile(nodeCreateRequest, artifactFile, localRepo.storageCredentials)
                    } catch (e: Exception) {
                        logger.warn("replicate taskObject[$taskObject] failed: ${e.localizedMessage}")
                        if (task.setting.errorStrategy == ErrorStrategy.FAST_FAIL) {
                            throw e
                        }
                    }
                }
            }
    }

    private fun createProjectAndRepo(
        centerRepo: RepositoryDetail,
        localProjectId: String,
        localRepoName: String,
    ): RepositoryDetail {
        val projectCreateRequest = ProjectCreateRequest(localProjectId, localRepoName, null, centerRepo.createdBy)
        try {
            projectClient.createProject(projectCreateRequest)
        } catch (e: RemoteErrorCodeException) {
            if (e.errorCode != ArtifactMessageCode.PROJECT_EXISTED.getCode()) {
                throw e
            }
        }

        val repoCreateRequest = RepoCreateRequest(
            projectId = localProjectId,
            name = localRepoName,
            type = centerRepo.type,
            category = centerRepo.category,
            public = centerRepo.public,
            description = centerRepo.description,
            operator = centerRepo.createdBy
        )
        return repositoryClient.createRepo(repoCreateRequest).data!!
    }

    private fun buildNodeCreateRequest(
        taskObject: ReplicaObjectInfo,
        task: ReplicaTaskInfo,
        nodeDetail: NodeDetail
    ) = NodeCreateRequest(
        projectId = taskObject.remoteProjectId ?: task.projectId,
        repoName = taskObject.remoteRepoName ?: taskObject.localRepoName,
        fullPath = nodeDetail.fullPath,
        folder = false,
        overwrite = task.setting.conflictStrategy == ConflictStrategy.OVERWRITE,
        sha256 = nodeDetail.sha256,
        md5 = nodeDetail.md5,
        nodeMetadata = nodeDetail.nodeMetadata,
        createdBy = nodeDetail.createdBy,
        createdDate = LocalDateTime.parse(nodeDetail.createdDate),
        lastModifiedBy = nodeDetail.lastModifiedBy,
        lastModifiedDate = LocalDateTime.parse(nodeDetail.lastModifiedDate)
    )

    companion object {
        private val logger = LoggerFactory.getLogger(EdgePullReplicaExecutor::class.java)
    }
}
