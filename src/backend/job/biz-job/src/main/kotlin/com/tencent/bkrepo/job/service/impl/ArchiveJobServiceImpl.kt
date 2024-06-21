package com.tencent.bkrepo.job.service.impl

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.archive.constant.ArchiveStorageClass
import com.tencent.bkrepo.archive.request.ArchiveFileRequest
import com.tencent.bkrepo.archive.request.UncompressFileRequest
import com.tencent.bkrepo.common.mongo.dao.util.sharding.HashShardingUtils
import com.tencent.bkrepo.common.query.util.MongoEscapeUtils
import com.tencent.bkrepo.fs.server.constant.FAKE_SHA256
import com.tencent.bkrepo.job.BATCH_SIZE
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.context.NodeContext
import com.tencent.bkrepo.job.batch.task.archive.IdleNodeArchiveJob
import com.tencent.bkrepo.job.batch.task.archive.IdleNodeArchiveJob.Companion.COLLECTION_NAME_PREFIX
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.migrate.MigrateRepoStorageService
import com.tencent.bkrepo.job.pojo.ArchiveRestoreRequest
import com.tencent.bkrepo.job.service.ArchiveJobService
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime

@Service
class ArchiveJobServiceImpl(
    private val archiveJob: IdleNodeArchiveJob,
    private val archiveClient: ArchiveClient,
    private val migrateRepoStorageService: MigrateRepoStorageService,
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
        NodeCommonUtils.findByCollectionAsync(query, BATCH_SIZE, collectionName) {
            val node = archiveJob.mapToEntity(it)
            archiveJob.archiveNode(node, context, storageClass, key, days)
        }.subscribe {
            logger.info("Success to archive project[$projectId], $context")
        }
    }

    override fun restore(request: ArchiveRestoreRequest) {
        val projectId = request.projectId
        val query = Query(buildCriteria(request))
        val index = HashShardingUtils.shardingSequenceFor(projectId, SHARDING_COUNT)
        val collectionName = COLLECTION_NAME_PREFIX.plus(index)
        val context = NodeContext()
        NodeCommonUtils.findByCollectionAsync(query, BATCH_SIZE, collectionName) {
            val node = archiveJob.mapToEntity(it)
            val repoName = node.repoName
            val fullPath = node.fullPath
            val sha256 = node.sha256
            try {
                val repo = RepositoryCommonUtils.getRepositoryDetail(projectId, repoName)
                val credentialKey = if (migrateRepoStorageService.migrating(projectId, repoName)) {
                    repo.oldCredentialsKey
                } else {
                    repo.storageCredentials?.key
                }
                if (it["archived"].toString() == "true") {
                    val req = ArchiveFileRequest(sha256, credentialKey, SYSTEM_USER)
                    archiveClient.restore(req)
                } else {
                    val req = UncompressFileRequest(sha256, credentialKey, SYSTEM_USER)
                    archiveClient.uncompress(req)
                }
                context.count.incrementAndGet()
                context.size.addAndGet(node.size)
                context.success.incrementAndGet()
                logger.info("Restore node $projectId/$repoName$fullPath($sha256)")
            } catch (e: Exception) {
                context.failed.incrementAndGet()
                logger.error("Restore node error $projectId/$repoName$fullPath($sha256)", e)
            } finally {
                context.total.incrementAndGet()
            }
        }.subscribe {
            logger.info("Success to restore project[$projectId], $context")
        }
    }

    fun buildCriteria(request: ArchiveRestoreRequest): Criteria {
        return with(request) {
            val criteria = Criteria.where("folder").isEqualTo(false)
                .and("deleted").isEqualTo(null)
                .and("sha256").ne(FAKE_SHA256)
                .and("projectId").isEqualTo(projectId).orOperator(
                    Criteria.where("archived").isEqualTo(true),
                    Criteria.where("compressed").isEqualTo(true),
                )
            repoName?.let { criteria.and("repoName").isEqualTo(it) }
            prefix?.let { criteria.and("fullPath").regex("^${MongoEscapeUtils.escapeRegex(it)}") }
            val metadataCriteria = metadata.map {
                val elemCriteria = Criteria().andOperator(
                    MetadataModel::key.isEqualTo(it.key),
                    MetadataModel::value.isEqualTo(it.value)
                )
                Criteria.where("metadata").elemMatch(elemCriteria)
            }
            if (metadataCriteria.isEmpty()) {
                criteria
            } else {
                val allCriteria = metadataCriteria.toMutableList()
                allCriteria.add(criteria)
                Criteria().andOperator(allCriteria)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArchiveJobServiceImpl::class.java)
    }
}
