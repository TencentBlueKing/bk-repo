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

package com.tencent.bkrepo.job.batch.task.cache.preload.ai

import com.alibaba.fastjson.JSONObject
import io.milvus.client.MilvusServiceClient
import io.milvus.common.clientenum.ConsistencyLevelEnum
import io.milvus.grpc.DataType
import io.milvus.param.MetricType
import io.milvus.param.R
import io.milvus.param.collection.CreateCollectionParam
import io.milvus.param.collection.DropCollectionParam
import io.milvus.param.collection.FieldType
import io.milvus.param.collection.FlushParam
import io.milvus.param.collection.HasCollectionParam
import io.milvus.param.collection.LoadCollectionParam
import io.milvus.param.dml.DeleteParam
import io.milvus.param.dml.InsertParam
import io.milvus.param.dml.SearchParam
import io.milvus.param.index.CreateIndexParam
import io.milvus.param.index.DescribeIndexParam
import io.milvus.response.QueryResultsWrapper
import io.milvus.response.SearchResultsWrapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean

/**
 *  升级到jdk17后迁移到spring-ai
 */
class MilvusVectorStore(
    private val config: MilvusVectorStoreProperties,
    private val milvusClient: MilvusServiceClient,
    private val embeddingModel: EmbeddingModel,
) : VectorStore, InitializingBean {

    override fun insert(documents: List<Document>) {
        val docIdArray = ArrayList<String>()
        val contentArray = ArrayList<String>()
        val metadataArray = ArrayList<JSONObject>()
        val embeddingArray = ArrayList<List<Float>>()

        for (document in documents) {
            val embedding = embeddingModel.embed(document)

            docIdArray.add(document.id)
            // Use a (future) DocumentTextLayoutFormatter instance to extract
            // the content used to compute the embeddings
            contentArray.add(document.content)
            metadataArray.add(JSONObject(document.metadata))
            embeddingArray.add(embedding)
        }

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
        val status = milvusClient.delete(
            DeleteParam.newBuilder()
                .withCollectionName(config.collectionName)
                .withExpr(deleteExpression)
                .build()
        )

        val deleteCount = status.data.deleteCnt
        if (deleteCount != ids.size.toLong()) {
            logger.warn(String.format("Deleted only %s entries from requested %s ", deleteCount, ids.size))
        }

        return status.status == R.Status.Success.code
    }

    override fun similaritySearch(request: SearchRequest): List<Document> {
        val nativeFilterExpressions = request.filterExpression ?: ""
        val embedding: List<Float> = embeddingModel.embed(request.query)

        val searchParamBuilder = SearchParam.newBuilder()
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
        return if ((config.metricType == MetricType.IP || config.metricType == MetricType.COSINE)) {
            distance
        } else {
            (1 - distance)
        }
    }

    override fun afterPropertiesSet() {
        this.createCollection()
    }

    override fun collectionExists(): Boolean {
        return milvusClient.hasCollection(
            HasCollectionParam.newBuilder()
                .withDatabaseName(config.databaseName)
                .withCollectionName(config.collectionName)
                .build()
        ).data
    }

    // used by the test as well
    override fun createCollection(): Boolean {
        var created = false
        if (!collectionExists()) {
            val docIdFieldType = FieldType.newBuilder()
                .withName(DOC_ID_FIELD_NAME)
                .withDataType(DataType.VarChar)
                .withMaxLength(36)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build()
            val contentFieldType = FieldType.newBuilder()
                .withName(CONTENT_FIELD_NAME)
                .withDataType(DataType.VarChar)
                .withMaxLength(65535)
                .build()
            val metadataFieldType = FieldType.newBuilder()
                .withName(METADATA_FIELD_NAME)
                .withDataType(DataType.JSON)
                .build()
            val embeddingFieldType = FieldType.newBuilder()
                .withName(EMBEDDING_FIELD_NAME)
                .withDataType(DataType.FloatVector)
                .withDimension(this.embeddingDimensions())
                .build()

            val createCollectionReq = CreateCollectionParam.newBuilder()
                .withDatabaseName(this.config.databaseName)
                .withCollectionName(this.config.collectionName)
                .withDescription("Spring AI Vector Store")
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .withShardsNum(2)
                .addFieldType(docIdFieldType)
                .addFieldType(contentFieldType)
                .addFieldType(metadataFieldType)
                .addFieldType(embeddingFieldType)
                .build()

            val collectionStatus = milvusClient.createCollection(createCollectionReq)
            if (collectionStatus.exception != null) {
                throw RuntimeException("Failed to create collection", collectionStatus.exception)
            }
            created = true
        }

        val indexDescriptionResponse = milvusClient
            .describeIndex(
                DescribeIndexParam.newBuilder()
                    .withDatabaseName(this.config.databaseName)
                    .withCollectionName(this.config.collectionName)
                    .build()
            )

        if (indexDescriptionResponse.data == null) {
            val indexStatus = milvusClient.createIndex(
                CreateIndexParam.newBuilder()
                    .withDatabaseName(this.config.databaseName)
                    .withCollectionName(this.config.collectionName)
                    .withFieldName(EMBEDDING_FIELD_NAME)
                    .withIndexType(this.config.indexType)
                    .withMetricType(this.config.metricType)
                    .withExtraParam(this.config.indexParameters)
                    .withSyncMode(false)
                    .build()
            )

            if (indexStatus.exception != null) {
                throw RuntimeException("Failed to create Index", indexStatus.exception)
            }
        }

        val loadCollectionStatus = milvusClient.loadCollection(
            LoadCollectionParam.newBuilder()
                .withDatabaseName(this.config.databaseName)
                .withCollectionName(this.config.collectionName)
                .build()
        )

        if (loadCollectionStatus.exception != null) {
            throw RuntimeException("Collection loading failed!", loadCollectionStatus.exception)
        }
        return created
    }

    override fun dropCollection() {
        val exception = milvusClient.dropCollection(
            DropCollectionParam.newBuilder()
                .withDatabaseName(config.databaseName)
                .withCollectionName(config.collectionName)
                .build()
        ).exception
        if (exception != null) {
            throw RuntimeException("Failed to drop collection[${config.collectionName}]", exception)
        }
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
