package com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.storage.innercos.http.toRequestBody
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.CreateCollectionReq
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.DropCollectionReq
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.HasCollectionReq
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.response.HasCollectionRes
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.response.MilvusResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.headersContentLength
import org.slf4j.LoggerFactory

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
        val request = Request.Builder()
            .post(req.toJsonString().toRequestBody(APPLICATION_JSON))
            .url("${clientProperties.uri}/v2/vectordb/collections/create")
            .build()
        client.newCall(request).execute().throwIfFailed {
            logger.info("create collection[${req.collectionName}] success")
        }
    }

    fun collectionExists(dbName: String, collectionName: String): Boolean {
        val request = Request.Builder()
            .url("${clientProperties.uri}/v2/vectordb/collections/has")
            .post(HasCollectionReq(dbName, collectionName).toJsonString().toRequestBody(APPLICATION_JSON))
            .build()
        return client.newCall(request).execute().throwIfFailed { response ->
            val data = response.body!!.byteStream().readJsonString<MilvusResponse<HasCollectionRes>>().data
            data.has
        }
    }

    fun dropCollection(dbName: String, collectionName: String) {
        val request = Request.Builder()
            .url("${clientProperties.uri}/v2/vectordb/collections/drop")
            .post(DropCollectionReq(dbName, collectionName).toJsonString().toRequestBody(APPLICATION_JSON))
            .build()
        return client.newCall(request).execute().throwIfFailed {
            logger.info("drop collection[${collectionName}] success")
        }
    }

    fun search() {}

    fun describeIndex(collectionName: String) {}

    fun createIndex(collectionName: String) {}

    private fun <T> Response.throwIfFailed(handler: (response: Response) -> T): T {
        use {
            if (isSuccessful) {
                return handler(this)
            } else {
                val message = if (headersContentLength() < DEFAULT_MESSAGE_LIMIT) {
                    body?.string()
                } else {
                    ""
                }
                throw RuntimeException("request milvus failed, code: $code, message: $message")
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MilvusClient::class.java)
        private const val DEFAULT_MESSAGE_LIMIT = 4096
        private val APPLICATION_JSON = MediaTypes.APPLICATION_JSON.toMediaType()
    }
}
