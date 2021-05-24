package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.stream.rateLimit
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.exception.ReplicaFileFailedException
import com.tencent.bkrepo.replication.job.ReplicationArtifactContext
import com.tencent.bkrepo.replication.pojo.request.RequestBodyUtil
import com.tencent.bkrepo.replication.service.ReplicationArtifactService
import com.tencent.bkrepo.replication.service.RepoDataService
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUpdateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.Request
import org.springframework.stereotype.Service
import java.net.URLEncoder

@Service
class ReplicationArtifactServiceImpl(
    private val repoDataService: RepoDataService,
    private val replicationProperties: ReplicationProperties
) : ReplicationArtifactService {
    override fun replicaFile(context: ReplicationArtifactContext, request: NodeCreateRequest) {
        with(context) {
            // 查询文件
            val localRepoDetail = currentRepoDetail.localRepoDetail
            val inputStream = repoDataService.getFile(request.sha256!!, request.size!!, localRepoDetail)
            val rateLimitInputStream = inputStream.rateLimit(replicationProperties.rateLimit.toBytes())
            val fileRequestBody = RequestBodyUtil.create(MEDIA_TYPE_STREAM, rateLimitInputStream, request.size!!)
            val fullPath = URLEncoder.encode(request.fullPath, "utf-8")
            val builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fullPath, fileRequestBody)
                .addFormDataPart("size", request.size.toString())
                .addFormDataPart("sha256", request.sha256!!)
                .addFormDataPart("md5", request.md5!!)
                .addFormDataPart("userId", request.operator)
            request.metadata?.forEach { (key, value) ->
                builder.addFormDataPart("metadata[$key]", value as String)
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
                    val responseString = response.body()?.string() ?: StringPool.UNKNOWN
                    throw ReplicaFileFailedException("Failed to replica node, response message: $responseString")
                }
            }
        }
    }

    override fun replicaPackageVersionCreatedRequest(
        context: ReplicationArtifactContext,
        request: PackageVersionCreateRequest
    ) {
        with(context) {
            artifactReplicaClient.replicaPackageVersionCreatedRequest(authToken, request)
        }
    }

    override fun replicaNodeCreateRequest(context: ReplicationArtifactContext, request: NodeCreateRequest) {
        with(context) {
            if (request.folder) {
                artifactReplicaClient.replicaNodeCreateRequest(authToken, request)
            } else {
                replicaFile(context, request)
            }
        }
    }

    override fun checkNodeExistRequest(
        context: ReplicationArtifactContext,
        projectId: String,
        repoName: String,
        fullPath: String
    ): Boolean {
        with(context) {
            return artifactReplicaClient.checkNodeExist(authToken, projectId, repoName, fullPath).data ?: false
        }
    }

    override fun replicaNodeRenameRequest(context: ReplicationArtifactContext, request: NodeRenameRequest) {
        with(context) {
            artifactReplicaClient.replicaNodeRenameRequest(authToken, request)
        }
    }

    override fun replicaNodeUpdateRequest(context: ReplicationArtifactContext, request: NodeUpdateRequest) {
        with(context) {
            artifactReplicaClient.replicaNodeUpdateRequest(authToken, request)
        }
    }

    override fun replicaNodeCopyRequest(context: ReplicationArtifactContext, request: NodeMoveCopyRequest) {
        with(context) {
            artifactReplicaClient.replicaNodeCopyRequest(authToken, request)
        }
    }

    override fun replicaNodeMoveRequest(context: ReplicationArtifactContext, request: NodeMoveCopyRequest) {
        with(context) {
            artifactReplicaClient.replicaNodeMoveRequest(authToken, request)
        }
    }

    override fun replicaNodeDeleteRequest(context: ReplicationArtifactContext, request: NodeDeleteRequest) {
        with(context) {
            artifactReplicaClient.replicaNodeDeleteRequest(authToken, request)
        }
    }

    override fun replicaRepoCreateRequest(context: ReplicationArtifactContext, request: RepoCreateRequest) {
        with(context) {
            artifactReplicaClient.replicaRepoCreateRequest(authToken, request)
        }
    }

    override fun replicaRepoUpdateRequest(context: ReplicationArtifactContext, request: RepoUpdateRequest) {
        with(context) {
            artifactReplicaClient.replicaRepoUpdateRequest(authToken, request)
        }
    }

    override fun replicaRepoDeleteRequest(context: ReplicationArtifactContext, request: RepoDeleteRequest) {
        with(context) {
            artifactReplicaClient.replicaRepoDeleteRequest(authToken, request)
        }
    }

    override fun replicaProjectCreateRequest(context: ReplicationArtifactContext, request: ProjectCreateRequest) {
        with(context) {
            artifactReplicaClient.replicaProjectCreateRequest(authToken, request)
        }
    }

    override fun replicaMetadataSaveRequest(context: ReplicationArtifactContext, request: MetadataSaveRequest) {
        with(context) {
            artifactReplicaClient.replicaMetadataSaveRequest(authToken, request)
        }
    }

    override fun replicaMetadataDeleteRequest(context: ReplicationArtifactContext, request: MetadataDeleteRequest) {
        with(context) {
            artifactReplicaClient.replicaMetadataDeleteRequest(authToken, request)
        }
    }

    companion object {
        private val MEDIA_TYPE_STREAM = MediaType.parse(MediaTypes.APPLICATION_OCTET_STREAM)
    }
}
