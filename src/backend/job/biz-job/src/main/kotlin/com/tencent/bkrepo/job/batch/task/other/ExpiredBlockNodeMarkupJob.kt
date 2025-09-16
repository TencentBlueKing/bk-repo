
package com.tencent.bkrepo.job.batch.task.other

import com.tencent.bkrepo.common.metadata.properties.BlockNodeProperties
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.job.COLLECTION_NAME_BLOCK_NODE
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.ExpiredBlockNodeMarkupJobProperties
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * 标记已过期的节点为已删除
 */
@Component
class ExpiredBlockNodeMarkupJob(
    properties: ExpiredBlockNodeMarkupJobProperties,
    private val blockNodeProperties: BlockNodeProperties,
    private val blockNodeService: BlockNodeService
) : DefaultContextMongoDbJob<ExpiredBlockNodeMarkupJob.BlockNode>(properties) {

    data class BlockNode(
        val projectId: String,
        val repoName: String,
        val nodeFullPath: String,
        val expireDate: LocalDateTime,
        val deleted: LocalDateTime?,
        val uploadId: String?
    )

    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)

    override fun collectionNames(): List<String> {
        val collectionNamePrefix = blockNodeProperties.collectionName.ifEmpty { COLLECTION_NAME_BLOCK_NODE }
        return (0 until SHARDING_COUNT).map { "${collectionNamePrefix}_$it" }
    }

    override fun buildQuery(): Query {
        return Query.query(
            where(BlockNode::expireDate)
                .lt(LocalDateTime.now())
                .and(BlockNode::deleted).isEqualTo(null)
                .and(BlockNode::uploadId).ne(null)
        )
    }

    override fun mapToEntity(row: Map<String, Any?>): BlockNode {
        return BlockNode(
            row[BlockNode::projectId.name].toString(),
            row[BlockNode::repoName.name].toString(),
            row[BlockNode::nodeFullPath.name].toString(),
            TimeUtils.parseMongoDateTimeStr(row[BlockNode::expireDate.name].toString())!!,
            TimeUtils.parseMongoDateTimeStr(row[BlockNode::deleted.name].toString()),
            row[BlockNode::uploadId.name].toString(),
        )
    }

    override fun entityClass(): KClass<BlockNode> {
        return BlockNode::class
    }

    override fun run(row: BlockNode, collectionName: String, context: JobContext) {
        try {
            blockNodeService.deleteBlocks(row.projectId, row.repoName, row.nodeFullPath, row.uploadId)
        } catch (e: Exception) {
            logger.warn("delete expired block node[$row] failed: $e")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ExpiredBlockNodeMarkupJob::class.java)
    }
}
