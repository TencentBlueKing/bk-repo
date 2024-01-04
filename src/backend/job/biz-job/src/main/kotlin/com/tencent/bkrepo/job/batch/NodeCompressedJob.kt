package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.archive.request.CompleteCompressRequest
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.job.batch.base.MongoDbBatchJob
import com.tencent.bkrepo.job.batch.context.NodeContext
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.config.properties.NodeCompressedJobProperties
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.node.service.NodeCompressedRequest
import java.time.Duration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * 节点压缩任务
 *
 * 将已压缩的node打赏compressed标签
 * 1. 找到所有已压缩的节点
 * 2. 设置compressed为true
 * 3. 删除原文件
 * 4. 完成压缩
 * */
@Component
@EnableConfigurationProperties(NodeCompressedJobProperties::class)
class NodeCompressedJob(
    properties: NodeCompressedJobProperties,
    val nodeClient: NodeClient,
    val archiveClient: ArchiveClient,
    val storageService: StorageService,
) :
    MongoDbBatchJob<NodeCompressedJob.CompressFile, NodeContext>(properties) {
    override fun createJobContext(): NodeContext {
        return NodeContext()
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(7)

    override fun collectionNames(): List<String> {
        return listOf("compress_file")
    }

    override fun buildQuery(): Query {
        return Query.query(Criteria.where("status").isEqualTo(CompressStatus.COMPRESSED))
    }

    override fun run(row: CompressFile, collectionName: String, context: NodeContext) {
        with(row) {
            listNode(sha256, storageCredentialsKey).forEach {
                val compressedRequest = NodeCompressedRequest(
                    projectId = it.projectId,
                    repoName = it.repoName,
                    fullPath = it.fullPath,
                    operator = lastModifiedBy,
                )
                nodeClient.compressedNode(compressedRequest)
            }
            val storageCredentials = storageCredentialsKey?.let {
                RepositoryCommonUtils.getStorageCredentials(storageCredentialsKey)
            }
            storageService.delete(sha256, storageCredentials)
            val request = CompleteCompressRequest(sha256, storageCredentialsKey, lastModifiedBy)
            archiveClient.completeCompress(request)
        }
    }

    override fun mapToEntity(row: Map<String, Any?>): CompressFile {
        return CompressFile(
            id = row[CompressFile::id.name].toString(),
            storageCredentialsKey = row[CompressFile::storageCredentialsKey.name]?.toString(),
            sha256 = row[CompressFile::sha256.name].toString(),
            uncompressedSize = row[CompressFile::uncompressedSize.name].toString().toLong(),
            lastModifiedBy = row[CompressFile::lastModifiedBy.name].toString(),
        )
    }

    override fun entityClass(): KClass<CompressFile> {
        return CompressFile::class
    }

    data class CompressFile(
        var id: String? = null,
        val sha256: String,
        val uncompressedSize: Long,
        val storageCredentialsKey: String?,
        val lastModifiedBy: String,
    )

    private fun listNode(sha256: String, storageCredentialsKey: String?): List<NodeCommonUtils.Node> {
        val query = Query.query(
            Criteria.where("sha256").isEqualTo(sha256)
                .and("compressed").ne(true)
                .and("deleted").isEqualTo(null),
        )
        return NodeCommonUtils.findNodes(query, storageCredentialsKey)
    }
}
