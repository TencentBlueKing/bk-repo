package com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.storage.innercos.http.toRequestBody
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.CreateCollectionReq
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request

class MilvusClient(
    private val clientProperties: MilvusClientProperties
) {
    val client = OkHttpClient.Builder().addInterceptor {
        val newReq = it.request().newBuilder()

        // add token to header
        with(clientProperties) {
            val token = if (username.isNotEmpty() && password.isNotEmpty()) {
                "Bearer ${username}:${password}"
            } else if (!clientProperties.token.isNullOrEmpty()) {
                "Bearer ${clientProperties.token}"
            } else {
                null
            }
            token?.let { newReq.header(HttpHeaders.AUTHORIZATION, it) }
        }

        it.proceed(newReq.build())
    }.build()

    fun createCollection(req: CreateCollectionReq) {
        val reqBody = req.toJsonString().toRequestBody(MediaTypes.APPLICATION_JSON.toMediaType())
        val request = Request.Builder()
            .post(reqBody)
            .url("${clientProperties.uri}/v2/vectordb/collections/create")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("create collection[${req.collectionName}] failed")
            }
        }
    }

    fun collectionExists(collectionName: String): Boolean {
        TODO()
    }

    fun dropCollection(collectionName: String) {
        TODO()
    }

    fun search() {}

    fun describeIndex(collectionName: String) {}

    fun createIndex(collectionName: String) {}
}