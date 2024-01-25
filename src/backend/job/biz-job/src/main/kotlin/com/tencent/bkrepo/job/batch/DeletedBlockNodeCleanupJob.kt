/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.DeletedBlockNodeCleanupJobProperties
import com.tencent.bkrepo.repository.api.FileReferenceClient
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * 清理被标记为删除的node，同时减少文件引用
 */
@Component("JobServiceDeletedBlockNodeCleanupJob")
@EnableConfigurationProperties(DeletedBlockNodeCleanupJobProperties::class)
class DeletedBlockNodeCleanupJob(
    private val properties: DeletedBlockNodeCleanupJobProperties,
    private val fileReferenceClient: FileReferenceClient
) : DefaultContextMongoDbJob<DeletedBlockNodeCleanupJob.BlockNode>(properties) {

    data class BlockNode(
        val id: String,
        val projectId: String,
        val repoName: String,
        val sha256: String,
        val deleted: LocalDateTime?
    )
    override fun getLockAtMostFor(): Duration = Duration.ofDays(7)

    override fun collectionNames(): List<String> {
        val collectionNames = mutableListOf<String>()
        for (i in 0 until SHARDING_COUNT) {
            collectionNames.add("$COLLECTION_NAME_PREFIX$i")
        }
        return collectionNames
    }

    override fun buildQuery(): Query {
        val expireDate = LocalDateTime.now().minusDays(properties.deletedNodeReserveDays)
        return Query.query(where(BlockNode::deleted).lt(expireDate))
    }

    override fun mapToEntity(row: Map<String, Any?>): BlockNode {
        return BlockNode(
            id = row[BlockNode::id.name].toString(),
            projectId = row[BlockNode::projectId.name].toString(),
            repoName = row[BlockNode::repoName.name].toString(),
            sha256 = row[BlockNode::sha256.name].toString(),
            deleted = TimeUtils.parseMongoDateTimeStr(row[BlockNode::deleted.name].toString())
        )
    }

    override fun entityClass(): KClass<BlockNode> {
        return BlockNode::class
    }

    override fun run(row: BlockNode, collectionName: String, context: JobContext) {
        try {
            val nodeQuery = Query.query(Criteria.where(ID).isEqualTo(row.id))
            mongoTemplate.remove(nodeQuery, collectionName)
            decrementFileReference(row)
        } catch (ignored: Exception) {
            logger.error("Clean up deleted block node[$row] failed in collection[$collectionName].", ignored)
        }
    }

    private fun decrementFileReference(blockNode: BlockNode) {
        with(blockNode) {
            val credentialsKey = RepositoryCommonUtils.getRepositoryDetail(projectId, repoName)
                .storageCredentials?.key
            fileReferenceClient.decrement(sha256, credentialsKey).data
        }
    }
    companion object {
        private val logger = LoggerFactory.getLogger(DeletedBlockNodeCleanupJob::class.java)
        private const val COLLECTION_NAME_PREFIX = "block_node_"
    }
}
