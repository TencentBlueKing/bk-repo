package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.StringPool.UNKNOWN
import com.tencent.bkrepo.replication.exception.ReplicaFileFailedException
import com.tencent.bkrepo.replication.job.ReplicationContext
import com.tencent.bkrepo.replication.pojo.request.RequestBodyUtil
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUpdateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.Request
import org.springframework.stereotype.Service
import java.net.URLEncoder.encode

@Service
class ReplicationService(val repoDataService: RepoDataService) {

    fun replicaFile(context: ReplicationContext, request: NodeCreateRequest) {
        with(context) {
            // 查询文件
            val inputStream = repoDataService.getFile(request.sha256!!, request.size!!, currentRepoDetail.localRepoInfo)
            val fullPath = encode(request.fullPath, "utf-8")
            val fileRequestBody = RequestBodyUtil.create(MEDIA_TYPE_STREAM, inputStream, request.size!!)
            val builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fullPath, fileRequestBody)
                .addFormDataPart("size", request.size.toString())
                .addFormDataPart("sha256", request.sha256!!)
                .addFormDataPart("md5", request.md5!!)
                .addFormDataPart("userId", request.operator)
            request.metadata?.forEach { (key, value) ->
                builder.addFormDataPart("metadata[$key]", value)
            }
            val url = "$normalizedUrl/replica/file/${request.projectId}/${request.repoName}/${request.fullPath}"
            val requestBody = builder.build()
            val httpRequest = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            val response = httpClient.newCall(httpRequest).execute()
            response.use {
                if (!response.isSuccessful) {
                    val responseString = response.body()?.string() ?: UNKNOWN
                    throw ReplicaFileFailedException("Failed to replica node, response message: $responseString")
                }
            }
        }
    }

    fun replicaNodeCreateRequest(context: ReplicationContext, request: NodeCreateRequest) {
        with(context) {
            if (request.folder) {
                replicationClient.replicaNodeCreateRequest(authToken, request)
            } else {
                replicaFile(context, request)
            }
        }
    }

    fun checkNodeExistRequest(
        context: ReplicationContext,
        projectId: String,
        repoName: String,
        fullPath: String
    ): Boolean {
        with(context) {
            return replicationClient.checkNodeExist(authToken, projectId, repoName, fullPath).data ?: run {
                return false
            }
        }
    }

    fun replicaNodeRenameRequest(context: ReplicationContext, request: NodeRenameRequest) {
        with(context) {
            replicationClient.replicaNodeRenameRequest(authToken, request)
        }
    }

    fun replicaNodeUpdateRequest(context: ReplicationContext, request: NodeUpdateRequest) {
        with(context) {
            replicationClient.replicaNodeUpdateRequest(authToken, request)
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
        private val MEDIA_TYPE_STREAM = MediaType.parse(MediaTypes.APPLICATION_OCTET_STREAM)
    }
}
