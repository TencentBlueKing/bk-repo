/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.job.CREATED_DATE
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.FOLDER
import com.tencent.bkrepo.job.LAST_MODIFIED_DATE
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.context.FileJobContext
import com.tencent.bkrepo.job.batch.utils.MongoShardingUtils.shardingSequence
import com.tencent.bkrepo.job.config.properties.FolderSizeStatJobProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime

/**
 * 统计folder下的文件size大小
 */
@Component
@EnableConfigurationProperties(FolderSizeStatJobProperties::class)
class FolderSizeStatJob(
    properties: FolderSizeStatJobProperties,
    private val mongoTemplate: MongoTemplate
) : DefaultContextMongoDbJob<FolderSizeStatJob.Node>(properties) {

    override fun start(): Boolean {
        return super.start()
    }

    override fun createJobContext(): FileJobContext {
        return FileJobContext()
    }

    override fun collectionNames(): List<String> {
        return (0 until SHARDING_COUNT)
            .map { "${COLLECTION_NODE_PREFIX}$it" }
            .toList()
    }

    override fun buildQuery(): Query {
        val criteria = Criteria.where(FOLDER).isEqualTo(true)
            .and(REPO).ne("report")
            .and(DELETED_DATE).isEqualTo(null)
        return Query.query(criteria).with(Sort.by(Sort.Direction.DESC, REPO))
    }

    override fun run(row: Node, collectionName: String, context: JobContext) {
        val folderSizeQuery = buildNodeQuery(row.projectId, row.repoName, row.fullPath)
        val folderSize = aggregateComputeSize(folderSizeQuery, collectionName)
        logger.info("stat folder ${row.fullPath} with repo ${row.projectId}|${row.repoName}" +
                        " in $collectionName, size[$folderSize]")
        updateFolderSize(
            projectId = row.projectId,
            repoName = row.repoName,
            fullPath = row.fullPath,
            size = folderSize
        )
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(14)

    override fun mapToEntity(map: Map<String, Any?>): Node {
        return Node(
            id = map[Node::id.name].toString(),
            folder = map[Node::folder.name].toString().toBoolean(),
            path = map[Node::path.name].toString(),
            fullPath = map[Node::fullPath.name].toString(),
            size = map[Node::size.name]?.toString()?.toLong() ?: 0L,
            projectId = map[Node::projectId.name].toString(),
            repoName = map[Node::repoName.name].toString()
        )
    }

    override fun entityClass(): Class<Node> {
        return Node::class.java
    }

    private fun buildNodeQuery(projectId: String, repoName: String, fullPath: String): Criteria {
        return where(Node::projectId).isEqualTo(projectId)
            .and(Node::repoName).isEqualTo(repoName)
            .and(Node::path).isEqualTo(fullPath+StringPool.SLASH)
            .and(DELETED_DATE).isEqualTo(null)
    }

    private fun aggregateComputeSize(criteria: Criteria, collectionName: String): Long {
        val aggregation = Aggregation.newAggregation(
            Aggregation.match(criteria),
            Aggregation.group().sum(Node::size.name).`as`(Node::size.name)
        )
        val aggregateResult = mongoTemplate.aggregate(aggregation, collectionName, HashMap::class.java)
        return aggregateResult.mappedResults.firstOrNull()?.get(Node::size.name) as? Long ?: 0
    }

    fun updateFolderSize(
        projectId: String,
        repoName: String,
        fullPath: String,
        size: Long
    ) {
        val query = Query(
            Criteria.where(PROJECT).isEqualTo(projectId)
                .and(REPO).isEqualTo(repoName)
                .and(FOLDER_PATH).isEqualTo(fullPath)
        )
        val update = Update().set(FOLDER_SIZE, size)
            .setOnInsert(CREATED_DATE, LocalDateTime.now())
            .set(LAST_MODIFIED_DATE, LocalDateTime.now())
        val folderCollectionName = COLLECTION_FOLDER_SIZE_STAT_PREFIX + shardingSequence(projectId, SHARDING_COUNT)
        mongoTemplate.upsert(query, update, folderCollectionName)
    }

    data class Node(
        val id: String,
        val folder: Boolean,
        val path: String,
        val fullPath: String,
        val size: Long,
        val projectId: String,
        val repoName: String
    )

    companion object {
        private val logger = LoggerFactory.getLogger(FolderSizeStatJob::class.java)
        private const val COLLECTION_NODE_PREFIX = "node_"
        private const val COLLECTION_FOLDER_SIZE_STAT_PREFIX = "folder_stat_"
        private const val FOLDER_PATH = "folderPath"
        private const val FOLDER_SIZE = "size"

    }
}
