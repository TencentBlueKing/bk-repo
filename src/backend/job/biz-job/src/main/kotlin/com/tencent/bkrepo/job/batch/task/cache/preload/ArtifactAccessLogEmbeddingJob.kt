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

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
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
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
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
            findAndHandle(1L, null, null) { lastMonthVectorStore.insert(it.values) }
            logger.info("insert data into collection[${lastMonthVectorStore.collectionName()}] success")
        }

        if (!curMonthCollectionExists) {
            // 当月数据不存在时候，使用月初至今的访问记录生成数据
            logger.info("collection[${curMonthVectorStore.collectionName()}] not exists, try to create")
            curMonthVectorStore.createCollection()
            val startOfToday = LocalDate.now().atStartOfDay()
            findAndHandle(0L, null, startOfToday) { curMonthVectorStore.insert(it.values) }
        } else {
            // 已有数据，使用昨日数据生成记录
            logger.info("collection[${curMonthVectorStore.collectionName()}] exists, insert data of last day")
            val startOfToday = LocalDate.now().atStartOfDay()
            val startOfLastDay = LocalDate.now().minusDays(1L).atStartOfDay()
            findAndHandle(0L, startOfLastDay, startOfToday) { curMonthVectorStore.insert(it.values) }
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
     * 对指定项目的访问日志进行向量化
     *
     * @param projectId 需要对访问日志进行向量化的项目
     */
    @Async
    fun embedAccessLog(projectId: String) {
        if (!properties.enabled) {
            return
        }
        logger.info("embed [$projectId] access log start")
        val lastMonthVectorStore = createVectorStore(1L)
        val curMonthVectorStore = createVectorStore(0L)
        if (!lastMonthVectorStore.collectionExists() || !curMonthVectorStore.collectionExists()) {
            throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, "collection has not been created")
        }
        findAndHandleByProjectId(projectId, 1L, null, null) {
            lastMonthVectorStore.insert(it.values)
        }
        findAndHandleByProjectId(projectId, 0L, null, LocalDate.now().atStartOfDay()) {
            curMonthVectorStore.insert(it.values)
        }
        logger.info("embed [$projectId] access log finished")
    }

    private fun VectorStore.insert(paths: Collection<AccessLog>) {
        if (paths.isEmpty()) {
            return
        }
        val documents = paths.map {
            val metadata = mapOf(
                METADATA_KEY_DOWNLOAD_TIMESTAMP to it.downloadTimestamp.joinToString(","),
                METADATA_KEY_ACCESS_COUNT to it.count.toString()
            )
            Document(content = it.projectRepoFullPath, metadata = metadata)
        }
        val elapsed = measureTimeMillis { insert(documents) }
        logger.info("insert ${documents.size} data into [${collectionName()}] in $elapsed ms")
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
        handler: (Map<String, AccessLog>) -> Unit
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
                    handler(projectBuffer[operateLog.projectId]!!)
                    projectBuffer.remove(operateLog.projectId)
                }
            }
        }
        projectBuffer.forEach { (_, paths) -> handler(paths) }
    }

    private fun findAndHandleByProjectId(
        projectId: String,
        minusMonth: Long,
        after: LocalDateTime? = null,
        before: LocalDateTime? = null,
        handler: (Map<String, AccessLog>) -> Unit
    ) {
        // buffer存储的内容结构为(projectId, (path, accessLog))
        val projectBuffer = HashMap<String, MutableMap<String, AccessLog>>()
        findByProject(projectId, minusMonth, after, before) { operateLog ->
            if (projectBuffer.addToBuffer(operateLog)) {
                handler(projectBuffer[operateLog.projectId]!!)
                projectBuffer.remove(operateLog.projectId)
            }
        }
        handler(projectBuffer[projectId] ?: emptyMap())
    }

    private fun HashMap<String, MutableMap<String, AccessLog>>.addToBuffer(operateLog: OperateLog): Boolean {
        with(operateLog) {
            val projectRepoFullPath = projectRepoFullPath(projectId, repoName, resourceKey)
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
            // 只添加间隔超过10分钟的下载时间戳，可能会导致时间戳数量小于count
            val createdTimestamp = createdDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val lastTimestamp = accessLog.downloadTimestamp.lastOrNull() ?: 0L
            if (abs(createdTimestamp - lastTimestamp) > 600_000) {
                accessLog.downloadTimestamp.add(createdTimestamp)
            }
            return buffer.size >= properties.batchToInsert ||
                    accessLog.downloadTimestamp.size >= properties.batchToInsert
        }
    }

    private fun iterateCollection(collectionName: String, startId: ObjectId, handler: (OperateLog) -> Unit) {
        if (!mongoTemplate.collectionExists(collectionName)) {
            logger.warn("mongo collection[$collectionName] not exists")
            return
        }
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
                logger.info("find access log from db elapsed[${end - start}]ms, $progress")
            }

            records.forEach { handler(it) }
            lastId = records.lastOrNull()?.id ?: break
        } while (records.size == query.limit && shouldRun())
    }

    private fun findByProject(
        projectId: String,
        minusMonth: Long,
        after: LocalDateTime?,
        before: LocalDateTime?,
        handler: (OperateLog) -> Unit,
    ) {
        val collectionName = collectionName(minusMonth)
        if (!mongoTemplate.collectionExists(collectionName)) {
            logger.warn("mongo collection[$collectionName] not exists")
            return
        }
        val pageSize = properties.batchSize
        var offset = 0L
        var resultSize: Int
        val criteria = buildProjectCriteria(projectId, after, before)
        var progress = 0
        do {
            val query = Query(criteria)
                .limit(pageSize)
                .skip(offset)
                .with(Sort.by(TOperateLog::projectId.name).ascending())
            query.fields().include(
                ID,
                TOperateLog::projectId.name,
                TOperateLog::repoName.name,
                TOperateLog::type.name,
                TOperateLog::resourceKey.name,
                TOperateLog::createdDate.name
            )

            val start = System.currentTimeMillis()
            val records = mongoTemplate.find(query, OperateLog::class.java, collectionName)
            progress += records.size
            if (progress % 10000 == 0) {
                val end = System.currentTimeMillis()
                logger.info("find [$projectId] access log from db elapsed[${end - start}]ms, $progress")
            }

            // 记录制品访问时间
            records.forEach { handler(it) }
            resultSize = records.size
            offset += records.size
        } while (resultSize == pageSize)
    }

    private fun buildProjectCriteria(projectId: String, after: LocalDateTime?, before: LocalDateTime?): Criteria {
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
        val downloadTimestamp: LinkedHashSet<Long> = LinkedHashSet(),
    )

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactAccessLogEmbeddingJob::class.java)
        private const val METADATA_KEY_DOWNLOAD_TIMESTAMP = "download_timestamp"
        private const val METADATA_KEY_ACCESS_COUNT = "access_count"
    }
}
