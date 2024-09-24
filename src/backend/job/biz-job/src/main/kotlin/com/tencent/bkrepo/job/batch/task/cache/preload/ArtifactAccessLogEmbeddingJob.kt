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
import com.tencent.bkrepo.job.batch.base.DefaultContextJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.AiProperties
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.Document
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.EmbeddingModel
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.VectorStore
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.MilvusClient
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.MilvusVectorStore
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.milvus.MilvusVectorStoreProperties
import com.tencent.bkrepo.job.config.properties.ArtifactAccessLogEmbeddingJobProperties
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.gte
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import kotlin.system.measureTimeMillis

@Component
@EnableConfigurationProperties(ArtifactAccessLogEmbeddingJobProperties::class)
@ConditionalOnProperty("job.artifact-access-log-embedding.enabled")
class ArtifactAccessLogEmbeddingJob(
    private val aiProperties: AiProperties,
    private val properties: ArtifactAccessLogEmbeddingJobProperties,
    private val mongoTemplate: MongoTemplate,
    private val milvusClient: MilvusClient,
    private val embeddingModel: EmbeddingModel,
) : DefaultContextJob(properties) {

    override fun getLockAtMostFor(): Duration = Duration.ofDays(7L)

    override fun doStart0(jobContext: JobContext) {
        val lastMonthVectorStore = createVectorStore(1L)
        val curMonthVectorStore = createVectorStore(0L)
        var lastMonthCollectionExists = lastMonthVectorStore.collectionExists()
        val curMonthCollectionExists = curMonthVectorStore.collectionExists()

        if (lastMonthCollectionExists && !curMonthCollectionExists) {
            // 可能由于数据生成过程被中断导致存在上月数据不存在当月数据，需要删除重新生成
            lastMonthVectorStore.dropCollection()
            lastMonthCollectionExists = false
        }

        if (!lastMonthCollectionExists) {
            // 上个月的数据不存在时，使用上个月的访问记录生成数据
            logger.info("collection[${lastMonthVectorStore.collectionName()}] not exists, try to create")
            lastMonthVectorStore.createCollection()
            lastMonthVectorStore.findAccessLogAndInsert(1L)
            logger.info("insert data into collection[${lastMonthVectorStore.collectionName()}] success")
        }

        if (!curMonthCollectionExists) {
            // 当月数据不存在时候，使用月初至今的访问记录生成数据
            logger.info("collection[${curMonthVectorStore.collectionName()}] not exists, try to create")
            curMonthVectorStore.createCollection()
            curMonthVectorStore.findAccessLogAndInsert(0L, before = LocalDate.now().atStartOfDay())
        } else {
            // 已有数据，使用昨日数据生成记录
            logger.info("collection[${curMonthVectorStore.collectionName()}] exists, insert data of last day")
            val startOfToday = LocalDate.now().atStartOfDay()
            val startOfLastDay = LocalDate.now().minusDays(1L).atStartOfDay()
            curMonthVectorStore.findAccessLogAndInsert(0L, after = startOfLastDay, before = startOfToday)
        }
        logger.info("insert data into collection[${curMonthVectorStore.collectionName()}] success")

        // 删除过期数据
        val deprecatedVectorStore = createVectorStore(2L)
        if (deprecatedVectorStore.collectionExists()) {
            logger.info("deprecated collection[${deprecatedVectorStore.collectionName()}] exists")
            deprecatedVectorStore.dropCollection()
            logger.info("drop collection [${deprecatedVectorStore.collectionName()}] success")
        }
    }

    /**
     * 获取访问记录并写入向量数据库
     */
    private fun VectorStore.findAccessLogAndInsert(
        minusMonth: Long,
        after: LocalDateTime? = null,
        before: LocalDateTime? = null
    ) {
        find(minusMonth, after, before) { projectId, paths ->
            val documents = paths.map {
                Document(
                    content = it.key,
                    metadata = mapOf(METADATA_KEY_ACCESS_TIMESTAMP to it.value.joinToString(","))
                )
            }
            val elapsed = measureTimeMillis { insert(documents) }
            logger.info("[$projectId] insert ${documents.size} data into [${collectionName()}] in $elapsed ms")
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


    private fun find(
        minusMonth: Long,
        after: LocalDateTime? = null,
        before: LocalDateTime? = null,
        handler: (String, Map<String, Set<Long>>) -> Unit
    ) {
        val collectionName = collectionName(minusMonth)
        if (!mongoTemplate.collectionExists(collectionName)) {
            logger.warn("mongo collection[$collectionName] not exists")
            return
        }
        val pageSize = properties.batchSize
        var lastId = ObjectId(findFirstObjectId(collectionName, after))
        var querySize: Int
        // buffer存储的内容结构为(projectId, (path, accessTimestamp))
        val projectBuffer = HashMap<String, MutableMap<String, MutableSet<Long>>>()
        val count = mongoTemplate.count(Query(), collectionName)
        var progress = 0
        do {
            val query = Query()
                .addCriteria(Criteria.where(ID).gt(lastId))
                .limit(pageSize)
                .with(Sort.by(ID).ascending())
            query.fields().include(
                TOperateLog::projectId.name,
                TOperateLog::repoName.name,
                TOperateLog::type.name,
                TOperateLog::resourceKey.name,
                TOperateLog::createdDate.name
            )

            val start = System.currentTimeMillis()
            val records = mongoTemplate.find<Map<String, Any?>>(query, collectionName)
            progress += records.size
            logger.info("find access log from db elapsed[${System.currentTimeMillis() - start}]ms, $progress/$count")

            for (record in records) {
                val projectId = record[TOperateLog::projectId.name] as String
                val type = record[TOperateLog::type.name]
                if (type != EventType.NODE_DOWNLOADED.name || projectId !in properties.projects) {
                    continue
                }
                val createDate = LocalDateTime.parse(record[TOperateLog::createdDate.name] as String, ISO_DATE_TIME)
                if (after != null && createDate.isBefore(after) || before != null && createDate.isAfter(before)) {
                    continue
                }

                if (type == EventType.NODE_DOWNLOADED.name && projectId in properties.projects) {
                    projectBuffer.addToBuffer(record)
                    if (projectBuffer[projectId]!!.size >= properties.batchToInsert) {
                        // flush
                        handler(projectId, projectBuffer[projectId]!!)
                        projectBuffer.remove(projectId)
                    }
                }
            }

            querySize = records.size
            lastId = records.last()[ID] as ObjectId
        } while (querySize == pageSize && shouldRun())
        projectBuffer.forEach { (projectId, paths) -> handler(projectId, paths) }
    }

    private fun HashMap<String, MutableMap<String, MutableSet<Long>>>.addToBuffer(record: Map<String, Any?>) {
        val projectId = record[TOperateLog::projectId.name] as String
        val repoName = record[TOperateLog::repoName.name] as String
        val fullPath = record[TOperateLog::resourceKey.name] as String
        val createDate = LocalDateTime.parse(record[TOperateLog::createdDate.name] as String, ISO_DATE_TIME)

        val projectRepoFullPath = if (repoName == PIPELINE) {
            // 流水线仓库路径/p-xxx/b-xxx/xxx中的构建id不参与相似度计算
            val secondSlashIndex = fullPath.indexOf("/", 1)
            val pipelinePath = fullPath.substring(0, secondSlashIndex)
            val artifactPath = fullPath.substring(fullPath.indexOf("/", secondSlashIndex + 1))
            "/$projectId/$repoName$pipelinePath$artifactPath"
        } else {
            "/$projectId/$repoName$fullPath"
        }
        val buffer = getOrPut(projectId) { HashMap() }
        val accessTimestampSet = buffer.getOrPut(projectRepoFullPath) { HashSet() }
        accessTimestampSet.add(createDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }

    private fun findFirstObjectId(collectionName: String, after: LocalDateTime?): String {
        if (after == null) {
            return MIN_OBJECT_ID
        }
        val startDateTime = after.minusHours(1L)
        val endDateTime = after
        val criteria = TOperateLog::createdDate.gte(startDateTime).lt(endDateTime)
        val query = Query(criteria)
        query.fields().include(ID)
        val id = mongoTemplate.findOne<Map<String, Any?>>(query, collectionName)?.let { it[ID] as String }
        return id ?: MIN_OBJECT_ID
    }

    private fun collectionName(minusMonth: Long): String {
        // 查询上个月的记录
        val seq = MonthRangeShardingUtils.shardingSequenceFor(LocalDateTime.now().minusMonths(minusMonth), 1)
        return "artifact_oplog_$seq"
    }

    private fun buildCriteria(projectId: String, after: LocalDateTime?, before: LocalDateTime?): Criteria {
        val criteria = Criteria
            .where(TOperateLog::projectId.name).isEqualTo(projectId)
            .and(TOperateLog::type.name).isEqualTo(EventType.NODE_DOWNLOADED.name)
        if (after != null && before != null) {
            criteria.and(TOperateLog::createdDate.name).gte(after).lt(before)
        } else {
            after?.let { criteria.and(TOperateLog::createdDate.name).gte(it) }
            before?.let { criteria.and(TOperateLog::createdDate.name).lt(it) }
        }
        return criteria
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactAccessLogEmbeddingJob::class.java)
        private const val METADATA_KEY_ACCESS_TIMESTAMP = "access_timestamp"
    }
}
