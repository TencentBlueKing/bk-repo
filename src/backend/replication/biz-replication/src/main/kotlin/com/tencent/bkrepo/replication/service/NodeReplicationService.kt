package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.StringPool.UNKNOWN
import com.tencent.bkrepo.replication.job.ReplicaJobContext
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File

@Service
class NodeReplicationService {

    fun replicaNode(context: ReplicaJobContext, file: File, nodeInfo: NodeInfo, metadata: Map<String, String>?) {
        with(context) {
            val remoteProjectId = context.currentProjectDetail.remoteProjectId
            val remoteRepoName = context.currentRepoDetail.remoteRepoName
            val fileRequestBody = RequestBody.create(MEDIA_TYPE_STREAM, file)
            val builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, fileRequestBody)
                .addFormDataPart("projectId", remoteProjectId)
                .addFormDataPart("repoName", remoteRepoName)
                .addFormDataPart("fullPath", nodeInfo.fullPath)
                .addFormDataPart("folder", false.toString())
                .addFormDataPart("size", nodeInfo.size.toString())
                .addFormDataPart("overwrite", true.toString())
                .addFormDataPart("sha256", nodeInfo.sha256!!)
                .addFormDataPart("md5", nodeInfo.md5!!)
                .addFormDataPart("userId", nodeInfo.createdBy)
            metadata?.forEach { key, value ->
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
                throw RuntimeException("Failed replica node, response message: $responseString")
            }
        }
    }

    companion object {

        private val logger = LoggerFactory.getLogger(NodeReplicationService::class.java)

        private val MEDIA_TYPE_STREAM = MediaType.parse(StringPool.MEDIA_TYPE_STREAM)
    }
}
