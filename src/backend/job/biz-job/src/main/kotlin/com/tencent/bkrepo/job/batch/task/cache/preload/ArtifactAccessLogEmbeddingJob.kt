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
import com.tencent.bkrepo.job.batch.base.DefaultContextJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.AiProperties
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.Document
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.EmbeddingModel
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.MilvusVectorStore
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.MilvusVectorStoreProperties
import com.tencent.bkrepo.job.batch.task.cache.preload.ai.VectorStore
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
import java.time.LocalDate
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
        properties.projects.forEach { projectId ->
            val documents =
                findData(projectId, minusMonth, after, before).map { Document(content = it, metadata = emptyMap()) }
            if (documents.isNotEmpty()) {
                insert(documents)
                logger.info("[$projectId] insert ${documents.size} data into collection[${collectionName()}] success")
            }
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
     * 获取有访问记录的路径
     */
    private fun findData(
        projectId: String,
        minusMonth: Long,
        after: LocalDateTime?,
        before: LocalDateTime?,
    ): Set<String> {
        val collectionName = collectionName(minusMonth)
        if (!mongoTemplate.collectionExists(collectionName)) {
            logger.warn("mongo collection[$collectionName] not exists")
            return emptySet()
        }
        val pageSize = BATCH_SIZE
        var lastId = ObjectId(MIN_OBJECT_ID)
        var querySize: Int
        val criteria = buildCriteria(projectId, after, before)
        val accessPaths = HashSet<String>()
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
                val projectRepoFullPath = if (repoName == PIPELINE) {
                    // 流水线仓库路径/p-xxx/b-xxx/xxx中的构建id不参与相似度计算
                    val secondSlashIndex = fullPath.indexOf("/", 1)
                    val pipelinePath = fullPath.substring(0, secondSlashIndex)
                    val artifactPath = fullPath.substring(fullPath.indexOf("/", secondSlashIndex + 1))
                    pipelinePath + artifactPath
                } else {
                    fullPath
                }
                accessPaths.add("/$projectId/$repoName$projectRepoFullPath")
            }

            querySize = data.size
            lastId = data.last()[ID] as ObjectId
        } while (querySize == pageSize && shouldRun())
        return accessPaths
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
        after?.let { criteria.and(TOperateLog::createdDate.name).gte(it) }
        before?.let { criteria.and(TOperateLog::createdDate.name).lt(it) }
        return criteria
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactAccessLogEmbeddingJob::class.java)
    }
}
