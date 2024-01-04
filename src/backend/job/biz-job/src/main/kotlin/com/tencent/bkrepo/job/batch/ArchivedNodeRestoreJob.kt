package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.archive.request.ArchiveFileRequest
import com.tencent.bkrepo.job.batch.base.MongoDbBatchJob
import com.tencent.bkrepo.job.batch.context.NodeContext
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.config.properties.ArchivedNodeRestoreJobProperties
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.node.service.NodeArchiveRequest
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * 归档节点恢复任务
 * 对文件数据已经恢复好的节点进行恢复，具体步骤如下：
 * 1. 找到对应的已归档的节点
 * 2. 恢复节点
 * 3. 删除归档数据
 * */
@Component
@EnableConfigurationProperties(ArchivedNodeRestoreJobProperties::class)
class ArchivedNodeRestoreJob(
    private val properties: ArchivedNodeRestoreJobProperties,
    private val archiveClient: ArchiveClient,
    private val nodeClient: NodeClient,
) : MongoDbBatchJob<ArchivedNodeRestoreJob.ArchiveFile, NodeContext>(properties) {

    override fun createJobContext(): NodeContext {
        return NodeContext()
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(7)

    override fun collectionNames(): List<String> {
        return listOf("archive_file")
    }

    override fun buildQuery(): Query {
        return Query.query(Criteria.where("status").isEqualTo(ArchiveStatus.RESTORED))
    }

    override fun run(row: ArchiveFile, collectionName: String, context: NodeContext) {
        with(row) {
            // 查找所有的sha256 key对应的node
            listNode(sha256, storageCredentialsKey).forEach {
                val projectId = it.projectId
                val repoName = it.repoName
                val fullPath = it.fullPath
                val request = NodeArchiveRequest(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = fullPath,
                    operator = lastModifiedBy,
                )
                nodeClient.restoreNode(request)
                context.count.incrementAndGet()
                context.size.addAndGet(it.size)
                logger.info("Success to restore node $projectId/$repoName/$fullPath.")
            }
            val request = ArchiveFileRequest(sha256, storageCredentialsKey, lastModifiedBy)
            archiveClient.delete(request)
            logger.info("Success to restore file $sha256.")
        }
    }

    override fun mapToEntity(row: Map<String, Any?>): ArchiveFile {
        return ArchiveFile(
            id = row[ArchiveFile::id.name].toString(),
            storageCredentialsKey = row[ArchiveFile::storageCredentialsKey.name]?.toString(),
            lastModifiedBy = row[ArchiveFile::storageCredentialsKey.name].toString(),
            sha256 = row[ArchiveFile::sha256.name].toString(),
            size = row[ArchiveFile::size.name].toString().toLong(),
        )
    }

    override fun entityClass(): KClass<ArchiveFile> {
        return ArchiveFile::class
    }

    private fun listNode(sha256: String, storageCredentialsKey: String?): List<NodeCommonUtils.Node> {
        val query = Query.query(
            Criteria.where("sha256").isEqualTo(sha256)
                .and("archived").isEqualTo(true)
                .and("deleted").isEqualTo(null),
        )
        return NodeCommonUtils.findNodes(query, storageCredentialsKey)
    }

    data class ArchiveFile(
        var id: String? = null,
        val sha256: String,
        val size: Long,
        val storageCredentialsKey: String?,
        val lastModifiedBy: String,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(ArchivedNodeRestoreJob::class.java)
    }
}
