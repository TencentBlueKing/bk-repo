package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.MongoDbBatchJob
import com.tencent.bkrepo.job.batch.context.NodeCopyJobContext
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.config.properties.NodeCopyJobProperties
import com.tencent.bkrepo.job.exception.JobExecuteException
import com.tencent.bkrepo.repository.api.FileReferenceClient
import com.tencent.bkrepo.repository.constant.DEFAULT_STORAGE_CREDENTIALS_KEY
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
@EnableConfigurationProperties(NodeCopyJobProperties::class)
class NodeCopyJob(
    private val storageService: StorageService,
    private val fileReferenceClient: FileReferenceClient,
    properties: NodeCopyJobProperties
) : MongoDbBatchJob<NodeCopyJob.NodeCopyData, NodeCopyJobContext>(properties) {

    override fun start(): Boolean {
        return super.start()
    }

    override fun collectionNames(): List<String> {
        return (0 until SHARDING_COUNT)
            .map { "${COLLECTION_NAME_PREFIX}$it" }
            .toList()
    }

    override fun buildQuery(): Query {
        return Query(where(NodeCopyData::copyFromCredentialsKey).ne(null))
    }

    override fun mapToEntity(row: Map<String, Any?>): NodeCopyData {
        return NodeCopyData(row)
    }

    override fun entityClass(): KClass<NodeCopyData> = NodeCopyData::class

    override fun createJobContext(): NodeCopyJobContext = NodeCopyJobContext()

    override fun run(row: NodeCopyData, collectionName: String, context: NodeCopyJobContext) {
        var digest: String? = null
        var srcCredentials: StorageCredentials? = null
        var dstCredentials: StorageCredentials? = null
        try {
            digest = row.sha256
            srcCredentials = RepositoryCommonUtils.getStorageCredentials(row.copyFromCredentialsKey)
            val repositoryDetail = RepositoryCommonUtils.getRepositoryDetail(row.projectId, row.repoName)
            dstCredentials = repositoryDetail.storageCredentials
            fileReferenceCheck(dstCredentials, row, digest)
            val targetCopy = TargetCopy(targetCredentialsKey = dstCredentials?.key, digest = digest)
            if (context.alreadyCopySet.contains(targetCopy)) {
                afterCopySuccess(row, collectionName)
                return
            }
            if (storageService.exist(digest, srcCredentials)) {
                safeCopy(digest, srcCredentials, dstCredentials)
                afterCopySuccess(row, collectionName)
                context.alreadyCopySet.add(targetCopy)
            } else {
                context.fileMissing.incrementAndGet()
                logger.warn("File[$digest] is missing on [$srcCredentials], skip copy.")
            }
        } catch (e: Exception) {
            throw JobExecuteException("Failed to copy file[$digest] from [$srcCredentials] to [$dstCredentials].", e)
        }
    }

    /**
     * 防止并发拷贝相同文件报错
     * */
    private fun safeCopy(
        digest: String,
        srcCredentials: StorageCredentials?,
        dstCredentials: StorageCredentials?
    ) {
        val key = "$digest-${srcCredentials?.key}-${dstCredentials?.key}"
        synchronized(key.intern()) {
            storageService.copy(digest, srcCredentials, dstCredentials)
        }
    }

    class NodeCopyData(map: Map<String, Any?>) {
        val id: String? by map
        val sha256: String by map
        val projectId: String by map
        val repoName: String by map
        val copyFromCredentialsKey: String by map
        val copyIntoCredentialsKey: String by map
    }

    /**
     * 文件引用核对
     * 拷贝时的存储实例与当前仓库的存储实例不同，说明仓库已经迁移到其他存储实例，
     * 则原先增加引用的存储实例，文件引用要减1还原
     * 当前存储实例引用加1
     * */
    private fun fileReferenceCheck(
        dstCredentials: StorageCredentials?,
        node: NodeCopyData,
        digest: String
    ) {
        var dstCredentialsKey: String? = dstCredentials?.key ?: DEFAULT_STORAGE_CREDENTIALS_KEY
        if (dstCredentialsKey != node.copyIntoCredentialsKey) {
            fileReferenceClient.decrement(digest, node.copyIntoCredentialsKey)
            if (dstCredentialsKey == DEFAULT_STORAGE_CREDENTIALS_KEY) {
                // 还原为默认存储key为null
                dstCredentialsKey = null
            }
            fileReferenceClient.increment(digest, dstCredentialsKey)
        }
    }

    /**
     * 拷贝成功后，删除存储映射
     * */
    private fun afterCopySuccess(node: NodeCopyData, collectionName: String) {
        with(node) {
            val update = Update().set(node::copyFromCredentialsKey.name, null)
                .set(node::copyIntoCredentialsKey.name, null)
            mongoTemplate.updateFirst(Query(Criteria(ID).isEqualTo(id)), update, collectionName)
        }
    }

    data class TargetCopy(
        val targetCredentialsKey: String?,
        val digest: String
    )

    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val COLLECTION_NAME_PREFIX = "node_"
    }
}
