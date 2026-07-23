package com.tencent.bkrepo.job.batch.task.sync

import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

class CollectionFamilyMigrationScanStrategy(
    override val ruleName: String,
    private val defaultMongoTemplate: MongoTemplate,
    private val properties: MongoMultiInstanceProperties,
) : MigrationScanStrategy {

    override val supportsCleanup: Boolean = false
    override val syncFailedCollection: String = OPLOG_SYNC_FAILED_COLLECTION

    override fun syncFailedOwnerId(task: MigrationSyncTask): String = task.ruleName

    override fun shardCollections(task: MigrationSyncTask): List<String> {
        val prefix = properties.rules?.get(ruleName)?.collectionPrefix?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        return defaultMongoTemplate.db.listCollectionNames()
            .asSequence()
            .filter { it.startsWith(prefix) }
            .sorted()
            .toList()
    }

    override fun buildPageQuery(
        task: MigrationSyncTask,
        collectionName: String,
        pageLastId: ObjectId,
    ): Query = Query(Criteria.where(ID).gt(pageLastId))

    override fun cleanupCriteria(task: MigrationSyncTask): Criteria = Criteria()

    companion object {
        const val ARTIFACT_OPLOG_RULE = "artifact-oplog"
        private const val OPLOG_SYNC_FAILED_COLLECTION = "oplog_sync_failed"
    }
}
