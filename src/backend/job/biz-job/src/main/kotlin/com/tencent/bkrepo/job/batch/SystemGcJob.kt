package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.archive.request.CompressFileRequest
import com.tencent.bkrepo.common.api.collection.groupBySimilar
import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.common.mongo.dao.util.sharding.HashShardingUtils
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
    private var lastCutoffTime = LocalDateTime.MIN
    private var curCutoffTime = LocalDateTime.MIN
    override fun doStart0(jobContext: JobContext) {
        curCutoffTime = LocalDateTime.now().minus(Duration.ofDays(properties.idleDays.toLong()))
        properties.repos.forEach {
            val splits = it.split("/")
            var count: Long
            val projectId = splits[0]
            val repoName = splits[1]
            val nanos = measureNanoTime { count = repoGc(projectId, repoName) }
            logger.info("Finish gc repository [$projectId/$repoName]($count nodes), took ${HumanReadable.time(nanos)}.")
        }
        lastCutoffTime = curCutoffTime
    }

    private fun repoGc(projectId: String, repoName: String): Long {
        lastId = MIN_OBJECT_ID
        val seq = HashShardingUtils.shardingSequenceFor(projectId, SHARDING_COUNT)
        val collectionName = "node_$seq"
        var nodes = mongoTemplate.find(buildQuery(projectId, repoName), Node::class.java, collectionName)
        var count: Long = 0
        while (nodes.size > properties.nodeLimit) {
            logger.info("Find ${nodes.size} nodes.")
            lastId = nodes.last().id
            count += nodes.size
            // 文件按类型与长度分类，降低聚合难度
            nodes.groupBy { it.name.substringAfterLast(".") + it.name.length }
                .flatMap { this.groupAndMeasure(it.value) }
                .filter { it.size > properties.retain }
                .forEach { gc(it) }
            nodes = mongoTemplate.find(buildQuery(projectId, repoName), Node::class.java, collectionName)
        }
        return count
    }

    private fun groupAndMeasure(list: List<Node>): List<List<Node>> {
        if (list.size == 1) {
            return listOf(list)
        }
        logger.info("Counting nodes: ${list.size}.")
        var groups: List<List<Node>>
        val nanos = measureNanoTime { groups = list.groupBySimilar({ node -> node.name }, this::isSimilar) }
        logger.info("Complete grouping,took ${HumanReadable.time(nanos)}.")
        return groups
    }

    fun isSimilar(node1: Node, node2: Node): Boolean {
        // 大小差异过大
        if (abs(node1.size - node2.size) * 2 / (node1.size + node2.size) > 0.5) {
            return false
        }
        val name1 = node1.name
        val name2 = node2.name
        val editDistance = HAMMING_DISTANCE_INSTANCE.apply(name1, name2)
        val ratio = editDistance.toDouble() * 2 / (name1.length + name2.length)
        if (logger.isDebugEnabled) {
            logger.debug("ham($name1,$name2)=$editDistance ($ratio)")
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
        ).limit(properties.maxBatchSize).with(Sort.by(ID).ascending()) // 长时间未访问
    }

    /**
     * 数据gc
     * */
    private fun gc(nodes: List<Node>) {
        val sortedNodes = nodes.distinctBy { it.sha256 }
            .apply {
                if ((size - properties.retain) < 1) {
                    return
                }
            }
            .sortedBy { it.createdDate }
        // 没有新的节点，表示节点已经gc过一轮了
        if (lastEndTime != null && sortedNodes.last().createdDate < lastCutoffTime) {
            logger.info("There are no new nodes, gc is skipped.")
            return
        }
        val gcNodes = sortedNodes.subList(0, sortedNodes.size - properties.retain)
        if (logger.isDebugEnabled) {
            logger.debug("Group node: [${gcNodes.joinToString(",") { it.name }}]")
        }
        // 保留最新的
        val newest = sortedNodes.last()
        val repo = RepositoryCommonUtils.getRepositoryDetail(newest.projectId, newest.repoName)
        val credentials = repo.storageCredentials
        gcNodes.forEach {
            val compressedRequest = CompressFileRequest(it.sha256, it.size, newest.sha256, credentials?.key)
            archiveClient.compress(compressedRequest)
            logger.info("Compress node ${it.name} by node ${newest.name}.")
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

    companion object {
        private val logger = LoggerFactory.getLogger(SystemGcJob::class.java)
        private val HAMMING_DISTANCE_INSTANCE = HammingDistance()
    }
}
