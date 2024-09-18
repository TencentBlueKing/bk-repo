package com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.storage.innercos.http.toRequestBody
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.CreateCollectionReq
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.DeleteVectorReq
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.DropCollectionReq
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.HasCollectionReq
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.InsertVectorReq
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.LoadCollectionReq
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.SearchVectorReq
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
            token?.let { t -> newReq.header(HttpHeaders.AUTHORIZATION, t) }
        }

        it.proceed(newReq.build())
    }.build()

    fun createCollection(req: CreateCollectionReq) {
        val request = Request.Builder()
            .post(req.toJsonString().toRequestBody(APPLICATION_JSON))
            .url("${clientProperties.uri}/v2/vectordb/collections/create")
            .build()
        client.newCall(request).execute().throwIfFailed<Any, Any> {
            logger.info("create collection[${req.collectionName}] success")
        }
    }

    fun collectionExists(dbName: String, collectionName: String): Boolean {
        val request = Request.Builder()
            .url("${clientProperties.uri}/v2/vectordb/collections/has")
            .post(HasCollectionReq(dbName, collectionName).toJsonString().toRequestBody(APPLICATION_JSON))
            .build()
        return client.newCall(request).execute().throwIfFailed<HasCollectionRes, Boolean> { it!!.has }
    }

    fun dropCollection(dbName: String, collectionName: String) {
        val request = Request.Builder()
            .url("${clientProperties.uri}/v2/vectordb/collections/drop")
            .post(DropCollectionReq(dbName, collectionName).toJsonString().toRequestBody(APPLICATION_JSON))
            .build()
        client.newCall(request).execute().throwIfFailed<Any, Any> {
            logger.info("drop collection[${collectionName}] success")
        }
    }

    fun loadCollection(dbName: String, collectionName: String) {
        val request = Request.Builder()
            .url("${clientProperties.uri}/v2/vectordb/collections/load")
            .post(LoadCollectionReq(dbName, collectionName).toJsonString().toRequestBody(APPLICATION_JSON))
            .build()
        client.newCall(request).execute().throwIfFailed<Any, Any> {
            logger.info("load collection[${collectionName}] success")
        }
    }

    fun insert(req: InsertVectorReq) {
        val request = Request.Builder()
            .url("${clientProperties.uri}/v2/vectordb/entities/insert")
            .post(req.toJsonString().toRequestBody(APPLICATION_JSON))
            .build()
        client.newCall(request).execute().throwIfFailed<Any, Any> {
            logger.info("insert ${req.data.size} data into [${req.collectionName}] success")
        }
    }

    fun delete(req: DeleteVectorReq) {
        val request = Request.Builder()
            .url("${clientProperties.uri}/v2/vectordb/entities/delete")
            .post(req.toJsonString().toRequestBody(APPLICATION_JSON))
            .build()
        client.newCall(request).execute().throwIfFailed<Any, Any> {
            logger.info("delete vector of [${req.dbName}/${req.collectionName}] success")
        }
    }

    fun search(req: SearchVectorReq): Map<String, Any> {
        val request = Request.Builder()
            .url("${clientProperties.uri}/v2/vectordb/entities/search")
            .post(req.toJsonString().toRequestBody(APPLICATION_JSON))
            .build()
        return client.newCall(request).execute().throwIfFailed<Map<String, Any>, Map<String, Any>> { it!! }
    }

    private inline fun <reified T, R> Response.throwIfFailed(handler: (res: T?) -> R): R {
        use {
            if (isSuccessful) {
                val res = body!!.byteStream().readJsonString<MilvusResponse<T>>()
                if (res.code != 0) {
                    throw RuntimeException("request milvus failed, code: $code, message: ${res.message}")
                }
                return handler(res.data)
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
