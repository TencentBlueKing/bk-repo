package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.archive.request.DeleteCompressRequest
import com.tencent.bkrepo.job.batch.base.MongoDbBatchJob
import com.tencent.bkrepo.job.batch.context.NodeContext
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.config.properties.NodeUncompressedJobProperties
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.node.service.NodeUnCompressedRequest
import java.time.Duration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * 节点解压任务
 *
 * 将已解压的节点去掉compressed标签
 * 1. 找到所有已解压的node
 * 2. 去掉compressed标签
 * 3. 删除压缩文件
 * */
@Component
@EnableConfigurationProperties(NodeUncompressedJobProperties::class)
class NodeUncompressedJob(
    properties: NodeUncompressedJobProperties,
    val nodeClient: NodeClient,
    val archiveClient: ArchiveClient,
) :
    MongoDbBatchJob<NodeUncompressedJob.CompressFile, NodeContext>(properties) {
    override fun createJobContext(): NodeContext {
        return NodeContext()
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(7)

    override fun collectionNames(): List<String> {
        return listOf("compress_file")
    }

    override fun buildQuery(): Query {
        return Query.query(Criteria.where("status").isEqualTo(CompressStatus.UNCOMPRESSED))
    }

    override fun run(row: CompressFile, collectionName: String, context: NodeContext) {
        with(row) {
            listNode(sha256, storageCredentialsKey).forEach {
                val compressedRequest = NodeUnCompressedRequest(
                    projectId = it.projectId,
                    repoName = it.repoName,
                    fullPath = it.fullPath,
                    operator = lastModifiedBy,
                )
                nodeClient.uncompressedNode(compressedRequest)
            }
            val request = DeleteCompressRequest(sha256, storageCredentialsKey, lastModifiedBy)
            archiveClient.deleteCompress(request)
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
                .and("compressed").isEqualTo(true)
                .and("deleted").isEqualTo(null),
        )
        return NodeCommonUtils.findNodes(query, storageCredentialsKey)
    }
}
