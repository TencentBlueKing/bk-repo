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

import com.tencent.bkrepo.auth.constant.PIPELINE
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.common.mongo.dao.util.sharding.MonthRangeShardingUtils
import com.tencent.bkrepo.common.operate.service.model.TOperateLog
import com.tencent.bkrepo.job.BATCH_SIZE
import com.tencent.bkrepo.job.METADATA_KEY_ACCESS_TIMESTAMP
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
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

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

    override fun getLockAtMostFor(): Duration = Duration.ofDays(7L)

    override fun doStart0(jobContext: JobContext) {
        val oldVectorStore = createVectorStore(2L)
        val vectorStore = createVectorStore(1L)

        if (oldVectorStore.collectionExists()) {
            logger.info("old collection exists, maybe new collection created failed, try to drop new collection")
            vectorStore.dropCollection()
        } else if (vectorStore.collectionExists()) {
            logger.info("new collection already created and filled data, skip create")
            return
        }

        vectorStore.createCollection()
        logger.info("create new collection success, start to fill data")

        properties.projects.forEach { vectorStore.findAccessLogAndInsert(it) }

        oldVectorStore.dropCollection()
        logger.info("drop old collection success")
    }

    /**
     * 获取访问记录并写入向量数据库
     */
    private fun VectorStore.findAccessLogAndInsert(projectId: String) {
        val documents = findData(projectId).map {
            val content = it.key
            val metadata = mapOf(METADATA_KEY_ACCESS_TIMESTAMP to it.value.joinToString(","))
            Document(content = content, metadata = metadata)
        }
        if (documents.isNotEmpty()) {
            insert(documents)
            logger.info("insert ${documents.size} data of [$projectId] success")
        }
    }

    private fun createVectorStore(minusMonth: Long): VectorStore {
        val seq = MonthRangeShardingUtils.shardingSequenceFor(LocalDateTime.now().minusMonths(minusMonth), 1)
        val collectionName = "${aiProperties.collectionPrefix}$seq"

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
    private fun findData(projectId: String): Map<String, Set<Long>> {
        val collectionName = collectionName()
        val pageSize = BATCH_SIZE
        var lastId = ObjectId(MIN_OBJECT_ID)
        var querySize: Int
        val criteria = buildCriteria(projectId)
        val accessTimeMap = HashMap<String, MutableSet<Long>>()
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
                val resourceKey = it[TOperateLog::resourceKey.name] as String
                val fullPath = if (repoName == PIPELINE) {
                    // 流水线仓库路径/p-xxx/b-xxx/xxx中的流水线id与构建id不参与相似度计算
                    val secondSlashIndex = resourceKey.indexOf("/", 1)
                    resourceKey.substring(resourceKey.indexOf("/"), secondSlashIndex + 1)
                } else {
                    resourceKey
                }
                val createdDate = TimeUtils.parseMongoDateTimeStr(it[TOperateLog::createdDate.name].toString())!!
                val createdTimestamp = createdDate.atZone(ZoneId.systemDefault()).toInstant().epochSecond
                val accessTime = accessTimeMap.getOrPut("/$projectId/$repoName$fullPath") { HashSet() }
                accessTime.add(createdTimestamp)
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
        private val logger = LoggerFactory.getLogger(ArtifactAccessLogEmbeddingJob::class.java)
    }
}
