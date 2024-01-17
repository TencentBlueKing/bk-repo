package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.archive.request.CompressFileRequest
import com.tencent.bkrepo.common.api.collection.groupBySimilar
import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.common.mongo.dao.util.sharding.HashShardingUtils
import com.tencent.bkrepo.common.service.exception.RemoteErrorCodeException
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.fs.server.constant.FAKE_SHA256
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.DefaultContextJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.config.properties.SystemGcJobProperties
import org.apache.commons.text.similarity.HammingDistance
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import kotlin.reflect.full.declaredMemberProperties
import kotlin.system.measureNanoTime

/**
 * 存储GC任务
 * 找到相似的节点，进行增量压缩，以减少不必要的存储。
 * */
@Component
@EnableConfigurationProperties(SystemGcJobProperties::class)
class SystemGcJob(
    val properties: SystemGcJobProperties,
    private val mongoTemplate: MongoTemplate,
    private val archiveClient: ArchiveClient,
) : DefaultContextJob(properties) {

    private var lastId = MIN_OBJECT_ID
    private var lastCutoffTimeMap = mutableMapOf<String, LocalDateTime>()
    private var curCutoffTime = LocalDateTime.MIN
    private val sampleNodesMap = mutableMapOf<String, MutableList<Node>>()
    override fun doStart0(jobContext: JobContext) {
        curCutoffTime = LocalDateTime.now().minus(Duration.ofDays(properties.idleDays.toLong()))
        var total: Long = 0
        var totalSize: Long = 0
        var gcCount: Long = 0
        var gcSize: Long = 0
        var repoCount = 0
        var startAt = System.nanoTime()
        properties.repos.forEach {
            val splits = it.split("/")
            var metric: GcMetric
            val projectId = splits[0]
            val repoName = splits[1]
            logger.info("Start gc($projectId/$repoName).")
            val nanos = measureNanoTime { metric = repoGc(projectId, repoName) }
            logger.info("Complete gc($projectId/$repoName): $metric, took ${HumanReadable.time(nanos)}.")
            total += metric.total
            totalSize += metric.totalSize
            gcCount += metric.gcCount
            gcSize += metric.gcSize
            repoCount++
            lastCutoffTimeMap[it] = curCutoffTime
        }
        val took = HumanReadable.time(System.nanoTime() - startAt)
        val metric = GcMetric(gcCount, total, gcSize, totalSize)
        logger.info("GC Summary: $repoCount repos,$metric,took $took.")
    }

    private fun repoGc(projectId: String, repoName: String): GcMetric {
        lastId = MIN_OBJECT_ID
        val seq = HashShardingUtils.shardingSequenceFor(projectId, SHARDING_COUNT)
        val collectionName = "node_$seq"
        var nodes = mongoTemplate.find(buildQuery(projectId, repoName), Node::class.java, collectionName)
        var total: Long = 0
        var totalSize: Long = 0
        var gcCount: Long = 0
        var gcSize: Long = 0
        val repo = RepositoryCommonUtils.getRepositoryDetail(projectId, repoName)
        val credentials = repo.storageCredentials
        while (nodes.size > properties.nodeLimit) {
            logger.info("${nodes.size} Nodes.")
            lastId = nodes.last().id
            total += nodes.size
            totalSize += nodes.map { it.size }.reduce(Long::plus)
            // 文件按类型与长度分类，降低聚合难度
            nodes.groupBy { it.name.substringAfterLast(".") + it.name.length }
                .flatMap { this.group(it.value) }
                .filter { it.size > properties.retain }
                .mapNotNull { filterGCable(it, credentials) }
                .forEach { result ->
                    val gcNodeList = result.first
                    gcCount += gcNodeList.size
                    gcSize += gcNodeList.map { it.size }.reduce(Long::plus)
                    logger.info("Start gc(${gcNodeList.size} nodes) ${gcNodeList.joinToString(",") { it.name }}.")
                    val newest = result.second
                    gcNodeList.forEach { node ->
                        compressNode(node, newest, credentials)
                    }
                }
            nodes = mongoTemplate.find(buildQuery(projectId, repoName), Node::class.java, collectionName)
        }
        return GcMetric(gcCount, total, gcSize, totalSize)
    }

    private fun group(list: List<Node>): List<List<Node>> {
        if (list.size == 1) {
            return listOf(list)
        }
        return list.groupBySimilar({ node -> node.name }, this::isSimilar)
    }

    fun isSimilar(node1: Node, node2: Node): Boolean {
        val name1 = node1.name
        val name2 = node2.name
        // 大小差异过大
        if (name1.length != name2.length ||
            abs(node1.size - node2.size).toDouble() / maxOf(node1.size, node2.size) > SIZE_RATIO
        ) {
            return false
        }
        val editDistance = HAMMING_DISTANCE_INSTANCE.apply(name1, name2)
        val ratio = editDistance.toDouble() * 2 / (name1.length + name2.length)
        if (logger.isTraceEnabled) {
            logger.trace("ham($name1,$name2)=$editDistance ($ratio)")
        }
        return ratio < properties.edThreshold
    }

    private fun buildQuery(projectId: String, repoName: String): Query {
        return Query.query(
            Criteria.where(ID).gt(ObjectId(lastId))
                .and("folder").isEqualTo(false)
                .and("sha256").ne(FAKE_SHA256)
                .and("deleted").isEqualTo(null)
                .and("projectId").isEqualTo(projectId)
                .and("repoName").isEqualTo(repoName)
                .and("compressed").ne(true) // 未被压缩
                .and("archived").ne(true) // 未被归档
                .and("size").gt(properties.fileSizeThreshold.toBytes())
                .orOperator(
                    Criteria.where("lastAccessDate").isEqualTo(null),
                    Criteria.where("lastAccessDate").lt(curCutoffTime),
                ),
        ).limit(properties.maxBatchSize)
            .with(Sort.by(ID).ascending())
            .apply {
                val fields = fields()
                Node::class.declaredMemberProperties.forEach {
                    fields.include(it.name)
                }
            }
    }

    /**
     * 数据gc
     * */
    private fun filterGCable(nodes: List<Node>, credentials: StorageCredentials?): Pair<List<Node>, Node>? {
        val sortedNodes = nodes.distinctBy { it.sha256 }
            .apply {
                if ((size - properties.retain) < 1) {
                    return null
                }
            }
            .sortedBy { it.createdDate }
        logger.info("Find a group(${sortedNodes.size} nodes): [${sortedNodes.joinToString(",") { it.name }}]")
        // 没有新的节点，表示节点已经gc过一轮了
        val repoKey = nodes.first().let { "${it.projectId}/${it.repoName}" }
        val sampleNodes = sampleNodesMap.getOrPut(repoKey) { mutableListOf() }
        // 从采样节点中找到相同的组
        val fileType = nodes.first().name.substringAfterLast(".")
        val sampleNode = sampleNodes.firstOrNull {
            it.name.substringAfterLast(".") == fileType &&
                isSimilar(it, nodes.first()) && isSimilar(it, nodes.last())
        }
        // 保留最新的
        val newest = sortedNodes.last()
        // 有采样节点存在，表示上次gc并没有完成
        val lastCutoffTime = lastCutoffTimeMap[repoKey]
        var gcable = false
        val gcNodes = sortedNodes.subList(0, sortedNodes.size - properties.retain).toMutableList()
        if (lastCutoffTime != null && newest.createdDate < lastCutoffTime && sampleNode == null) {
            // 陈旧
            logger.info("Stale skipped.")
        } else {
            if (gcNodes.size < MIN_SAMPLING_GROUP_SIZE) {
                // 直接压缩
                gcable = true
            } else if (sampleNode != null) {
                gcable = determineBySample(sampleNode, credentials, newest, sampleNodes)
            } else {
                val node = selectOne(gcNodes, newest, credentials)
                if (node != null) {
                    sampleNodes.add(node)
                    logger.info("Sampling: $node")
                } else {
                    logger.info("Sample failed.")
                }
            }
        }
        return if (gcable) {
            if (sampleNode != null) {
                gcNodes.remove(sampleNode)
            }
            Pair(gcNodes, newest)
        } else {
            null
        }
    }

    /**
     * 创建采样节点
     * */
    private fun selectOne(
        gcNodes: List<Node>,
        newest: Node,
        credentials: StorageCredentials?,
    ): Node? {
        /*
         * 这里每次我们都会新建采样，是因为我们的目标还是希望可以回收文件数据，
         * 假设最坏结果就是，这个文件组都是无法gc的，那么我们浪费的只是在文件
         * 组有变化时的采样节点，对于这样的成本我们是愿意承担的。
         * */
        for (node in gcNodes) {
            val compressFile = archiveClient.getCompressInfo(node.sha256, credentials?.key).data
            // 创建新的采样
            if (compressFile == null) {
                val resp = compressNode(node, newest, credentials)
                if (resp == 1) {
                    logger.info("Create a new sample [$node].")
                    return node
                }
            }
        }
        return null
    }

    /**
     * 通过已有的采样节点决定是否gc
     * */
    private fun determineBySample(
        sampleNode: Node,
        credentials: StorageCredentials?,
        newest: Node,
        sampleNodes: MutableList<Node>,
    ): Boolean {
        val compressFile = archiveClient.getCompressInfo(sampleNode.sha256, credentials?.key).data
        val status = compressFile?.status
        return when (status) {
            // 压缩信息丢失
            null -> {
                logger.info("Lost sample information.")
                compressNode(sampleNode, newest, credentials)
                false
            }
            // 压缩中
            CompressStatus.CREATED,
            CompressStatus.COMPRESSING,
            -> {
                logger.info("Sample [$sampleNode] in process")
                false
            }
            // 压缩失败，放弃此次gc nodes,移除采样节点后，如果有gc node有新的group形成，则会进行新一轮的gc。
            CompressStatus.COMPRESS_FAILED -> {
                sampleNodes.remove(sampleNode)
                logger.info("Sample [$sampleNode] fails.")
                false
            }
            // 压缩成功，压缩gc nodes
            else -> {
                logger.info("Sample [$sampleNode] success.")
                sampleNodes.remove(sampleNode)
                true
            }
        }
    }

    /**
     * 压缩节点
     * */
    private fun compressNode(node: Node, baseNode: Node, storageCredentials: StorageCredentials?): Int {
        with(node) {
            logger.info("Compress node $name by node ${baseNode.name}.")
            val compressedRequest = CompressFileRequest(
                sha256 = sha256,
                size = size,
                baseSha256 = baseNode.sha256,
                baseSize = baseNode.size,
                storageCredentialsKey = storageCredentials?.key,
            )
            return try {
                archiveClient.compress(compressedRequest)
                1
            } catch (ignore: RemoteErrorCodeException) {
                0
            }
        }
    }

    data class Node(
        val id: String,
        val projectId: String,
        val repoName: String,
        val fullPath: String,
        val sha256: String,
        val size: Long,
        val name: String,
        val createdDate: LocalDateTime,
    ) {
        override fun toString(): String {
            return "$projectId/$repoName$fullPath($sha256)"
        }
    }

    data class GcMetric(
        val gcCount: Long = 0,
        val total: Long = 0,
        val gcSize: Long = 0,
        val totalSize: Long = 0,

    ) {
        override fun toString(): String {
            return "$gcCount/$total nodes,${HumanReadable.size(gcSize)}/${HumanReadable.size(totalSize)}."
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SystemGcJob::class.java)
        private const val SIZE_RATIO = 0.5
        private val HAMMING_DISTANCE_INSTANCE = HammingDistance()
        private const val MIN_SAMPLING_GROUP_SIZE = 5
    }
}
