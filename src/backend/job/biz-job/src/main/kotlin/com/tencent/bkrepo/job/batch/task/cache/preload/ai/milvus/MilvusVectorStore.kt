/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus

import com.alibaba.fastjson.JSONObject
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.Document
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.EmbeddingModel
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.SearchRequest
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.VectorStore
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.CollectionSchema
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.ConsistencyLevel
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.CreateCollectionReq
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.DataType
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.DeleteVectorReq
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.ElementTypeParams
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.FieldSchema
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.IndexParam
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.InsertVectorReq
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.MetricType
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.request.Params
import io.milvus.common.clientenum.ConsistencyLevelEnum
import io.milvus.param.R
import io.milvus.param.collection.DropCollectionParam
import io.milvus.param.collection.FlushParam
import io.milvus.param.dml.DeleteParam
import io.milvus.param.dml.InsertParam
import io.milvus.param.dml.SearchParam
import io.milvus.response.QueryResultsWrapper
import io.milvus.response.SearchResultsWrapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean

/**
 *  升级到jdk17后迁移到spring-ai
 */
class MilvusVectorStore(
    private val config: MilvusVectorStoreProperties,
    private val milvusClient: MilvusClient,
    private val embeddingModel: EmbeddingModel,
) : VectorStore, InitializingBean {

    override fun insert(documents: List<Document>) {
        val docIdArray = ArrayList<String>()
        val contentArray = ArrayList<String>()
        val metadataArray = ArrayList<String>()
        val embeddingArray = embeddingModel.embed(documents.map { it.content })

        for (document in documents) {
            docIdArray.add(document.id)
            // Use a (future) DocumentTextLayoutFormatter instance to extract
            // the content used to compute the embeddings
            contentArray.add(document.content)
            metadataArray.add(document.metadata.toJsonString())
        }
        val insertRequest = InsertVectorReq(config.databaseName, config.collectionName)

        val fields: MutableList<InsertParam.Field> = ArrayList()
        fields.add(InsertParam.Field(DOC_ID_FIELD_NAME, docIdArray))
        fields.add(InsertParam.Field(CONTENT_FIELD_NAME, contentArray))
        fields.add(InsertParam.Field(METADATA_FIELD_NAME, metadataArray))
        fields.add(InsertParam.Field(EMBEDDING_FIELD_NAME, embeddingArray))

        val insertParam = InsertParam.newBuilder()
            .withDatabaseName(config.databaseName)
            .withCollectionName(config.collectionName)
            .withFields(fields)
            .build()

        val status = milvusClient.insert(insertParam)
        if (status.exception != null) {
            throw RuntimeException("Failed to insert:", status.exception)
        }
        milvusClient.flush(
            FlushParam.newBuilder()
                .withDatabaseName(config.databaseName)
                .addCollectionName(config.collectionName)
                .build()
        )
    }

    override fun delete(ids: Set<String>): Boolean {
        val deleteExpression = "$DOC_ID_FIELD_NAME in [${ids.joinToString(",") { "'$it'" }}]"
        val req = DeleteVectorReq(
            dbName = config.databaseName,
            collectionName = config.collectionName,
            filter = deleteExpression
        )
        milvusClient.delete(req)
        return true
    }

    override fun similaritySearch(request: SearchRequest): List<Document> {
        val nativeFilterExpressions = request.filterExpression ?: ""
        val embedding: List<Float> = embeddingModel.embed(request.query)

        val searchParamBuilder = SearchParam.newBuilder()
            .withDatabaseName(config.databaseName)
            .withCollectionName(config.collectionName)
            .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
            .withMetricType(config.metricType)
            .withOutFields(SEARCH_OUTPUT_FIELDS)
            .withTopK(request.topK)
            .withFloatVectors(listOf(embedding))
            .withVectorFieldName(EMBEDDING_FIELD_NAME)

        if (nativeFilterExpressions.isNotBlank()) {
            searchParamBuilder.withExpr(nativeFilterExpressions)
        }

        val respSearch = milvusClient.search(searchParamBuilder.build())

        if (respSearch.exception != null) {
            throw RuntimeException("Search failed!", respSearch.exception)
        }

        val wrapperSearch = SearchResultsWrapper(respSearch.data.results)

        return wrapperSearch.getRowRecords(0)
            .filter { getResultSimilarity(it) >= request.similarityThreshold }
            .map { rowRecord ->
                val docId = rowRecord[DOC_ID_FIELD_NAME] as String
                val content = rowRecord[CONTENT_FIELD_NAME] as String
                val metadata = rowRecord[METADATA_FIELD_NAME] as JSONObject
                // inject the distance into the metadata.
                metadata[DISTANCE_FIELD_NAME] = 1 - getResultSimilarity(rowRecord)
                Document(content, metadata.innerMap, docId)
            }
            .toList()
    }

    private fun getResultSimilarity(rowRecord: QueryResultsWrapper.RowRecord): Float {
        val distance = rowRecord[DISTANCE_FIELD_NAME] as Float
        return if ((config.metricType == MetricType.IP.name || config.metricType == MetricType.COSINE.name)) {
            distance
        } else {
            (1 - distance)
        }
    }

    override fun afterPropertiesSet() {
        this.createCollection()
    }

    override fun collectionExists(): Boolean {
        return milvusClient.collectionExists(config.databaseName, config.collectionName)
    }

    override fun collectionName(): String {
        return config.collectionName
    }

    // used by the test as well
    override fun createCollection(): Boolean {
        var created = false
        if (!collectionExists()) {
            val fieldSchemas = ArrayList<FieldSchema>()
            fieldSchemas.add(
                FieldSchema(
                    fieldName = DOC_ID_FIELD_NAME,
                    dataType = DataType.VarChar.name,
                    isPrimary = true,
                    elementTypeParams = ElementTypeParams(maxLength = 36),
                )
            )
            fieldSchemas.add(
                FieldSchema(
                    fieldName = CONTENT_FIELD_NAME,
                    dataType = DataType.VarChar.name,
                    elementTypeParams = ElementTypeParams(maxLength = 65535),
                )
            )
            fieldSchemas.add(
                FieldSchema(
                    fieldName = METADATA_FIELD_NAME,
                    dataType = DataType.JSON.name,
                )
            )
            fieldSchemas.add(
                FieldSchema(
                    fieldName = EMBEDDING_FIELD_NAME,
                    dataType = DataType.FloatVector.name,
                    elementTypeParams = ElementTypeParams(dim = embeddingDimensions())
                )
            )

            val indexParams = ArrayList<IndexParam>()
            indexParams.add(
                IndexParam(
                    indexName = null,
                    metricType = config.metricType,
                    fieldName = EMBEDDING_FIELD_NAME,
                    params = Params(
                        indexType = config.indexType,
                        nlist = config.nList,
                    )
                )
            )

            val collectionSchema = CollectionSchema(enableDynamicField = false, fields = fieldSchemas)
            val createCollectionReq = CreateCollectionReq(
                dbName = config.databaseName,
                collectionName = config.collectionName,
                dimension = embeddingDimensions(),
                metricType = config.metricType,
                idType = DataType.VarChar,
                autoId = false,
                primaryFieldName = DOC_ID_FIELD_NAME,
                vectorFieldName = EMBEDDING_FIELD_NAME,
                schema = collectionSchema,
                indexParams = indexParams,
                params = CreateCollectionReq.Params(
                    consistencyLevel = ConsistencyLevel.Strong.name,
                    shardsNum = 2
                )
            )

            milvusClient.createCollection(createCollectionReq)
            created = true
        }

        milvusClient.loadCollection(config.databaseName, config.collectionName)
        return created
    }

    override fun dropCollection() {
        milvusClient.dropCollection(config.databaseName, config.collectionName)
    }

    private fun embeddingDimensions(): Int {
        if (config.embeddingDimension != INVALID_EMBEDDING_DIMENSION) {
            return config.embeddingDimension
        }
        return embeddingModel.dimensions()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MilvusVectorStore::class.java)

        const val INVALID_EMBEDDING_DIMENSION: Int = -1
        const val DOC_ID_FIELD_NAME: String = "doc_id"
        const val CONTENT_FIELD_NAME: String = "content"
        const val METADATA_FIELD_NAME: String = "metadata"
        const val EMBEDDING_FIELD_NAME: String = "embedding"

        // Metadata, automatically assigned by Milvus.
        const val DISTANCE_FIELD_NAME: String = "distance"

        val SEARCH_OUTPUT_FIELDS = listOf(DOC_ID_FIELD_NAME, CONTENT_FIELD_NAME, METADATA_FIELD_NAME)
    }
}
