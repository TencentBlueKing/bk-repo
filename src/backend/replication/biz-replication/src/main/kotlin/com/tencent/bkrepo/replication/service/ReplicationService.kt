package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.StringPool.UNKNOWN
import com.tencent.bkrepo.replication.job.ReplicationContext
import com.tencent.bkrepo.replication.pojo.request.NodeReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.ProjectReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.RepoReplicaRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import org.springframework.stereotype.Service

@Service
class ReplicationService(
    val repoDataService: RepoDataService
) {
    fun replicaFile(context: ReplicationContext, repoReplicaRequest: NodeReplicaRequest) {
        with(context) {
            // 查询文件
            val file = repoDataService.getFile(repoReplicaRequest.sha256, currentRepoDetail.localRepoInfo)
            val fileRequestBody = RequestBody.create(MEDIA_TYPE_STREAM, file)
            val builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, fileRequestBody)
                .addFormDataPart("projectId", repoReplicaRequest.projectId)
                .addFormDataPart("repoName", repoReplicaRequest.repoName)
                .addFormDataPart("fullPath", repoReplicaRequest.fullPath)
                .addFormDataPart("size", repoReplicaRequest.size.toString())
                .addFormDataPart("sha256", repoReplicaRequest.sha256)
                .addFormDataPart("md5", repoReplicaRequest.md5)
                .addFormDataPart("userId", repoReplicaRequest.userId)
            repoReplicaRequest.metadata.forEach { (key, value) ->
                builder.addFormDataPart("metadata[$key]", value)
            }
            val requestBody = builder.build()
            val request = Request.Builder()
                .url("$normalizedUrl/replica/file")
                .post(requestBody)
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val responseString = response.body()?.string() ?: UNKNOWN
                throw RuntimeException("Failed to replica node, response message: $responseString")
            }
        }
    }

    fun replicaRepository(context: ReplicationContext, repoReplicaRequest: RepoReplicaRequest): RepositoryInfo {
        with(context) {
            return replicationClient.replicaRepository(authToken, repoReplicaRequest).data!!
        }
    }

    fun replicaProject(context: ReplicationContext, projectReplicaRequest: ProjectReplicaRequest): ProjectInfo {
        with(context) {
            return replicationClient.replicaProject(authToken, projectReplicaRequest).data!!
        }
    }

    fun replicaNodeCreateRequest(context: ReplicationContext, request: NodeCreateRequest) {
        with(context) {
            if (request.folder) {
                replicationClient.replicaNodeCreateRequest(authToken, request)
            } else {
                NodeReplicaRequest(
                    projectId = request.projectId,
                    repoName = request.repoName,
                    fullPath = request.fullPath,
                    expires = request.expires,
                    size = request.size!!,
                    sha256 = request.sha256!!,
                    md5 = request.md5!!,
                    userId = request.operator
                ).apply { replicaFile(context, this) }
            }
        }
    }

    fun replicaNodeRenameRequest(context: ReplicationContext, request: NodeRenameRequest) {
        with(context) {
            replicationClient.replicaNodeRenameRequest(authToken, request)
        }
    }

    fun replicaNodeCopyRequest(context: ReplicationContext, request: NodeCopyRequest) {
        with(context) {
            replicationClient.replicaNodeCopyRequest(authToken, request)
        }
    }

    fun replicaNodeMoveRequest(context: ReplicationContext, request: NodeMoveRequest) {
        with(context) {
            replicationClient.replicaNodeMoveRequest(authToken, request)
        }
    }

    fun replicaNodeDeleteRequest(context: ReplicationContext, request: NodeDeleteRequest) {
        with(context) {
            replicationClient.replicaNodeDeleteRequest(authToken, request)
        }
    }

    fun replicaRepoCreateRequest(context: ReplicationContext, request: RepoCreateRequest) {
        with(context) {
            replicationClient.replicaRepoCreateRequest(authToken, request)
        }
    }

    fun replicaRepoUpdateRequest(context: ReplicationContext, request: RepoUpdateRequest) {
        with(context) {
            replicationClient.replicaRepoUpdateRequest(authToken, request)
        }
    }

    fun replicaRepoDeleteRequest(context: ReplicationContext, request: RepoDeleteRequest) {
        with(context) {
            replicationClient.replicaRepoDeleteRequest(authToken, request)
        }
    }

    fun replicaProjectCreateRequest(context: ReplicationContext, request: ProjectCreateRequest) {
        with(context) {
            replicationClient.replicaProjectCreateRequest(authToken, request)
        }
    }

    fun replicaMetadataSaveRequest(context: ReplicationContext, request: MetadataSaveRequest) {
        with(context) {
            replicationClient.replicaMetadataSaveRequest(authToken, request)
        }
    }

    fun replicaMetadataDeleteRequest(context: ReplicationContext, request: MetadataDeleteRequest) {
        with(context) {
            replicationClient.replicaMetadataDeleteRequest(authToken, request)
        }
    }

    companion object {
        private val MEDIA_TYPE_STREAM = MediaType.parse(StringPool.MEDIA_TYPE_STREAM)
    }
}
