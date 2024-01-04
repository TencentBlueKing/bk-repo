package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.archive.request.ArchiveFileRequest
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.job.batch.base.MongoDbBatchJob
import com.tencent.bkrepo.job.batch.context.NodeContext
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.config.properties.ArchivedNodeCompleteJobProperties
import com.tencent.bkrepo.job.config.properties.IdleNodeArchiveJobProperties
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.node.service.NodeArchiveRequest
import java.time.Duration
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * 归档节点完成任务
 * 对文件数据已经归档的节点，进行归档处理。具体步骤如下：
 * 1. 找到文件数据对应的待归档节点，如果没有跳至4
 * 2. 检查是否有新的节点使用该文件数据，如果有，则删除归档数据，停止归档。
 * 3. 把相应的节点进行归档
 * 4. 完成文件数据归档。
 * */
@Component
@EnableConfigurationProperties(ArchivedNodeCompleteJobProperties::class)
class ArchivedNodeCompleteJob(
    val properties: ArchivedNodeCompleteJobProperties,
    private val archiveClient: ArchiveClient,
    private val nodeClient: NodeClient,
    private val storageService: StorageService,
    private val idleNodeArchiveJobProperties: IdleNodeArchiveJobProperties,
) : MongoDbBatchJob<ArchivedNodeRestoreJob.ArchiveFile, NodeContext>(properties) {
    override fun createJobContext(): NodeContext {
        return NodeContext()
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(7)

    override fun collectionNames(): List<String> {
        return listOf("archive_file")
    }

    override fun buildQuery(): Query {
        return Query.query(Criteria.where("status").isEqualTo(ArchiveStatus.ARCHIVED))
    }

    override fun run(row: ArchivedNodeRestoreJob.ArchiveFile, collectionName: String, context: NodeContext) {
        with(row) {
            val cutoffTime = LocalDateTime.now().minus(Duration.ofDays(idleNodeArchiveJobProperties.days.toLong()))
            val pendingNodes = listNode(sha256, storageCredentialsKey)
            if (pendingNodes.isEmpty()) {
                logger.info("$sha256($storageCredentialsKey) no nodes need to be archived.")
                val archiveFileRequest = ArchiveFileRequest(
                    sha256 = sha256,
                    storageCredentialsKey = storageCredentialsKey,
                    operator = SYSTEM_USER,
                )
                archiveClient.complete(archiveFileRequest)
                return
            }
            val inUseNodes = pendingNodes.filter { it.lastAccessDate != null && it.lastAccessDate > cutoffTime }
            if (inUseNodes.isNotEmpty()) {
                inUseNodes.forEach {
                    logger.info("New node[$it] created.")
                }
                val archiveFileRequest = ArchiveFileRequest(
                    sha256 = sha256,
                    storageCredentialsKey = storageCredentialsKey,
                    operator = SYSTEM_USER,
                )
                archiveClient.delete(archiveFileRequest)
                logger.info("$sha256/$storageCredentialsKey reuse,delete archive file")
                return
            }
            pendingNodes.forEach {
                val repo = RepositoryCommonUtils.getRepositoryDetail(it.projectId, it.repoName)
                archiveNode(it.projectId, it.repoName, it.fullPath, sha256, repo.storageCredentials)
                context.count.incrementAndGet()
                context.size.addAndGet(it.size)
                logger.info("Success to archive node[$it].")
            }
            val archiveFileRequest = ArchiveFileRequest(
                sha256 = sha256,
                storageCredentialsKey = storageCredentialsKey,
                operator = SYSTEM_USER,
            )
            archiveClient.complete(archiveFileRequest)
        }
    }

    override fun mapToEntity(row: Map<String, Any?>): ArchivedNodeRestoreJob.ArchiveFile {
        return ArchivedNodeRestoreJob.ArchiveFile(
            id = row[ArchivedNodeRestoreJob.ArchiveFile::id.name].toString(),
            storageCredentialsKey = row[ArchivedNodeRestoreJob.ArchiveFile::storageCredentialsKey.name]?.toString(),
            lastModifiedBy = row[ArchivedNodeRestoreJob.ArchiveFile::storageCredentialsKey.name].toString(),
            sha256 = row[ArchivedNodeRestoreJob.ArchiveFile::sha256.name].toString(),
            size = row[ArchivedNodeRestoreJob.ArchiveFile::size.name].toString().toLong(),
        )
    }

    override fun entityClass(): KClass<ArchivedNodeRestoreJob.ArchiveFile> {
        return ArchivedNodeRestoreJob.ArchiveFile::class
    }

    private fun archiveNode(
        projectId: String,
        repoName: String,
        fullPath: String,
        sha256: String,
        storageCredentials: StorageCredentials?,
    ) {
        val nodeArchiveRequest = NodeArchiveRequest(
            projectId = projectId,
            repoName = repoName,
            fullPath = fullPath,
            operator = SYSTEM_USER,
        )
        nodeClient.archiveNode(nodeArchiveRequest)
        // 删除原存储
        storageService.delete(sha256, storageCredentials)
    }

    private fun listNode(sha256: String, storageCredentialsKey: String?): List<NodeCommonUtils.Node> {
        val query = Query.query(
            Criteria.where("sha256").isEqualTo(sha256)
                .and("archived").ne(true)
                .and("deleted").isEqualTo(null),
        )
        return NodeCommonUtils.findNodes(query, storageCredentialsKey)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArchivedNodeCompleteJob::class.java)
    }
}
