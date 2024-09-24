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
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.gte
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
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
        findAndHandle(minusMonth, after, before) { projectId, paths ->
            val documents = paths.map {
                val metadata = mapOf(
                    METADATA_KEY_DOWNLOAD_TIMESTAMP to it.value.downloadTimestamp.joinToString(","),
                    METADATA_KEY_ACCESS_COUNT to it.value.count.toString()
                )
                Document(content = it.key, metadata = metadata)
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


    private fun findAndHandle(
        minusMonth: Long,
        after: LocalDateTime? = null,
        before: LocalDateTime? = null,
        handler: (String, Map<String, AccessLog>) -> Unit
    ) {
        val collectionName = collectionName(minusMonth)
        // buffer存储的内容结构为(projectId, (path, accessLog))
        val projectBuffer = HashMap<String, MutableMap<String, AccessLog>>()
        iterateCollection(collectionName, findFirstObjectId(collectionName, after)) { operateLog ->
            val createDate = operateLog.createdDate
            val outOfDateRange =
                after != null && createDate.isBefore(after) || before != null && createDate.isAfter(before)
            val acceptableType = operateLog.type == EventType.NODE_DOWNLOADED.name
            val acceptableProject = operateLog.projectId in properties.projects

            if (!outOfDateRange && acceptableProject && acceptableType) {
                val shouldFlush = projectBuffer.addToBuffer(operateLog)
                if (shouldFlush) {
                    handler(operateLog.projectId, projectBuffer[operateLog.projectId]!!)
                    projectBuffer.remove(operateLog.projectId)
                }
            }
        }
        projectBuffer.forEach { (projectId, paths) -> handler(projectId, paths) }
    }

    private fun HashMap<String, MutableMap<String, AccessLog>>.addToBuffer(operateLog: OperateLog): Boolean {
        with(operateLog) {
            val projectRepoFullPath = if (repoName == PIPELINE) {
                // 流水线仓库路径/p-xxx/b-xxx/xxx中的构建id不参与相似度计算
                val secondSlashIndex = resourceKey.indexOf("/", 1)
                val pipelinePath = resourceKey.substring(0, secondSlashIndex)
                val artifactPath = resourceKey.substring(resourceKey.indexOf("/", secondSlashIndex + 1))
                "/$projectId/$repoName$pipelinePath$artifactPath"
            } else {
                "/$projectId/$repoName$resourceKey"
            }
            val buffer = getOrPut(projectId) { HashMap() }
            val accessLog = buffer.getOrPut(projectRepoFullPath) {
                AccessLog(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = resourceKey,
                    projectRepoFullPath = projectRepoFullPath
                )
            }
            accessLog.count += 1
            accessLog.downloadTimestamp.add(createdDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            return buffer.size >= properties.batchToInsert ||
                    accessLog.downloadTimestamp.size >= properties.batchToInsert
        }
    }

    private fun iterateCollection(collectionName: String, startId: ObjectId, handler: (OperateLog) -> Unit) {
        if (!mongoTemplate.collectionExists(collectionName)) {
            logger.warn("mongo collection[$collectionName] not exists")
            return
        }
        val count = mongoTemplate.count(Query(), collectionName)
        var progress = 0
        var records: List<OperateLog>
        var lastId = startId
        do {
            val query = buildQuery(lastId)
            val start = System.currentTimeMillis()
            records = mongoTemplate.find(query, OperateLog::class.java, collectionName)

            progress += records.size
            if (progress % 1000000 == 0) {
                val end = System.currentTimeMillis()
                logger.info("find access log from db elapsed[${end - start}]ms, $progress/$count")
            }

            records.forEach { handler(it) }
            lastId = records.lastOrNull()?.id ?: break
        } while (records.size == query.limit && shouldRun())
    }

    private fun buildQuery(lastId: ObjectId): Query {
        val query = Query(Criteria.where(ID).gt(lastId)).limit(properties.batchSize).with(Sort.by(ID).ascending())
        query.fields().include(
            ID,
            TOperateLog::projectId.name,
            TOperateLog::repoName.name,
            TOperateLog::type.name,
            TOperateLog::resourceKey.name,
            TOperateLog::createdDate.name
        )
        return query
    }

    private fun findFirstObjectId(collectionName: String, after: LocalDateTime?): ObjectId {
        if (after == null) {
            return ObjectId(MIN_OBJECT_ID)
        }
        // 找到after之前1小时的记录作为起始遍历点，1小时之前没有访问记录表示访问量较小可以直接从最小ID开始遍历
        val startDateTime = after.minusHours(1L)
        val query = Query(TOperateLog::createdDate.gte(startDateTime).lt(after))
        query.fields().include(ID)
        val id = mongoTemplate.findOne<Map<String, Any?>>(query, collectionName)?.let { it[ID] as ObjectId }
        return id ?: ObjectId(MIN_OBJECT_ID)
    }

    private fun collectionName(minusMonth: Long): String {
        // 查询上个月的记录
        val seq = MonthRangeShardingUtils.shardingSequenceFor(LocalDateTime.now().minusMonths(minusMonth), 1)
        return "artifact_oplog_$seq"
    }

    private data class OperateLog(
        var id: ObjectId,
        val projectId: String,
        val repoName: String,
        val resourceKey: String,
        val createdDate: LocalDateTime,
        val type: String,
    )

    private data class AccessLog(
        val projectId: String,
        val repoName: String,
        val fullPath: String,
        val projectRepoFullPath: String,
        var count: Long = 0,
        val downloadTimestamp: MutableSet<Long> = HashSet(),
    )

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactAccessLogEmbeddingJob::class.java)
        private const val METADATA_KEY_DOWNLOAD_TIMESTAMP = "download_timestamp"
        private const val METADATA_KEY_ACCESS_COUNT = "access_count"
    }
}
