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

package com.tencent.bkrepo.job.batch.task.cache.preload

import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.common.mongo.dao.util.sharding.MonthRangeShardingUtils
import com.tencent.bkrepo.common.operate.service.model.TOperateLog
import com.tencent.bkrepo.job.BATCH_SIZE
import com.tencent.bkrepo.job.batch.base.DefaultContextJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.AiProperties
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.Document
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.EmbeddingModel
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.MilvusVectorStore
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.MilvusVectorStoreProperties
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.VectorStore
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.ArtifactAccessLogEmbeddingJobProperties
import io.milvus.client.MilvusServiceClient
import org.bson.types.ObjectId
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
@EnableConfigurationProperties(ArtifactAccessLogEmbeddingJobProperties::class)
@ConditionalOnProperty("job.artifact-access-log-embedding.enabled")
class ArtifactAccessLogEmbeddingJob(
    private val aiProperties: AiProperties,
    private val properties: ArtifactAccessLogEmbeddingJobProperties,
    private val mongoTemplate: MongoTemplate,
    private val milvusClient: MilvusServiceClient,
    private val embeddingModel: EmbeddingModel,
) : DefaultContextJob(properties) {
    override fun doStart0(jobContext: JobContext) {
        properties.projects.forEach { findAndInsertToVectorStore(it) }
    }

    /**
     * 获取访问记录并写入向量数据库
     */
    private fun findAndInsertToVectorStore(projectId: String) {
        val documents = findData(projectId).map {
            val content = it.key
            val metadata = mapOf(METADATA_KEY_ACCESS_HOUR to it.value.joinToString(","))
            Document(content = content, metadata = metadata)
        }
        if (documents.isEmpty()) {
            return
        }

        // 旧表存在时表示新表创建失败或者尚未创建，尝试删除未完成创建的新表
        val oldSeq = MonthRangeShardingUtils.shardingSequenceFor(LocalDateTime.now().minusMonths(2L), 1)
        val oldCollectionName = "${aiProperties.collectionPrefix}$oldSeq"
        val oldVectorStore = createVectorStore(oldCollectionName)

        val seq = MonthRangeShardingUtils.shardingSequenceFor(LocalDateTime.now().minusMonths(1L), 1)
        val collectionName = "${aiProperties.collectionPrefix}$seq"
        val vectorStore = createVectorStore(collectionName)

        if (oldVectorStore.collectionExists()) {
            vectorStore.dropCollection()
        }

        // 创建新表
        vectorStore.createCollection()
        vectorStore.insert(documents)

        // 删除旧表
        oldVectorStore.dropCollection()
    }

    private fun createVectorStore(collectionName: String): VectorStore {
        val config = MilvusVectorStoreProperties(
            databaseName = aiProperties.databaseName,
            collectionName = collectionName,
            embeddingDimension = embeddingModel.dimensions(),
        )
        return MilvusVectorStore(config, milvusClient, embeddingModel)
    }

    /**
     * 获取访问记录
     */
    private fun findData(projectId: String): Map<String, Set<Int>> {
        val collectionName = collectionName()
        val pageSize = BATCH_SIZE
        var lastId = ObjectId(MIN_OBJECT_ID)
        var querySize: Int
        val criteria = buildCriteria(projectId)
        val accessTimeMap = HashMap<String, MutableSet<Int>>()
        do {
            val query = Query(criteria)
                .addCriteria(Criteria.where(ID).gt(lastId))
                .limit(pageSize)
                .with(Sort.by(ID).ascending())
            query.fields().include(
                TOperateLog::repoName.name,
                TOperateLog::resourceKey.name,
                TOperateLog::createdDate.name
            )
            val data = mongoTemplate.find<Map<String, Any?>>(query, collectionName)

            // 记录制品访问时间
            data.forEach {
                val repoName = it[TOperateLog::repoName.name] as String
                val fullPath = it[TOperateLog::resourceKey.name] as String
                val hour = TimeUtils.parseMongoDateTimeStr(it[TOperateLog::createdDate.name].toString())!!.hour
                val accessTime = accessTimeMap.getOrPut("$projectId/$repoName/$fullPath") { HashSet() }
                accessTime.add(hour)
            }

            querySize = data.size
            lastId = data.last()[ID] as ObjectId
        } while (querySize == pageSize && shouldRun())
        return accessTimeMap
    }

    private fun collectionName(): String {
        // 查询上个月的记录
        val seq = MonthRangeShardingUtils.shardingSequenceFor(LocalDateTime.now().minusMonths(1L), 1)
        return "artifact_oplog_$seq"
    }

    private fun buildCriteria(projectId: String): Criteria {
        return Criteria
            .where(TOperateLog::projectId.name).isEqualTo(projectId)
            .and(TOperateLog::type.name).isEqualTo(EventType.NODE_DOWNLOADED.name)
    }

    companion object {
        /**
         * 访问时间点key
         */
        const val METADATA_KEY_ACCESS_HOUR = "accessHour"
    }
}
