package com.tencent.bkrepo.common.metadata.routing

import com.tencent.bkrepo.common.metadata.properties.BlockNodeProperties
import com.tencent.bkrepo.common.metadata.util.BlockNodeCollectionNaming
import com.tencent.bkrepo.common.mongo.api.routing.BatchQueryGroup
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component

@Component
class NodeBatchQueryHelper(
    private val defaultMongoTemplate: MongoTemplate,
    private val registry: MongoRoutingRegistry? = null,
    private val blockNodeProperties: BlockNodeProperties? = null,
) {

    fun buildGroups(collectionNames: List<String>, ruleName: String = NODE_RULE): List<BatchQueryGroup>? {
        val reg = registry ?: return null
        val routedProjects = reg.routedProjectIds(ruleName)
        val shardRoutedCollections = reg.shardRoutedCollections(ruleName)
        if (routedProjects.isEmpty() && shardRoutedCollections.isEmpty()) return null

        val groups = mutableListOf<BatchQueryGroup>()
        val shardingCount = when (ruleName) {
            NODE_RULE -> SHARDING_COUNT
            BLOCK_NODE_RULE -> BlockNodeCollectionNaming.shardCount(blockNodeProperties)
            else -> resolveShardingCount(collectionNames)
        }

        val defaultCollections = collectionNames.filter { it !in shardRoutedCollections }
        if (defaultCollections.isNotEmpty()) {
            val defaultScanCollections = resolveDefaultScanCollections(
                ruleName = ruleName,
                routedProjects = routedProjects,
                availableCollections = defaultCollections,
                shardingCount = shardingCount,
            )
            if (defaultScanCollections.isNotEmpty()) {
                groups += BatchQueryGroup(
                    instanceId = DEFAULT_INSTANCE,
                    mongoTemplate = defaultMongoTemplate,
                    collectionNames = defaultScanCollections,
                    criteriaCustomizer = createDefaultGroupCustomizer(ruleName, routedProjects),
                )
            }
        }

        val allInstanceNames = (
            reg.projectsByInstance(ruleName).keys + reg.shardsByInstance(ruleName).keys
            ).toSet()
        for (instanceName in allInstanceNames) {
            val template = reg.primaryTemplateByInstance(ruleName, instanceName) ?: continue

            val shardCols = reg.shardsByInstance(ruleName)[instanceName].orEmpty()
                .filter { it in collectionNames }
            if (shardCols.isNotEmpty()) {
                groups += BatchQueryGroup(
                    instanceId = instanceName,
                    mongoTemplate = template,
                    collectionNames = shardCols,
                    criteriaCustomizer = { it },
                )
            }

            val projectsForInstance = reg.projectsByInstance(ruleName)[instanceName].orEmpty()
            val nonShardCols = collectionNames.filter { it !in shardRoutedCollections }
            val projectScanCollections = narrowCollectionsForProjects(
                ruleName = ruleName,
                projectIds = projectsForInstance,
                availableCollections = nonShardCols,
                shardingCount = shardingCount,
            )
            if (projectsForInstance.isNotEmpty() && projectScanCollections.isNotEmpty()) {
                groups += BatchQueryGroup(
                    instanceId = instanceName,
                    mongoTemplate = template,
                    collectionNames = projectScanCollections,
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

    private fun resolveDefaultScanCollections(
        ruleName: String,
        routedProjects: Set<String>,
        availableCollections: List<String>,
        shardingCount: Int,
    ): List<String> {
        if (routedProjects.isEmpty() || routedProjects.size <= WHITELIST_THRESHOLD) {
            return availableCollections
        }
        val remaining = registry?.allKnownProjectIds(ruleName).orEmpty() - routedProjects
        return narrowCollectionsForProjects(ruleName, remaining, availableCollections, shardingCount)
            .ifEmpty { availableCollections }
    }

    private fun createDefaultGroupCustomizer(
        ruleName: String,
        routedProjects: Set<String>,
    ): (Query) -> Query {
        if (routedProjects.isEmpty()) return { it }
        if (routedProjects.size > WHITELIST_THRESHOLD) {
            val allKnown = registry?.allKnownProjectIds(ruleName) ?: emptySet()
            val remaining = allKnown - routedProjects
            return { query ->
                Query.of(query).addCriteria(Criteria.where(PROJECT_FIELD).`in`(remaining))
            }
        }
        return { query ->
            Query.of(query).addCriteria(Criteria.where(PROJECT_FIELD).nin(routedProjects))
        }
    }

    companion object {
        const val PROJECT_FIELD = "projectId"
        private const val NODE_RULE = "node"
        private const val BLOCK_NODE_RULE = "block-node"
        private const val DEFAULT_INSTANCE = "default"
        private const val WHITELIST_THRESHOLD = 20

        internal fun resolveShardingCount(collectionNames: List<String>): Int {
            val sequences = collectionNames.mapNotNull { it.substringAfterLast('_').toIntOrNull() }
            return if (sequences.isEmpty()) SHARDING_COUNT else sequences.max() + 1
        }

        internal fun collectionPrefix(collectionNames: List<String>): String? {
            val first = collectionNames.firstOrNull() ?: return null
            val separator = first.lastIndexOf('_')
            if (separator <= 0) return null
            return first.substring(0, separator)
        }

        internal fun narrowCollectionsForProjects(
            ruleName: String,
            projectIds: Collection<String>,
            availableCollections: List<String>,
            shardingCount: Int,
        ): List<String> {
            if (projectIds.isEmpty() || ruleName != NODE_RULE) {
                return availableCollections
            }
            val prefix = collectionPrefix(availableCollections) ?: return availableCollections
            val targetSequences = projectIds.map {
                HashShardingUtils.shardingSequenceFor(it, shardingCount)
            }.toSet()
            return availableCollections.filter { collection ->
                collection.startsWith("${prefix}_") &&
                    collection.removePrefix("${prefix}_").toIntOrNull() in targetSequences
            }
        }
    }
}
