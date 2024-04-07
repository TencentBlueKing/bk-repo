package com.tencent.bkrepo.job.service.impl

import com.tencent.bkrepo.archive.constant.ArchiveStorageClass
import com.tencent.bkrepo.common.mongo.dao.util.sharding.HashShardingUtils
import com.tencent.bkrepo.fs.server.constant.FAKE_SHA256
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.context.NodeContext
import com.tencent.bkrepo.job.batch.task.archive.IdleNodeArchiveJob
import com.tencent.bkrepo.job.batch.task.archive.IdleNodeArchiveJob.Companion.COLLECTION_NAME_PREFIX
import com.tencent.bkrepo.job.service.ArchiveJobService
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime

@Service
class ArchiveJobServiceImpl(
    private val archiveJob: IdleNodeArchiveJob,
    val reactiveMongoTemplate: ReactiveMongoTemplate,
) : ArchiveJobService {
    override fun archive(projectId: String, key: String, days: Int, storageClass: ArchiveStorageClass) {
        val now = LocalDateTime.now()
        val cutoffTime = now.minus(Duration.ofDays(days.toLong()))
        val query = Query.query(
            Criteria.where("folder").isEqualTo(false)
                .and("deleted").isEqualTo(null)
                .and("sha256").ne(FAKE_SHA256)
                .and("archived").ne(true)
                .and("compressed").ne(true)
                .and("projectId").isEqualTo(projectId)
                .orOperator(
                    Criteria.where("lastAccessDate").isEqualTo(null),
                    Criteria.where("lastAccessDate").lt(cutoffTime),
                ),
        )
        val index = HashShardingUtils.shardingSequenceFor(projectId, SHARDING_COUNT)
        val collectionName = COLLECTION_NAME_PREFIX.plus(index)
        val context = NodeContext()
        reactiveMongoTemplate.find(query, IdleNodeArchiveJob.Node::class.java, collectionName)
            .doFinally { logger.info(context.toString()) }
            .subscribe {
                archiveJob.archiveNode(it, context, storageClass, key, days)
            }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArchiveJobServiceImpl::class.java)
    }
}
