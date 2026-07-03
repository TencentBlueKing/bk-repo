package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.BasicQuery
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 补偿消费后即时校验（§3.17.3 第二层）。
 */
@Component
@ConditionalOnBean(MongoRoutingRegistry::class)
class CompensationPostCheck(
    private val routingRegistry: MongoRoutingRegistry,
    private val defaultMongoTemplate: MongoTemplate,
) {

    fun postReplayCheck(task: Document) {
        val operationType = task.getString(FIELD_OPERATION_TYPE) ?: return
        if (operationType !in CHECK_OPS) return
        val collectionName = task.getString(FIELD_COLLECTION_NAME) ?: return
        val compensatedTemplate = resolveTargetTemplate(task) ?: return
        val writeRoute = routingRegistry.resolveWriteRoute(
            collectionName,
            task.getString(FIELD_ROUTING_KEY),
            defaultMongoTemplate,
        )
        val primaryTemplate = writeRoute.primary
        when (operationType) {
            OP_REMOVE -> checkRemove(primaryTemplate, compensatedTemplate, collectionName, task)
            OP_UPDATE_MULTI -> checkUpdateMulti(primaryTemplate, compensatedTemplate, collectionName, task)
            else -> checkDocumentMatch(primaryTemplate, compensatedTemplate, collectionName, task)
        }
    }

    private fun checkRemove(
        primaryTemplate: MongoTemplate,
        secondaryTemplate: MongoTemplate,
        collectionName: String,
        task: Document,
    ) {
        val primaryKey = task.getString(FIELD_PRIMARY_KEY)
        if (primaryKey != null) {
            val idQuery = Query(Criteria.where("_id").`is`(primaryKey))
            val primaryDoc = primaryTemplate.findOne(idQuery, Document::class.java, collectionName)
            val secondaryDoc = secondaryTemplate.findOne(idQuery, Document::class.java, collectionName)
            if (primaryDoc != null || secondaryDoc != null) {
                report(task, "REMOVE doc still exists: primary=${primaryDoc != null}, secondary=${secondaryDoc != null}")
            }
            return
        }
        val queryDoc = task.get(FIELD_QUERY, Document::class.java) ?: return
        val query = BasicQuery(queryDoc)
        val primaryCount = primaryTemplate.count(query, collectionName)
        val secondaryCount = secondaryTemplate.count(query, collectionName)
        if (primaryCount != secondaryCount) {
            report(task, "REMOVE count mismatch: primary=$primaryCount, secondary=$secondaryCount")
        }
    }

    private fun checkUpdateMulti(
        primaryTemplate: MongoTemplate,
        secondaryTemplate: MongoTemplate,
        collectionName: String,
        task: Document,
    ) {
        val queryDoc = task.get(FIELD_QUERY, Document::class.java) ?: return
        val query = BasicQuery(queryDoc)
        val primaryCount = primaryTemplate.count(query, collectionName)
        val secondaryCount = secondaryTemplate.count(query, collectionName)
        if (primaryCount != secondaryCount) {
            report(task, "UPDATE_MULTI count mismatch: primary=$primaryCount, secondary=$secondaryCount")
        }
    }

    private fun checkDocumentMatch(
        primaryTemplate: MongoTemplate,
        secondaryTemplate: MongoTemplate,
        collectionName: String,
        task: Document,
    ) {
        val primaryKey = task.getString(FIELD_PRIMARY_KEY) ?: return
        val idQuery = Query(Criteria.where("_id").`is`(primaryKey))
        val primaryDoc = primaryTemplate.findOne(idQuery, Document::class.java, collectionName)
        val secondaryDoc = secondaryTemplate.findOne(idQuery, Document::class.java, collectionName)
        if (primaryDoc == null || secondaryDoc == null) {
            report(task, "doc missing after compensation")
            return
        }
        if (primaryDoc["_id"] != secondaryDoc["_id"]) {
            report(task, "_id mismatch: primary=${primaryDoc["_id"]}, secondary=${secondaryDoc["_id"]}")
            return
        }
        for (field in FIELDS_TO_CHECK) {
            if (primaryDoc[field] != secondaryDoc[field]) {
                report(task, "field mismatch on $field: primary=${primaryDoc[field]}, secondary=${secondaryDoc[field]}")
            }
        }
    }

    private fun report(task: Document, reason: String) {
        logger.warn("Post-check failed: {}, collection={}", reason, task.getString(FIELD_COLLECTION_NAME))
        recordInconsistency(task, reason)
    }

    private fun recordInconsistency(task: Document, reason: String) {
        val doc = Document().apply {
            put("ruleName", task.getString(FIELD_RULE_NAME))
            put("routingKey", task.getString(FIELD_ROUTING_KEY))
            put("collectionName", task.getString(FIELD_COLLECTION_NAME))
            put("primaryKey", task.getString(FIELD_PRIMARY_KEY))
            put("operationType", task.getString(FIELD_OPERATION_TYPE))
            put("reason", reason)
            put("createdAt", LocalDateTime.now())
        }
        runCatching {
            defaultMongoTemplate.insert(doc, INCONSISTENCY_COLLECTION)
        }.onFailure {
            logger.error("Failed to persist inconsistency record: {}", it.message)
        }
    }

    private fun resolveTargetTemplate(task: Document): MongoTemplate? =
        if (task.getBoolean(FIELD_TARGET_USE_DEFAULT, false)) {
            defaultMongoTemplate
        } else {
            val ruleName = task.getString(FIELD_RULE_NAME) ?: return null
            val instanceName = task.getString(FIELD_TARGET_INSTANCE) ?: return null
            routingRegistry.primaryTemplateByInstance(ruleName, instanceName)
        }

    companion object {
        private val logger = LoggerFactory.getLogger(CompensationPostCheck::class.java)
        private const val INCONSISTENCY_COLLECTION = "mongo_inconsistency_log"
        private val CHECK_OPS = setOf(
            OP_INSERT, OP_SAVE, OP_UPSERT, OP_FIND_AND_MODIFY,
            OP_REMOVE, OP_UPDATE_FIRST, OP_UPDATE_MULTI,
        )
        private val FIELDS_TO_CHECK = listOf("projectId", "fullPath", "deleted", "sha256", "createdDate")
        private const val OP_INSERT = "INSERT"
        private const val OP_SAVE = "SAVE"
        private const val OP_UPSERT = "UPSERT"
        private const val OP_FIND_AND_MODIFY = "FIND_AND_MODIFY"
        private const val OP_REMOVE = "REMOVE"
        private const val OP_UPDATE_FIRST = "UPDATE_FIRST"
        private const val OP_UPDATE_MULTI = "UPDATE_MULTI"
        private const val FIELD_RULE_NAME = "ruleName"
        private const val FIELD_ROUTING_KEY = "routingKey"
        private const val FIELD_COLLECTION_NAME = "collectionName"
        private const val FIELD_OPERATION_TYPE = "operationType"
        private const val FIELD_PRIMARY_KEY = "primaryKey"
        private const val FIELD_QUERY = "query"
        private const val FIELD_TARGET_USE_DEFAULT = "targetUseDefault"
        private const val FIELD_TARGET_INSTANCE = "targetInstance"
    }
}
