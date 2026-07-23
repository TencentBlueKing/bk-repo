package com.tencent.bkrepo.job.batch.task.sync

import com.tencent.bkrepo.common.mongo.constant.ID
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

class ProjectShardMigrationScanStrategy(
    override val ruleName: String,
    private val shardCollectionsProvider: () -> List<String>,
    override val syncFailedCollection: String,
) : MigrationScanStrategy {

    override val supportsCleanup: Boolean = true

    override fun syncFailedOwnerId(task: MigrationSyncTask): String = task.projectId

    override fun shardCollections(task: MigrationSyncTask): List<String> = shardCollectionsProvider()

    override fun buildPageQuery(
        task: MigrationSyncTask,
        collectionName: String,
        pageLastId: ObjectId,
    ): Query = Query(Criteria.where("projectId").`is`(task.projectId).and(ID).gt(pageLastId))

    override fun cleanupCriteria(task: MigrationSyncTask): Criteria =
        Criteria.where("projectId").`is`(task.projectId)
}
