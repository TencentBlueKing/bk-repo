package com.tencent.bkrepo.common.mongo.api.routing

import org.springframework.data.mongodb.core.query.Criteria

/** 分库路由框架 MongoDB 集合名（统一 mongo_routing_ 前缀，代码只引用此处常量） */
object MongoRoutingCollections {
    const val MIGRATION_STATE = "mongo_routing_migration_state"
    const val SYNC_FAILED = "mongo_routing_sync_failed"
    const val CONFIG = "mongo_routing_config"
    const val COMPENSATION = "mongo_routing_compensation"
    const val RECONCILIATION = "mongo_routing_reconciliation"
    const val INCONSISTENCY = "mongo_routing_inconsistency"

    const val SYNC_FAILED_RULE_NAME = "ruleName"
    const val SYNC_FAILED_OWNER_ID = "ownerId"
    const val SYNC_FAILED_COLLECTION_NAME = "collectionName"
    const val SYNC_FAILED_DOC_ID = "docId"
    const val SYNC_FAILED_ERROR = "error"
    const val SYNC_FAILED_CREATED_AT = "createdAt"

    fun syncFailedOwnerCriteria(ruleName: String, ownerId: String): Criteria =
        Criteria.where(SYNC_FAILED_RULE_NAME).`is`(ruleName).and(SYNC_FAILED_OWNER_ID).`is`(ownerId)

    fun syncFailedDocCriteria(ruleName: String, docId: String): Criteria =
        Criteria.where(SYNC_FAILED_RULE_NAME).`is`(ruleName).and(SYNC_FAILED_DOC_ID).`is`(docId)
}
