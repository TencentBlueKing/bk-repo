package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.archive.request.CreateArchiveFileRequest
import com.tencent.bkrepo.common.mongo.dao.util.sharding.HashShardingUtils
import com.tencent.bkrepo.fs.server.constant.FAKE_SHA256
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.MongoDbBatchJob
import com.tencent.bkrepo.job.batch.context.NodeContext
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.IdleNodeArchiveJobProperties
import com.tencent.bkrepo.repository.api.FileReferenceClient
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * 空闲节点归档任务
 * 将长期未被访问的节点进行归档，具体步骤如下：
 * 1. 查找长期未访问的未归档节点
 * 2. 查看是否已经存在归档任务，如果有则跳过此次归档
 * 3. 查看文件引用数，如果引用数为1，则直接进行归档任务，否则检查引用是否在存活时间内被使用
 * 4. 如果未被使用，则进行归档。
 * */
@Component
@EnableConfigurationProperties(IdleNodeArchiveJobProperties::class)
class IdleNodeArchiveJob(
    private val properties: IdleNodeArchiveJobProperties,
    private val archiveClient: ArchiveClient,
    private val fileReferenceClient: FileReferenceClient,
) : MongoDbBatchJob<IdleNodeArchiveJob.Node, NodeContext>(properties) {
    private var lastCutoffTime: LocalDateTime? = null
    private var tempCutoffTime: LocalDateTime? = null
    private var refreshCount = INITIAL_REFRESH_COUNT
    private val nodeUseInfoCache = ConcurrentHashMap<NodeDataId, Boolean>()

    override fun collectionNames(): List<String> {
        val collectionNames = mutableListOf<String>()
        if (properties.projects.isNotEmpty()) {
            properties.projects.forEach {
                val index = HashShardingUtils.shardingSequenceFor(it, SHARDING_COUNT)
                collectionNames.add("${COLLECTION_NAME_PREFIX}$index")
            }
        } else {
            (0 until SHARDING_COUNT).forEach { collectionNames.add("${COLLECTION_NAME_PREFIX}$it") }
        }
        return collectionNames
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(7)

    override fun doStart0(jobContext: NodeContext) {
        super.doStart0(jobContext)
        // 由于新的文件可能会被删除，所以旧文件数据的引用会被改变，所以需要重新扫描旧文件引用。
        if (refreshCount-- < 0) {
            lastCutoffTime = null
            refreshCount = INITIAL_REFRESH_COUNT
        } else {
            lastCutoffTime = tempCutoffTime
        }
        nodeUseInfoCache.clear()
    }

    override fun buildQuery(): Query {
        val now = LocalDateTime.now()
        val cutoffTime = now.minus(Duration.ofDays(properties.days.toLong()))
        tempCutoffTime = cutoffTime
        return Query.query(
            Criteria.where("folder").isEqualTo(false)
                .and("deleted").isEqualTo(null)
                .and("sha256").ne(FAKE_SHA256)
                .and("archived").ne(true)
                .and("compressed").ne(true)
                .and("size").gt(properties.fileSizeThreshold.toBytes())
                .apply {
                    if (properties.projects.isNotEmpty()) {
                        and("projectId").inValues(properties.projects)
                    }
                    if (lastCutoffTime == null) {
                        // 首次查询
                        orOperator(
                            Criteria.where("lastAccessDate").isEqualTo(null),
                            Criteria.where("lastAccessDate").lt(cutoffTime),
                        )
                    } else {
                        and("lastAccessDate").gte(lastCutoffTime!!).lt(cutoffTime)
                    }
                },
        )
    }

    override fun run(row: Node, collectionName: String, context: NodeContext) {
        val sha256 = row.sha256
        val projectId = row.projectId
        val repoName = row.repoName
        val repo = RepositoryCommonUtils.getRepositoryDetail(projectId, repoName)
        val credentialsKey = repo.storageCredentials?.key
        if (properties.ignoreStorageCredentialsKeys.contains(credentialsKey) ||
            properties.ignoreRepoType.contains(repo.type.name)
        ) {
            logger.info("Skip $row#${repo.type.name} on $credentialsKey.")
            return
        }
        val nodeDataId = NodeDataId(sha256, credentialsKey)
        if (nodeUseInfoCache[nodeDataId] == true) {
            logger.info("Find it[$row] in use by cache,skip archive.")
            return
        }
        val af = archiveClient.get(sha256, credentialsKey).data
        if (af == null) {
            val count = fileReferenceClient.count(sha256, credentialsKey).data
            // 归档任务不存在
            if (count == 1L) {
                // 快速归档
                createArchiveFile(credentialsKey, context, row)
            } else {
                synchronized(sha256.intern()) {
                    slowArchive(row, credentialsKey, context)
                }
            }
        } else {
            logger.info("Archive[$row] job already exist[${af.status}].")
        }
    }

    private fun slowArchive(
        row: Node,
        credentialsKey: String?,
        context: NodeContext,
    ) {
        with(row) {
            val af = archiveClient.get(sha256, credentialsKey).data
            if (af == null) {
                val nodeDataId = NodeDataId(sha256, credentialsKey)
                val inUse = nodeUseInfoCache[nodeDataId] ?: checkUse(sha256, credentialsKey)
                if (inUse) {
                    // 只需要缓存被使用的情况，这可以避免sha256被重复搜索。当sha256未被使用时，它会创建一条归档记录，所以无需缓存。
                    nodeUseInfoCache[nodeDataId] = true
                } else {
                    createArchiveFile(credentialsKey, context, row)
                }
            } else {
                logger.info("Archive[$row] job already exist[${af.status}].")
            }
        }
    }

    private fun createArchiveFile(
        credentialsKey: String?,
        context: NodeContext,
        row: Node,
    ) {
        with(row) {
            val createArchiveFileRequest = CreateArchiveFileRequest(
                sha256 = sha256,
                size = size,
                storageCredentialsKey = credentialsKey,
                operator = SYSTEM_USER,
            )
            archiveClient.archive(createArchiveFileRequest)
            context.count.incrementAndGet()
            context.size.addAndGet(row.size)
            logger.info("Success to archive node [$row],lat:$lastAccessDate.")
        }
    }

    override fun mapToEntity(row: Map<String, Any?>): Node {
        return Node(
            id = row[Node::id.name].toString(),
            projectId = row[Node::projectId.name].toString(),
            repoName = row[Node::repoName.name].toString(),
            fullPath = row[Node::fullPath.name].toString(),
            sha256 = row[Node::sha256.name].toString(),
            size = row[Node::size.name].toString().toLong(),
            lastAccessDate = TimeUtils.parseMongoDateTimeStr(row[Node::lastAccessDate.name].toString()),
        )
    }

    override fun entityClass(): KClass<Node> {
        return Node::class
    }

    override fun createJobContext(): NodeContext {
        return NodeContext()
    }

    data class Node(
        val id: String,
        val projectId: String,
        val repoName: String,
        val fullPath: String,
        val sha256: String,
        val size: Long,
        var lastAccessDate: LocalDateTime? = null,
    ) {
        override fun toString(): String {
            return "$projectId/$repoName$fullPath($sha256)"
        }
    }

    private fun checkUse(sha256: String, credentialsKey: String?): Boolean {
        val cutoffTime = LocalDateTime.now().minus(Duration.ofDays(properties.days.toLong()))
        val query = Query.query(
            Criteria.where("sha256").isEqualTo(sha256)
                .and("deleted").isEqualTo(null)
                .and("lastAccessDate").gt(cutoffTime),
        )
        collectionNames().forEach {
            val nodes = mongoTemplate.find(query, Node::class.java, it)
            nodes.forEach { n ->
                val repo = RepositoryCommonUtils.getRepositoryDetail(n.projectId, n.repoName)
                if (repo.storageCredentials?.key == credentialsKey) {
                    logger.info("$sha256/$credentialsKey in use[$n].")
                    return true
                }
            }
        }
        return false
    }

    data class NodeDataId(
        val sha256: String,
        val credentialsKey: String?,
    )

    companion object {
        private const val COLLECTION_NAME_PREFIX = "node_"
        private const val INITIAL_REFRESH_COUNT = 3
        private val logger = LoggerFactory.getLogger(IdleNodeArchiveJob::class.java)
    }
}
