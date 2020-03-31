package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.StringPool.UNKNOWN
import com.tencent.bkrepo.replication.job.ReplicationContext
import com.tencent.bkrepo.replication.pojo.request.NodeReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.ProjectReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.RepoReplicaRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
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
    fun replicaProject(context: ReplicationContext, projectReplicaRequest: ProjectReplicaRequest): ProjectInfo {
        with(context) {
            return replicationClient.replicaProject(authToken, projectReplicaRequest).data!!
        }
    }

    fun replicaRepository(context: ReplicationContext, repoReplicaRequest: RepoReplicaRequest): RepositoryInfo {
        with(context) {
            return replicationClient.replicaRepository(authToken, repoReplicaRequest).data!!
        }
    }

    fun replicaNode(context: ReplicationContext, repoReplicaRequest: NodeReplicaRequest) {
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
                .url("$normalizedUrl/replica/node")
                .post(requestBody)
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val responseString = response.body()?.string() ?: UNKNOWN
                throw RuntimeException("Failed to replica node, response message: $responseString")
            }
        }
    }

    companion object {
        private val MEDIA_TYPE_STREAM = MediaType.parse(StringPool.MEDIA_TYPE_STREAM)
    }
}
