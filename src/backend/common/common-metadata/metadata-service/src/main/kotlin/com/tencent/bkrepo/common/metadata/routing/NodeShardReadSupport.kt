package com.tencent.bkrepo.common.metadata.routing

import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component

@Component
class NodeShardReadSupport(
    private val defaultMongoTemplate: MongoTemplate,
    @Autowired(required = false)
    private val routingRegistry: MongoRoutingRegistry? = null,
    @Autowired(required = false)
    private val nodeBatchQueryHelper: NodeBatchQueryHelper? = null,
) {

    fun readTemplate(projectId: String, collectionName: String): MongoTemplate =
        routingRegistry?.resolveReadRoute(collectionName, projectId, defaultMongoTemplate)?.template
            ?: defaultMongoTemplate

    fun forEachShardGroup(
        collectionNames: List<String>,
        action: (MongoTemplate, String, (Query) -> Query) -> Unit,
    ) {
        val groups = nodeBatchQueryHelper?.buildGroups(collectionNames)
        if (groups != null) {
            groups.forEach { group ->
                group.collectionNames.forEach { col ->
                    action(group.template, col, group.criteriaCustomizer)
                }
            }
            return
        }
        collectionNames.forEach { col ->
            action(defaultMongoTemplate, col) { Query.of(it) }
        }
    }

    fun updateMultiOnShards(
        shardingCount: Int,
        collectionPrefix: String,
        query: Query,
        update: Update,
    ): UpdateResult {
        var matched = 0L
        var modified = 0L
        val names = (0 until shardingCount).map { "$collectionPrefix$it" }
        forEachShardGroup(names) { template, collection, customizer ->
            val result = template.updateMulti(customizer(query), update, collection)
            matched += result.matchedCount
            modified += result.modifiedCount
        }
        return UpdateResult.acknowledged(matched, modified.toLong(), null)
    }

    fun removeOnShards(
        shardingCount: Int,
        collectionPrefix: String,
        query: Query,
    ): DeleteResult {
        var deleted = 0L
        val names = (0 until shardingCount).map { "$collectionPrefix$it" }
        forEachShardGroup(names) { template, collection, customizer ->
            deleted += template.remove(customizer(query), collection).deletedCount
        }
        return DeleteResult.acknowledged(deleted)
    }

    fun findOnAllNodeInstances(
        collectionName: String,
        query: Query,
        clazz: Class<Map<*, *>>,
    ): Map<*, *>? {
        defaultMongoTemplate.find(query, clazz, collectionName).firstOrNull()?.let { return it }
        val reg = routingRegistry ?: return null
        for (template in reg.allSecondaryTemplates(NODE_RULE).values) {
            if (template === defaultMongoTemplate) continue
            template.find(query, clazz, collectionName).firstOrNull()?.let { return it }
        }
        return null
    }

    companion object {
        private const val NODE_RULE = "node"
    }
}
