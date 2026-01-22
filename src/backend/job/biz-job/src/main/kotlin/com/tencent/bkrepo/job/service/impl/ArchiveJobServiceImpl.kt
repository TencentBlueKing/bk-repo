package com.tencent.bkrepo.job.service.impl

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.archive.constant.ArchiveStorageClass
import com.tencent.bkrepo.archive.request.ArchiveFileRequest
import com.tencent.bkrepo.archive.request.UncompressFileRequest
import com.tencent.bkrepo.common.api.util.EscapeUtils
import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import com.tencent.bkrepo.job.BATCH_SIZE
import com.tencent.bkrepo.job.RESTORE
import com.tencent.bkrepo.job.RESTORE_ARCHIVED
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.context.NodeContext
import com.tencent.bkrepo.job.batch.task.archive.IdleNodeArchiveJob
import com.tencent.bkrepo.job.batch.task.archive.IdleNodeArchiveJob.Companion.COLLECTION_NAME_PREFIX
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.migrate.MigrateRepoStorageService
import com.tencent.bkrepo.job.pojo.ArchiveRestoreRequest
import com.tencent.bkrepo.job.separation.dao.SeparationNodeDao
import com.tencent.bkrepo.job.separation.pojo.NodeFilterInfo
import com.tencent.bkrepo.job.separation.pojo.SeparationContent
import com.tencent.bkrepo.job.separation.pojo.task.SeparationTaskRequest
import com.tencent.bkrepo.job.separation.service.SeparationTaskService
import com.tencent.bkrepo.job.service.ArchiveJobService
import com.tencent.bkrepo.job.service.MigrateArchivedFileService
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.function.Consumer

@Service
class ArchiveJobServiceImpl(
    private val archiveJob: IdleNodeArchiveJob,
    private val archiveClient: ArchiveClient,
    private val migrateRepoStorageService: MigrateRepoStorageService,
    private val migrateArchivedFileService: MigrateArchivedFileService,
    private val separationTaskService: SeparationTaskService,
    private val separationNodeDao: SeparationNodeDao,
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
            archiveJob.archiveNode(node, context, storageClass, key)
        }.subscribe {
            logger.info("Success to archive project[$projectId], $context")
        }
    }

    override fun restore(request: ArchiveRestoreRequest) {
        val projectId = request.projectId
        val repoName = request.repoName
        val prefix = request.prefix
        
        // 先查询冷表中是否存在需要恢复的节点
        checkAndRestoreSeparationNodes(projectId, repoName, prefix)
        
        val query = Query(buildCriteria(request))
        val index = HashShardingUtils.shardingSequenceFor(projectId, SHARDING_COUNT)
        val collectionName = COLLECTION_NAME_PREFIX.plus(index)
        val context = NodeContext()
        NodeCommonUtils
            .findByCollectionAsync(query, BATCH_SIZE, collectionName, RestoreConsumer(context))
            .subscribe { logger.info("Success to restore project[$projectId], $context") }
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
            prefix?.let { criteria.and("fullPath").regex("^${EscapeUtils.escapeRegex(it)}") }
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

    /**
     * 检查并恢复冷表中的节点
     */
    private fun checkAndRestoreSeparationNodes(projectId: String, repoName: String?, prefix: String?) {
        try {
            val separateDates = separationTaskService.findDistinctSeparationDate(projectId, repoName)
            if (separateDates.isEmpty()) {
                logger.info("No separation dates found for project[$projectId], repo[$repoName]")
                return
            }
            
            separateDates.forEach { separationDate ->
                val separationNodes = querySeparationNodes(projectId, repoName, prefix)
                if (separationNodes.isNotEmpty()) {
                    createRestoreTasksForSeparationNodes(projectId, separationNodes, separationDate)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to check and restore separation nodes for project[$projectId], repo[$repoName]", e)
        }
    }
    
    private fun querySeparationNodes(projectId: String, repoName: String?, prefix: String?) = if (prefix != null) {
        separationNodeDao.find(
            Query(
                Criteria.where("projectId").isEqualTo(projectId)
                    .apply { repoName?.let { and("repoName").isEqualTo(it) } }
                    .and("fullPath").regex("^${EscapeUtils.escapeRegex(prefix)}")
                    .and("folder").isEqualTo(false)
                    .and("deleted").isEqualTo(null)
            )
        )
    } else {
        separationNodeDao.find(
            Query(
                Criteria.where("projectId").isEqualTo(projectId)
                    .apply { repoName?.let { and("repoName").isEqualTo(it) } }
                    .and("folder").isEqualTo(false)
                    .and("deleted").isEqualTo(null)
            )
        )
    }
    
    private fun createRestoreTasksForSeparationNodes(
        projectId: String,
        separationNodes: List<*>,
        separationDate: LocalDateTime
    ) {
        logger.info("Found ${separationNodes.size} separation nodes for project[$projectId], creating restore task")
        separationNodes.forEach { node ->
            val n = node as com.tencent.bkrepo.job.separation.model.TSeparationNode
            val taskType = if (n.archived == true) RESTORE_ARCHIVED else RESTORE
            val task = SeparationTaskRequest(
                projectId = projectId,
                repoName = n.repoName,
                type = taskType,
                separateAt = separationDate.format(DateTimeFormatter.ISO_DATE_TIME),
                content = SeparationContent(
                    paths = mutableListOf(NodeFilterInfo(path = n.fullPath))
                )
            )
            separationTaskService.createSeparationTask(task)
            val msg = "Created restore task for separation node: ${n.projectId}/${n.repoName}${n.fullPath}, " +
                "will trigger archive restore after separation recovery"
            logger.info(msg)
        }
    }
    
    private inner class RestoreConsumer(private val context: NodeContext) : Consumer<Map<String, Any?>> {
        override fun accept(nodeMap: Map<String, Any?>) {
            val node = archiveJob.mapToEntity(nodeMap)
            val projectId = node.projectId
            val repoName = node.repoName
            val fullPath = node.fullPath
            val sha256 = node.sha256
            try {
                val repo = RepositoryCommonUtils.getRepositoryDetail(projectId, repoName)
                if (migrateRepoStorageService.migrating(projectId, repoName)) {
                    // 仓库正在迁移时触发恢复，需要先迁移归档存储，随后将恢复到迁移的目标存储中
                    val migrated = migrateArchivedFileService.migrateArchivedFile(
                        repo.oldCredentialsKey, repo.storageCredentials?.key, sha256
                    )
                    if (!migrated) {
                        // 不存在归档文件，无法恢复
                        logger.info("archive file of node[$projectId/$repoName$fullPath($sha256)] not exist")
                        return
                    }
                }
                val credentialKey = repo.storageCredentials?.key
                if (nodeMap["archived"].toString() == "true") {
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
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArchiveJobServiceImpl::class.java)
    }
}
