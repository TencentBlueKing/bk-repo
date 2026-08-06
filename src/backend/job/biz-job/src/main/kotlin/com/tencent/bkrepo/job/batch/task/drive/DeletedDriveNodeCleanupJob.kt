package com.tencent.bkrepo.job.batch.task.drive

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.job.COUNT
import com.tencent.bkrepo.job.CREDENTIALS
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.NAME
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.SHA256
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.batch.utils.ValueConvertUtils
import com.tencent.bkrepo.job.config.properties.DriveDeletedNodeCleanupJobProperties
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * 清理已软删除的 drive node，并在安全前提下物理删除其 block
 */
@Component
class DeletedDriveNodeCleanupJob(
    private val properties: DriveDeletedNodeCleanupJobProperties,
    @Qualifier("driveMongoTemplate")
    private val driveMongoTemplate: MongoTemplate,
) : DefaultContextMongoDbJob<DeletedDriveNodeCleanupJob.DriveNode>(properties) {

    override fun getLockAtMostFor(): Duration = Duration.ofDays(28)

    override fun collectionNames(): List<String> {
        return (0 until SHARDING_COUNT).map { "$COLLECTION_DRIVE_NODE_PREFIX$it" }.toList()
    }

    override fun buildQuery(): Query {
        val expireDate = LocalDateTime.now().minusDays(properties.deletedNodeReserveDays)
        return Query(Criteria.where(DELETED_DATE).lt(expireDate))
    }

    override fun run(row: DriveNode, collectionName: String, context: JobContext) {
        if (isReferencedBySnapshot(row)) {
            logger.info(
                "Skip deleting drive node[{}], it is still visible to snapshots in [{}, {}).",
                "${row.projectId}/${row.repoName}/${row.id}",
                row.snapSeq,
                row.deleteSnapSeq
            )
            return
        }
        val siblings = findSiblingNodes(row, collectionName)
        if (allSiblingsCanBeCleanedUp(siblings)) {
            if (!cleanupDriveBlocks(row)) {
                logger.warn(
                    "Skip deleting drive node[{}], failed to cleanup drive blocks or references.",
                    "${row.projectId}/${row.repoName}/${row.id}",
                )
                return
            }
        } else {
            logger.info(
                "Skip deleting drive blocks for node[{}], same realIno[{}] still referenced by other drive nodes.",
                "${row.projectId}/${row.repoName}/${row.id}",
                row.realIno
            )
        }
        deleteDriveNode(row, collectionName)
    }

    override fun mapToEntity(row: Map<String, Any?>): DriveNode {
        return DriveNode(
            id = row[ID]!!.toString(),
            projectId = row[PROJECT]!!.toString(),
            repoName = row[REPO]!!.toString(),
            ino = ValueConvertUtils.toLong(row[DriveNode::ino.name], DriveNode::ino.name),
            targetIno = ValueConvertUtils.toLongOrNull(row[DriveNode::targetIno.name]),
            realIno = ValueConvertUtils.toLong(row[DriveNode::realIno.name], DriveNode::realIno.name),
            snapSeq = ValueConvertUtils.toLong(row[DriveNode::snapSeq.name], DriveNode::snapSeq.name),
            deleteSnapSeq = ValueConvertUtils.toLong(row[DriveNode::deleteSnapSeq.name], DriveNode::deleteSnapSeq.name),
            deleted = TimeUtils.parseMongoDateTimeStr(row[DELETED_DATE].toString()),
        )
    }

    override fun entityClass(): KClass<DriveNode> {
        return DriveNode::class
    }

    override fun batchQueryMongoTemplate(): MongoTemplate = driveMongoTemplate

    private val snapshotSnapSeqCache: LoadingCache<RepositoryId, List<Long>> = CacheBuilder.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build(CacheLoader.from { key -> loadSnapshotSnapSeqList(key!!) })

    /**
     * 判断节点是否仍被任一未删除快照引用。
     *
     * Drive 节点在快照 s 下可见的条件为：
     * `node.snapSeq <= s < node.deleteSnapSeq`
     *
     * 实现方式：
     * 1. 按仓库从缓存中获取已排序的活跃快照 snapSeq 列表
     * 2. 使用二分查找第一个 >= node.snapSeq 的候选快照
     * 3. 若候选快照存在且 < node.deleteSnapSeq，则说明该节点仍被快照引用
     */
    private fun isReferencedBySnapshot(node: DriveNode): Boolean {
        val snapshotSnapSeqList = snapshotSnapSeqCache.get(RepositoryId(node.projectId, node.repoName))
        if (snapshotSnapSeqList.isEmpty()) {
            return false
        }
        val firstCandidate = snapshotSnapSeqList.binarySearch(node.snapSeq).let { index ->
            if (index >= 0) index else -index - 1
        }
        if (firstCandidate >= snapshotSnapSeqList.size) {
            return false
        }
        return snapshotSnapSeqList[firstCandidate] < node.deleteSnapSeq
    }

    private fun loadSnapshotSnapSeqList(repositoryId: RepositoryId): List<Long> {
        val criteria = Criteria.where(PROJECT).isEqualTo(repositoryId.projectId)
            .and(REPO).isEqualTo(repositoryId.repoName)
        val query = Query.query(criteria)
        query.fields().include(SNAPSHOT_SEQ)
        val snapshots = driveMongoTemplate.find(query, Document::class.java, COLLECTION_DRIVE_SNAPSHOT)
        return snapshots.map { ValueConvertUtils.toLong(it[SNAPSHOT_SEQ], SNAPSHOT_SEQ) }.sorted()
    }

    /**
     * 查询同 projectId/repoName/realIno 且排除自身的所有 drive node
     */
    private fun findSiblingNodes(node: DriveNode, collectionName: String): List<DriveNode> {
        val criteria = Criteria.where(PROJECT).isEqualTo(node.projectId)
            .and(REPO).isEqualTo(node.repoName)
            .and(DriveNode::realIno.name).isEqualTo(node.realIno)
            .and(ID).ne(node.id)
        return driveMongoTemplate.find(Query.query(criteria), Document::class.java, collectionName)
            .map { mapToEntity(it) }
    }

    /**
     * 判断所有同 realIno 兄弟节点是否都已删除、已过期且不再被快照引用
     */
    private fun allSiblingsCanBeCleanedUp(siblings: List<DriveNode>): Boolean {
        val expireDate = LocalDateTime.now().minusDays(properties.deletedNodeReserveDays)
        return siblings.all { sibling ->
            sibling.deleted != null && sibling.deleted < expireDate && !isReferencedBySnapshot(sibling)
        }
    }

    private fun cleanupDriveBlocks(node: DriveNode): Boolean {
        val blockCollectionName = resolveDriveBlockCollectionName(node.projectId, node.repoName, node.realIno)
        val criteria = Criteria.where(PROJECT).isEqualTo(node.projectId)
            .and(REPO).isEqualTo(node.repoName)
            .and(DriveNode::ino.name).isEqualTo(node.realIno)
        val query = Query.query(criteria)
        query.fields().include(ID).include(SHA256)
        val blocks = driveMongoTemplate.find(query, Document::class.java, blockCollectionName)
        if (blocks.isEmpty()) {
            return true
        }
        val credentialsKey = getCredentialsKey(node.projectId, node.repoName)
        blocks.forEach {
            val sha256 = it[SHA256]!!.toString()
            if (!decrementFileReferences(sha256, credentialsKey)) {
                return false
            }
        }
        val deleteQuery = Query.query(Criteria.where(ID).`in`(blocks.map { it[ID]!! }))
        val result = driveMongoTemplate.remove(deleteQuery, blockCollectionName)
        logger.info(
            "delete drive blocks in collection[{}], repo[{}/{}], realIno[{}], deletedCount[{}].",
            blockCollectionName,
            node.projectId,
            node.repoName,
            node.realIno,
            result.deletedCount
        )
        return result.deletedCount == blocks.size.toLong()
    }

    private fun deleteDriveNode(node: DriveNode, collectionName: String) {
        val query = Query.query(Criteria.where(ID).isEqualTo(node.id))
        driveMongoTemplate.remove(query, collectionName)
    }

    private fun decrementFileReferences(sha256: String, credentialsKey: String?): Boolean {
        val collectionName =
            COLLECTION_DRIVE_FILE_REFERENCE + HashShardingUtils.shardingSequenceFor(sha256, SHARDING_COUNT)
        val criteria = buildReferenceCriteria(sha256, credentialsKey).and(COUNT).gt(0)
        val query = Query(criteria)
        val update = Update().inc(COUNT, -1)
        val result = driveMongoTemplate.updateFirst(query, update, collectionName)
        if (result.modifiedCount == 1L) {
            logger.info("Decrement references of drive file [{}] on credentialsKey [{}].", sha256, credentialsKey)
            return true
        }

        val referenceQuery = Query(buildReferenceCriteria(sha256, credentialsKey))
        val reference = driveMongoTemplate.findOne(referenceQuery, Document::class.java, collectionName)
        if (reference == null) {
            logger.error(
                "Failed to decrement reference of drive file [{}] on credentialsKey [{}].", sha256, credentialsKey
            )
            driveMongoTemplate.upsert(referenceQuery, Update().inc(COUNT, 0), collectionName)
            logger.info(
                "Compensate create drive file reference [{}] on credentialsKey [{}] with count [0].",
                sha256,
                credentialsKey
            )
            return true
        }
        logger.error(
            "Failed to decrement reference of drive file [{}] on credentialsKey [{}]: reference count is 0.",
            sha256,
            credentialsKey
        )
        return false
    }

    private fun buildReferenceCriteria(sha256: String, credentialsKey: String?): Criteria {
        return Criteria.where(SHA256).`is`(sha256).and(CREDENTIALS).`is`(credentialsKey)
    }

    private fun getCredentialsKey(projectId: String, repoName: String): String? {
        return credentialsKeyCache.get(RepositoryId(projectId, repoName)).orElse(null)
    }

    private val credentialsKeyCache: LoadingCache<RepositoryId, Optional<String>> = CacheBuilder.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(30, TimeUnit.MINUTES)
        .build(CacheLoader.from { key -> Optional.ofNullable(loadCredentialsKey(key!!)) })

    private fun loadCredentialsKey(repositoryId: RepositoryId): String? {
        val criteria = Criteria.where(PROJECT).isEqualTo(repositoryId.projectId)
            .and(NAME).isEqualTo(repositoryId.repoName)
        val query = Query.query(criteria)
        query.fields().include(CREDENTIALS)
        val repository = mongoTemplate.findOne(query, Document::class.java, COLLECTION_REPOSITORY)
            ?: throw RepoNotFoundException("${repositoryId.projectId}/${repositoryId.repoName}")
        return repository[CREDENTIALS] as String?
    }

    private fun resolveDriveBlockCollectionName(projectId: String, repoName: String, ino: Long): String {
        val shardingSequence = HashShardingUtils.shardingSequenceFor(listOf(projectId, repoName, ino), SHARDING_COUNT)
        return "${COLLECTION_DRIVE_BLOCK_NODE_PREFIX}_$shardingSequence"
    }

    data class DriveNode(
        val id: String,
        val projectId: String,
        val repoName: String,
        val ino: Long,
        val targetIno: Long?,
        val realIno: Long,
        val snapSeq: Long,
        val deleteSnapSeq: Long,
        val deleted: LocalDateTime?,
    )

    data class RepositoryId(val projectId: String, val repoName: String)

    companion object {
        private val logger = LoggerFactory.getLogger(DeletedDriveNodeCleanupJob::class.java)
        private const val COLLECTION_DRIVE_NODE_PREFIX = "drive_node_"
        private const val COLLECTION_DRIVE_BLOCK_NODE_PREFIX = "drive_block_node"
        private const val COLLECTION_DRIVE_FILE_REFERENCE = "drive_file_reference_"
        private const val COLLECTION_REPOSITORY = "repository"
        private const val COLLECTION_DRIVE_SNAPSHOT = "drive_snapshot"
        private const val SNAPSHOT_SEQ = "snapSeq"
    }
}
