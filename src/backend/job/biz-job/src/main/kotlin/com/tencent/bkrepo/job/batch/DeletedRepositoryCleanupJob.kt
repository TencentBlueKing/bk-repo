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

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.FULL_PATH
import com.tencent.bkrepo.job.LAST_MODIFIED_BY
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.MongoShardingUtils
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.DeletedRepositoryCleanupJobProperties
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * 清理被标记为删除的repository
 */
@Component
@EnableConfigurationProperties(DeletedRepositoryCleanupJobProperties::class)
class DeletedRepositoryCleanupJob(
    private val properties: DeletedRepositoryCleanupJobProperties,
) : DefaultContextMongoDbJob<DeletedRepositoryCleanupJob.Repository>(properties) {


    data class Repository(
        val id: String,
        val projectId: String,
        val name: String,
        val deleted: LocalDateTime?
    )



    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)

    override fun collectionNames(): List<String> {
        return listOf(COLLECTION_REPOSITORY)
    }

    override fun buildQuery(): Query {
        return Query(Criteria.where(DELETED_DATE).ne(null))
    }

    override fun run(row: Repository, collectionName: String, context: JobContext) {
        // 仓库被标记为已删除，且该仓库下不存在任何节点时，删除仓库
        val nodeCollectionName = COLLECTION_NODE_PREFIX+
            MongoShardingUtils.shardingSequence(row.projectId, SHARDING_COUNT)
        deleteNode(row.projectId, row.name, nodeCollectionName)
        if (mongoTemplate.count(buildNodeQuery(row.projectId, row.name), nodeCollectionName) == 0L) {
            val repoQuery = Query.query(Criteria.where(ID).isEqualTo(row.id))
            mongoTemplate.remove(repoQuery, collectionName)
            logger.info("Clean up deleted repository[${row.projectId}/${row.name}] for no nodes remaining!")
        }
    }


    override fun entityClass(): KClass<Repository> {
        return Repository::class
    }

    override fun mapToEntity(row: Map<String, Any?>): Repository {
        return Repository(
            id = row[ID].toString(),
            projectId = row[PROJECT].toString(),
            name = row[Repository::name.name].toString(),
            deleted = TimeUtils.parseMongoDateTimeStr(row[DELETED_DATE].toString()),
        )
    }

    /**
     * 再次刪除非deleted的节点
     */
    private fun deleteNode(projectId: String, repoName: String, collectionName: String) {
        val criteria = Criteria.where(PROJECT).isEqualTo(projectId)
            .and(REPO).isEqualTo(repoName)
            .and(DELETED_DATE).isEqualTo(null)
            .orOperator(
                Criteria.where(FULL_PATH).regex("^${PathUtils.ROOT}"),
                Criteria.where(FULL_PATH).isEqualTo(PathUtils.ROOT)
            )
        val query = Query(criteria)
        val update = Update()
            .set(LAST_MODIFIED_BY, SYSTEM_USER)
            .set(DELETED_DATE, LocalDateTime.now())
        mongoTemplate.updateMulti(query, update, collectionName)
    }

    private fun buildNodeQuery(projectId: String, repoName: String): Query {
        val criteria = Criteria.where(PROJECT).isEqualTo(projectId)
            .and(REPO).isEqualTo(repoName)
        return Query.query(criteria).limit(1)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DeletedRepositoryCleanupJob::class.java)
        private const val COLLECTION_REPOSITORY = "repository"
        private const val COLLECTION_NODE_PREFIX = "node_"

    }
}