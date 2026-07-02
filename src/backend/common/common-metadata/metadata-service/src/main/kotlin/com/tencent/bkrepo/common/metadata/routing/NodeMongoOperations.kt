package com.tencent.bkrepo.common.metadata.routing

import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.mongo.routing.MongoDualWriteCompensationService
import com.tencent.bkrepo.common.mongo.routing.MongoDualWriteSupport
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.WriteRoute
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.UpdateDefinition
import org.springframework.data.util.Pair

/**
 * Job 层写 node_* 集合的统一接口。
 */
interface NodeMongoOperations {
    fun remove(projectId: String, query: Query, collectionName: String): DeleteResult

    fun updateFirst(
        projectId: String,
        query: Query,
        update: Update,
        collectionName: String
    ): UpdateResult

    fun updateMulti(
        projectId: String,
        query: Query,
        update: Update,
        collectionName: String
    ): UpdateResult

    fun upsert(
        projectId: String,
        query: Query,
        update: Update,
        collectionName: String
    ): UpdateResult

    fun findAndModify(
        projectId: String,
        query: Query,
        update: Update,
        options: FindAndModifyOptions,
        collectionName: String
    ): TNode?

    fun bulkOps(projectId: String, collectionName: String): BulkOperations

    fun save(projectId: String, entity: Any, collectionName: String): Any

    fun bulkUpdateOne(
        projectId: String,
        collectionName: String,
        clauses: List<Pair<Query, UpdateDefinition>>,
    )
}

class DefaultNodeMongoOperations(
    private val registry: MongoRoutingRegistry,
    private val defaultTemplate: MongoTemplate,
    private val compensationService: MongoDualWriteCompensationService? = null,
) : NodeMongoOperations {

    private fun writeRoute(projectId: String, collectionName: String): WriteRoute =
        registry.resolveWriteRoute(collectionName, projectId, defaultTemplate)

    private fun <T> executePrimaryWrite(
        route: WriteRoute,
        collectionName: String,
        action: (MongoTemplate) -> T,
    ): T = MongoDualWriteSupport.executePrimaryWrite(
        route, collectionName, defaultTemplate, registry, action,
    )

    private fun submitSecondaryWrite(
        route: WriteRoute,
        collectionName: String,
        enqueue: () -> Unit,
        action: (MongoTemplate) -> Unit,
    ) = MongoDualWriteSupport.submitSecondaryWrite(route, collectionName, enqueue, action)

    override fun remove(projectId: String, query: Query, collectionName: String): DeleteResult {
        val route = writeRoute(projectId, collectionName)
        val result = executePrimaryWrite(route, collectionName) {
            it.remove(query, TNode::class.java, collectionName)
        }
        submitSecondaryWrite(route, collectionName, {
            compensationService?.enqueueRemove(route, collectionName, TNode::class.java.name, query)
        }) {
            it.remove(query, TNode::class.java, collectionName)
        }
        return result
    }

    override fun updateFirst(
        projectId: String,
        query: Query,
        update: Update,
        collectionName: String
    ): UpdateResult {
        val route = writeRoute(projectId, collectionName)
        val result = executePrimaryWrite(route, collectionName) {
            it.updateFirst(query, update, collectionName)
        }
        submitSecondaryWrite(route, collectionName, {
            compensationService?.enqueueUpdateFirst(route, collectionName, query, update)
        }) {
            it.updateFirst(query, update, collectionName)
        }
        return result
    }

    override fun updateMulti(
        projectId: String,
        query: Query,
        update: Update,
        collectionName: String
    ): UpdateResult {
        val route = writeRoute(projectId, collectionName)
        val result = executePrimaryWrite(route, collectionName) {
            it.updateMulti(query, update, collectionName)
        }
        submitSecondaryWrite(route, collectionName, {
            compensationService?.enqueueUpdateMulti(route, collectionName, query, update)
        }) {
            it.updateMulti(query, update, collectionName)
        }
        return result
    }

    override fun upsert(
        projectId: String,
        query: Query,
        update: Update,
        collectionName: String
    ): UpdateResult {
        val route = writeRoute(projectId, collectionName)
        val result = executePrimaryWrite(route, collectionName) {
            it.upsert(query, update, collectionName)
        }
        submitSecondaryWrite(route, collectionName, {
            compensationService?.enqueueUpsert(route, collectionName, query, update)
        }) {
            it.upsert(query, update, collectionName)
        }
        return result
    }

    override fun findAndModify(
        projectId: String,
        query: Query,
        update: Update,
        options: FindAndModifyOptions,
        collectionName: String
    ): TNode? {
        val route = writeRoute(projectId, collectionName)
        val result = executePrimaryWrite(route, collectionName) {
            it.findAndModify(query, update, options, TNode::class.java, collectionName)
        }
        submitSecondaryWrite(route, collectionName, {
            compensationService?.enqueueFindAndModify(
                route,
                collectionName,
                query,
                update,
                options,
                TNode::class.java.name,
            )
        }) {
            it.findAndModify(query, update, options, TNode::class.java, collectionName)
        }
        return result
    }

    override fun bulkOps(projectId: String, collectionName: String): BulkOperations =
        writeRoute(projectId, collectionName).primary
            .bulkOps(BulkOperations.BulkMode.UNORDERED, collectionName)

    override fun save(projectId: String, entity: Any, collectionName: String): Any {
        val route = writeRoute(projectId, collectionName)
        val result = executePrimaryWrite(route, collectionName) {
            it.save(entity, collectionName)
        }
        submitSecondaryWrite(route, collectionName, {
            compensationService?.enqueueSave(route, collectionName, entity)
        }) {
            it.save(entity, collectionName)
        }
        return result
    }

    override fun bulkUpdateOne(
        projectId: String,
        collectionName: String,
        clauses: List<Pair<Query, UpdateDefinition>>,
    ) {
        if (clauses.isEmpty()) return
        val route = writeRoute(projectId, collectionName)
        executePrimaryWrite(route, collectionName) { primary ->
            primary.bulkOps(BulkOperations.BulkMode.UNORDERED, collectionName)
                .updateOne(clauses)
                .execute()
        }
        submitSecondaryWrite(route, collectionName, {
            clauses.forEach { clause ->
                val update = clause.second
                if (update is Update) {
                    compensationService?.enqueueUpdateFirst(
                        route,
                        collectionName,
                        clause.first,
                        update,
                    )
                }
            }
        }) { secondary ->
            secondary.bulkOps(BulkOperations.BulkMode.UNORDERED, collectionName)
                .updateOne(clauses)
                .execute()
        }
    }
}

class SimpleNodeMongoOperations(
    private val template: MongoTemplate,
) : NodeMongoOperations {

    override fun remove(projectId: String, query: Query, collectionName: String): DeleteResult =
        template.remove(query, TNode::class.java, collectionName)

    override fun updateFirst(
        projectId: String,
        query: Query,
        update: Update,
        collectionName: String
    ): UpdateResult = template.updateFirst(query, update, collectionName)

    override fun updateMulti(
        projectId: String,
        query: Query,
        update: Update,
        collectionName: String
    ): UpdateResult = template.updateMulti(query, update, collectionName)

    override fun upsert(
        projectId: String,
        query: Query,
        update: Update,
        collectionName: String
    ): UpdateResult = template.upsert(query, update, collectionName)

    override fun findAndModify(
        projectId: String,
        query: Query,
        update: Update,
        options: FindAndModifyOptions,
        collectionName: String
    ): TNode? = template.findAndModify(query, update, options, TNode::class.java, collectionName)

    override fun bulkOps(projectId: String, collectionName: String): BulkOperations =
        template.bulkOps(BulkOperations.BulkMode.UNORDERED, collectionName)

    override fun save(projectId: String, entity: Any, collectionName: String): Any =
        template.save(entity, collectionName)

    override fun bulkUpdateOne(
        projectId: String,
        collectionName: String,
        clauses: List<Pair<Query, UpdateDefinition>>,
    ) {
        if (clauses.isEmpty()) return
        template.bulkOps(BulkOperations.BulkMode.UNORDERED, collectionName)
            .updateOne(clauses)
            .execute()
    }
}
