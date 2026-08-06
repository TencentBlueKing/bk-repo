package com.tencent.bkrepo.job.batch.task.drive

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.model.TRepository
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.LAST_MODIFIED_DATE
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.task.clean.DeletedRepositoryCleanupJob.Repository
import com.tencent.bkrepo.job.config.properties.DriveDeletedRepositoryCleanupJobProperties
import org.bson.Document
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * 清理已软删除的 DRIVE 仓库
 * 当drive服务使用独立数据库时需要为job服务也添加对应数据库配置，否则该任务会只清理repository漏清理关联资源
 */
@Component
class DriveDeletedRepositoryCleanupJob(
    private val properties: DriveDeletedRepositoryCleanupJobProperties,
    @Qualifier("driveMongoTemplate")
    private val driveMongoTemplate: MongoTemplate,
) : DefaultContextMongoDbJob<Repository>(properties) {

    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)

    override fun collectionNames(): List<String> {
        return listOf(COLLECTION_REPOSITORY)
    }

    override fun buildQuery(): Query {
        val criteria = Criteria.where(DELETED_DATE).ne(null)
            .and(TRepository::type.name).isEqualTo(RepositoryType.DRIVE.name)
        return Query(criteria)
    }

    override fun run(row: Repository, collectionName: String, context: JobContext) {
        // 删除所有快照
        deleteSnapshots(row.projectId, row.name)

        // 删除所有drive-node
        val driveNodeCollectionName = resolveDriveNodeCollectionName(row.projectId, row.name)
        if (hasUndeletedDriveNode(row.projectId, row.name, driveNodeCollectionName)) {
            val currentSnapSeq = resolveCurrentSnapSeq(row.projectId, row.name)
            if (currentSnapSeq == null) {
                logger.error("Skip cleanup deleted drive repository[${row.projectId}/${row.name}], snapSeq not found.")
                return
            }
            markAllDriveNodesDeleted(row.projectId, row.name, currentSnapSeq, driveNodeCollectionName)
        }

        // 检查所有drive-node已物理删除
        val remainDriveNode = hasDriveNode(row.projectId, row.name, driveNodeCollectionName)
        if (remainDriveNode) {
            logger.info(
                "Skip removing repository[${row.projectId}/${row.name}] for drive nodes still exist."
            )
            return
        }

        // 删除快照序列号
        deleteSnapSeq(row.projectId, row.name)

        // 删除仓库
        val repoQuery = Query.query(Criteria.where(ID).isEqualTo(row.id))
        mongoTemplate.remove(repoQuery, collectionName)
        logger.info("Clean up deleted drive repository[${row.projectId}/${row.name}] success.")
    }

    override fun entityClass(): KClass<Repository> {
        return Repository::class
    }

    override fun mapToEntity(row: Map<String, Any?>) = Repository.from(row)

    private fun resolveCurrentSnapSeq(projectId: String, repoName: String): Long? {
        val query = Query.query(
            Criteria.where(PROJECT).isEqualTo(projectId)
                .and(REPO).isEqualTo(repoName)
        )
        val snapSeq = driveMongoTemplate.findOne(query, Map::class.java, COLLECTION_DRIVE_SNAP_SEQ)
        return snapSeq?.get("snapSeq").toString().toLongOrNull()
    }

    private fun markAllDriveNodesDeleted(projectId: String, repoName: String, snapSeq: Long, collectionName: String) {
        var querySize: Int
        do {
            val query = Query.query(activeDriveNodeCriteria(projectId, repoName))
                .limit(properties.batchSize)
            query.fields().include(ID)
            val driveNodes = driveMongoTemplate.find<Document>(query, collectionName)
            if (driveNodes.isEmpty()) {
                break
            }
            val driveNodeIds = driveNodes.mapNotNull { it.getObjectId(ID) }
            if (driveNodeIds.isEmpty()) {
                break
            }
            val now = LocalDateTime.now()
            markDriveNodesDeleted(projectId, repoName, driveNodeIds, snapSeq, now, collectionName)
            querySize = driveNodes.size
        } while (querySize == properties.batchSize)
    }

    private fun markDriveNodesDeleted(
        projectId: String,
        repoName: String,
        nodeIds: List<ObjectId>,
        snapSeq: Long,
        now: LocalDateTime,
        collectionName: String,
    ) {
        val criteria = Criteria.where(ID).`in`(nodeIds)
            .and(PROJECT).isEqualTo(projectId)
            .and(REPO).isEqualTo(repoName)
            .and(DELETED_DATE).isEqualTo(null)
            .and(DELETE_SNAP_SEQ).isEqualTo(Long.MAX_VALUE)
        val query = Query(criteria)
        val update = Update()
            .set(LAST_MODIFIED_DATE, now)
            .set(DELETE_SNAP_SEQ, snapSeq)
            .set(DELETED_DATE, now)
        driveMongoTemplate.updateMulti(query, update, collectionName)
    }

    private fun hasUndeletedDriveNode(projectId: String, repoName: String, collectionName: String): Boolean {
        val query = Query.query(activeDriveNodeCriteria(projectId, repoName)).limit(1)
        return driveMongoTemplate.exists(query, collectionName)
    }

    private fun hasDriveNode(projectId: String, repoName: String, collectionName: String): Boolean {
        val criteria = Criteria.where(PROJECT).isEqualTo(projectId)
            .and(REPO).isEqualTo(repoName)
        val query = Query.query(criteria).limit(1)
        return driveMongoTemplate.exists(query, collectionName)
    }

    private fun deleteSnapshots(projectId: String, repoName: String) {
        val query = Query.query(
            Criteria.where(PROJECT).isEqualTo(projectId)
                .and(REPO).isEqualTo(repoName)
        )
        driveMongoTemplate.remove(query, COLLECTION_DRIVE_SNAPSHOT)
    }

    private fun deleteSnapSeq(projectId: String, repoName: String) {
        val query = Query.query(
            Criteria.where(PROJECT).isEqualTo(projectId)
                .and(REPO).isEqualTo(repoName)
        )
        driveMongoTemplate.remove(query, COLLECTION_DRIVE_SNAP_SEQ)
    }

    private fun activeDriveNodeCriteria(projectId: String, repoName: String): Criteria {
        return Criteria.where(PROJECT).isEqualTo(projectId)
            .and(REPO).isEqualTo(repoName)
            .and(DELETED_DATE).isEqualTo(null)
            .and(DELETE_SNAP_SEQ).isEqualTo(Long.MAX_VALUE)
    }

    private fun resolveDriveNodeCollectionName(projectId: String, repoName: String): String {
        val shardingSequence = HashShardingUtils.shardingSequenceFor(listOf(projectId, repoName), SHARDING_COUNT)
        return "${COLLECTION_DRIVE_NODE}_$shardingSequence"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DriveDeletedRepositoryCleanupJob::class.java)

        private const val COLLECTION_REPOSITORY = "repository"
        private const val COLLECTION_DRIVE_NODE = "drive_node"
        private const val COLLECTION_DRIVE_SNAPSHOT = "drive_snapshot"
        private const val COLLECTION_DRIVE_SNAP_SEQ = "drive_snap_seq"
        private const val DELETE_SNAP_SEQ = "deleteSnapSeq"
    }
}
