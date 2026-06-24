package com.tencent.bkrepo.common.metadata.routing

import com.tencent.bkrepo.common.mongo.api.routing.BatchQueryGroup
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component

@Component
class NodeBatchQueryHelper(
    private val defaultMongoTemplate: MongoTemplate,
    @Autowired(required = false)
    private val registry: MongoRoutingRegistry? = null,
) {

    fun buildGroups(collectionNames: List<String>): List<BatchQueryGroup>? {
        val reg = registry ?: return null
        val routedProjects = reg.routedProjectIds(NODE_RULE)
        val shardRoutedCollections = reg.shardRoutedCollections(NODE_RULE)
        if (routedProjects.isEmpty() && shardRoutedCollections.isEmpty()) return null

        val groups = mutableListOf<BatchQueryGroup>()

        val defaultCollections = collectionNames.filter { it !in shardRoutedCollections }
        if (defaultCollections.isNotEmpty()) {
            val customizer: (Query) -> Query = if (routedProjects.isNotEmpty()) {
                if (routedProjects.size > WHITELIST_THRESHOLD) {
                    val remaining = reg.allKnownProjectIds(NODE_RULE) - routedProjects
                    if (remaining.isNotEmpty()) {
                        { query ->
                            Query.of(query).addCriteria(Criteria.where(PROJECT_FIELD).`in`(remaining))
                        }
                    } else {
                        { it }
                    }
                } else {
                    { query ->
                        Query.of(query).addCriteria(Criteria.where(PROJECT_FIELD).nin(routedProjects))
                    }
                }
            } else {
                { it }
            }
            groups += BatchQueryGroup(
                instanceId = DEFAULT_INSTANCE,
                template = defaultMongoTemplate,
                collectionNames = defaultCollections,
                criteriaCustomizer = customizer,
            )
        }

        val allInstanceNames = (
            reg.projectsByInstance(NODE_RULE).keys + reg.shardsByInstance(NODE_RULE).keys
            ).toSet()
        for (instanceName in allInstanceNames) {
            val template = reg.secondaryTemplateByInstance(NODE_RULE, instanceName) ?: continue

            val shardCols = reg.shardsByInstance(NODE_RULE)[instanceName].orEmpty()
                .filter { it in collectionNames }
            if (shardCols.isNotEmpty()) {
                groups += BatchQueryGroup(
                    instanceId = instanceName,
                    template = template,
                    collectionNames = shardCols,
                    criteriaCustomizer = { it },
                )
            }

            val projectsForInstance = reg.projectsByInstance(NODE_RULE)[instanceName].orEmpty()
            val nonShardCols = collectionNames.filter { it !in shardRoutedCollections }
            if (projectsForInstance.isNotEmpty() && nonShardCols.isNotEmpty()) {
                groups += BatchQueryGroup(
                    instanceId = instanceName,
                    template = template,
                    collectionNames = nonShardCols,
                    criteriaCustomizer = { query ->
                        Query.of(query).addCriteria(
                            Criteria.where(PROJECT_FIELD).`in`(projectsForInstance),
                        )
                    },
                )
            }
        }

        return groups.takeIf { it.isNotEmpty() }
    }

    companion object {
        const val PROJECT_FIELD = "projectId"
        private const val NODE_RULE = "node"
        private const val WHITELIST_THRESHOLD = 20
        private const val DEFAULT_INSTANCE = "default"
    }
}
