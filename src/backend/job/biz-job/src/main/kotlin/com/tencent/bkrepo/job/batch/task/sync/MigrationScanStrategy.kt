package com.tencent.bkrepo.job.batch.task.sync

import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

interface MigrationScanStrategy {
    val ruleName: String
    val supportsCleanup: Boolean
    val syncFailedCollection: String

    fun syncFailedOwnerId(task: MigrationSyncTask): String

    fun shardCollections(task: MigrationSyncTask): List<String>

    fun buildPageQuery(task: MigrationSyncTask, collectionName: String, pageLastId: ObjectId): Query

    fun cleanupCriteria(task: MigrationSyncTask): Criteria
}
