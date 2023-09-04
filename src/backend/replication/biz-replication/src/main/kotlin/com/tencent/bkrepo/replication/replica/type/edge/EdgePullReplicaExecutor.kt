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

package com.tencent.bkrepo.replication.replica.type.edge

import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.constant.REPO_KEY
import com.tencent.bkrepo.common.artifact.exception.ArtifactReceiveException
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
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.UrlUtils
import com.tencent.bkrepo.common.storage.innercos.http.toMediaTypeOrNull
import com.tencent.bkrepo.common.storage.innercos.retry
import com.tencent.bkrepo.replication.api.cluster.ClusterReplicaRecordClient
import com.tencent.bkrepo.replication.api.cluster.ClusterReplicaTaskClient
import com.tencent.bkrepo.replication.pojo.blob.BlobPullRequest
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.pojo.task.setting.ConflictStrategy
import com.tencent.bkrepo.replication.pojo.task.setting.ErrorStrategy
import com.tencent.bkrepo.replication.util.OkHttpClientPool
import com.tencent.bkrepo.replication.replica.base.interceptor.SignInterceptor
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.api.cluster.ClusterNodeClient
import com.tencent.bkrepo.repository.api.cluster.ClusterRepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime

@Component
@Conditional(CommitEdgeEdgeCondition::class)
class EdgePullReplicaExecutor(
    private val clusterProperties: ClusterProperties,
    private val projectClient: ProjectClient,
    private val repositoryClient: RepositoryClient,
    private val storageManager: StorageManager,
    private val replicaRecordService: ReplicaRecordService
) {

    private val centerBlobReplicaClient = OkHttpClientPool.getHttpClient(
        timoutCheckHosts = emptyList(),
        clusterInfo = clusterProperties.center,
        readTimeout = Duration.ofMillis(READ_TIMEOUT),
        writeTimeout = Duration.ofMillis(WRITE_TIMEOUT),
        closeTimeout = Duration.ZERO,
        interceptors = arrayOf(SignInterceptor(clusterProperties.center))
    )
    private val centerReplicaTaskClient: ClusterReplicaTaskClient
        by lazy { FeignClientFactory.create(clusterProperties.center,"replication", clusterProperties.self.name) }
    private val centerNodeClient: ClusterNodeClient
        by lazy { FeignClientFactory.create(clusterProperties.center, "repository", clusterProperties.self.name) }
    private val centerRepoClient: ClusterRepositoryClient
        by lazy { FeignClientFactory.create(clusterProperties.center, "repository", clusterProperties.self.name) }
    private val centerReplicaRecordClient: ClusterReplicaRecordClient
        by lazy { FeignClientFactory.create(clusterProperties.center, "replication", clusterProperties.self.name) }

    fun pullReplica(taskId: String) {
        logger.info("start to execute task: $taskId")
        val task = centerReplicaTaskClient.info(taskId).data!!
        val record = replicaRecordService.startNewRecord(task.key)
        val taskObjectList = centerReplicaTaskClient.listObject(task.key).data.orEmpty()
        var executionStatus = ExecutionStatus.FAILED
        var errorReason: String? = null
        try {
            taskObjectList.forEach { taskObject ->
                retry(3) { replicateCenterNode(taskObject = taskObject, task = task) }
                executionStatus = ExecutionStatus.SUCCESS
                errorReason = null
            }
        } catch (e: Exception) {
            logger.warn("replicate task[$taskId] failed: ${e.localizedMessage}")
            executionStatus = ExecutionStatus.FAILED
            errorReason = e.localizedMessage
            if (task.setting.errorStrategy == ErrorStrategy.FAST_FAIL) {
                throw e
            }
        } finally {
            replicaRecordService.completeRecord(record.id, executionStatus, errorReason)
            centerReplicaRecordClient.writeBack(replicaRecordService.getRecordById(record.id)!!)
        }
    }

    private fun replicateCenterNode(
        taskObject: ReplicaObjectInfo,
        task: ReplicaTaskInfo
    ) {
        val centerProjectId = task.projectId
        val centerRepoName = taskObject.localRepoName
        val fullPath = taskObject.pathConstraints!!.first().path!!
        val localProjectId = taskObject.remoteProjectId ?: task.projectId
        val localRepoName = taskObject.remoteRepoName ?: taskObject.localRepoName
        val centerRepo = centerRepoClient.getRepoDetail(centerProjectId, centerRepoName).data
            ?: throw RepoNotFoundException(centerRepoName)
        val nodeDetail = centerNodeClient.getNodeDetail(
            projectId = centerProjectId,
            repoName = centerRepoName,
            fullPath = fullPath
        ).data ?: throw NodeNotFoundException(fullPath)

        val localRepo = repositoryClient.getRepoDetail(localProjectId, localRepoName).data
            ?: createProjectAndRepo(centerRepo, localProjectId, localRepoName)
        // 手动重试时，需要把仓库信息添加到request attribute中
        HttpContextHolder.getRequestOrNull()?.setAttribute(REPO_KEY, localRepo)
        val blobPullRequest = BlobPullRequest(
            sha256 = nodeDetail.sha256!!,
            range = Range.full(nodeDetail.size),
            storageKey = centerRepo.storageCredentials?.key
        )
        val url = UrlUtils.extractDomain(clusterProperties.center.url) +
            "/replication/cluster/replica/blob/pull"
        val requestBody = blobPullRequest.toJsonString()
            .toRequestBody(MediaTypes.APPLICATION_JSON.toMediaTypeOrNull())
        val request = Request.Builder().url(url).post(requestBody).build()
        centerBlobReplicaClient.newCall(request).execute().use {
            if (!it.isSuccessful) {
                throw ArtifactReceiveException("Blob[${nodeDetail.sha256}] receive error")
            }
            val inputStream = it.body!!.byteStream()
            val artifactFile = ArtifactFileFactory.build(inputStream, nodeDetail.size)
            if (artifactFile.getFileSha256() != nodeDetail.sha256) {
                throw ArtifactReceiveException(
                    "Blob[${nodeDetail.sha256}] receive error: ${artifactFile.getFileSha256()}"
                )
            }
            val nodeCreateRequest = buildNodeCreateRequest(taskObject, task, nodeDetail)
            val localNodeDetail = storageManager.storeArtifactFile(
                request = nodeCreateRequest,
                artifactFile = artifactFile,
                storageCredentials = localRepo.storageCredentials
            )
            logger.info("replicate node[$localNodeDetail] success")
        }
    }

    private fun createProjectAndRepo(
        centerRepo: RepositoryDetail,
        localProjectId: String,
        localRepoName: String,
    ): RepositoryDetail {
        val projectCreateRequest = ProjectCreateRequest(
            name = localProjectId,
            displayName = localProjectId,
            description = null,
            createPermission = true,
            operator = centerRepo.createdBy
        )
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
        size = nodeDetail.size,
        nodeMetadata = nodeDetail.nodeMetadata,
        createdBy = nodeDetail.createdBy,
        createdDate = LocalDateTime.parse(nodeDetail.createdDate),
        lastModifiedBy = nodeDetail.lastModifiedBy,
        lastModifiedDate = LocalDateTime.parse(nodeDetail.lastModifiedDate)
    )

    companion object {
        private val logger = LoggerFactory.getLogger(EdgePullReplicaExecutor::class.java)

        private const val READ_TIMEOUT = 60 * 1000L
        private const val WRITE_TIMEOUT = 60 * 1000L
    }
}
