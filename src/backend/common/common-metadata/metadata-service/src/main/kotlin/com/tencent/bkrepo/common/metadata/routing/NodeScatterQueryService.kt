package com.tencent.bkrepo.common.metadata.routing

import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.routing.ScatterMongoTemplateProvider
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * 无 projectId 条件的跨实例散发查询（如按 sha256 查找引用）。
 */
class NodeScatterQueryService(
    private val defaultTemplate: MongoTemplate,
    private val registry: MongoRoutingRegistry,
    private val executor: Executor,
    private val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
    private val mode: ScatterMode = ScatterMode.STRICT,
    private val metrics: com.tencent.bkrepo.common.mongo.routing.MongoRoutingMetrics? = null,
    private val scatterTemplates: ScatterMongoTemplateProvider? = null,
) {

    enum class ScatterMode {
        STRICT,
        DEGRADE,
    }

    private data class ScatterGroup(
        val template: MongoTemplate,
        val collectionNames: List<String>,
        val queryPatch: (Query) -> Query,
    )

    fun isScatterReadEnabled(): Boolean =
        registry.routedProjectIds(NODE_RULE).isNotEmpty() ||
            registry.shardRoutedCollections(NODE_RULE).isNotEmpty()

    fun scatterFind(
        query: Query,
        clazz: Class<TNode>,
        collectionNames: List<String>,
    ): List<TNode> {
        val started = System.nanoTime()
        val groups = buildScatterGroups(collectionNames)
        val futures = groups.flatMap { group ->
            val patchedQuery = group.queryPatch(query)
            group.collectionNames.map { col ->
                CompletableFuture.supplyAsync(
                    { findOnTemplate(group.template, patchedQuery, clazz, col) },
                    executor,
                )
            }
        }
        return joinScatterFutures(futures, started)
    }

    fun pageBySha256(
        sha256: String,
        pageRequest: PageRequest,
        collectionNames: List<String>,
    ): Page<TNode> {
        val offset = pageRequest.pageNumber.toLong() * pageRequest.pageSize
        if (offset > MAX_SCATTER_OFFSET) {
            throw BadRequestException(
                CommonMessageCode.PARAMETER_INVALID,
                "Scatter query does not support deep pagination (offset > $MAX_SCATTER_OFFSET)",
            )
        }
        val query = sha256Query(sha256)
            .limit((offset + pageRequest.pageSize).toInt().coerceAtMost(MAX_SCATTER_FETCH))
        val all = scatterFind(query, TNode::class.java, collectionNames)
        val total = all.size.toLong()
        val start = offset.toInt().coerceAtMost(all.size)
        val end = (start + pageRequest.pageSize).coerceAtMost(all.size)
        return PageImpl(all.subList(start, end), pageRequest, total)
    }

    private fun joinScatterFutures(
        futures: List<CompletableFuture<List<TNode>>>,
        startedNanos: Long,
    ): List<TNode> {
        if (futures.isEmpty()) return emptyList()
        return try {
            CompletableFuture.allOf(*futures.toTypedArray())
                .get(timeoutSeconds, TimeUnit.SECONDS)
            futures.flatMap { it.join() }.distinctBy { it.id }.also {
                metrics?.recordScatterQuery(System.nanoTime() - startedNanos, partial = false)
            }
        } catch (e: Exception) {
            if (mode == ScatterMode.STRICT) {
                metrics?.recordScatterQuery(System.nanoTime() - startedNanos, partial = false)
                logger.error("Scatter query STRICT mode: timeout or error, aborting. ${e.message}")
                throw BadRequestException(
                    CommonMessageCode.SERVICE_CALL_ERROR,
                    "Scatter query failed: some shards timed out or errored. Retry later.",
                )
            }
            logger.warn(
                "Scatter query DEGRADE mode: timeout or partial error (${e.message}), returning partial results",
            )
            futures.filter { it.isDone && !it.isCompletedExceptionally }
                .flatMap { it.join() }.distinctBy { it.id }.also {
                    metrics?.recordScatterQuery(System.nanoTime() - startedNanos, partial = true)
                }
        }
    }

    private fun findOnTemplate(
        template: MongoTemplate,
        query: Query,
        clazz: Class<TNode>,
        collection: String,
    ): List<TNode> = if (mode == ScatterMode.STRICT) {
        template.find(query, clazz, collection)
    } else {
        runCatching { template.find(query, clazz, collection) }
            .onFailure { logger.warn("Scatter query failed on collection $collection: ${it.message}") }
            .getOrDefault(emptyList())
    }

    private fun defaultReadTemplate(): MongoTemplate =
        scatterTemplates?.defaultTemplate() ?: defaultTemplate

    private fun heavyReadTemplate(instanceName: String): MongoTemplate? =
        scatterTemplates?.heavyTemplate(instanceName)
            ?: registry.primaryTemplateByInstance(NODE_RULE, instanceName)

    private fun sha256Query(sha256: String): Query = Query(
        Criteria.where(TNode::sha256.name).`is`(sha256)
            .and(TNode::folder.name).`is`(false)
            .and(TNode::deleted.name).`is`(null),
    )

    private fun buildScatterGroups(collectionNames: List<String>): List<ScatterGroup> {
        val routedProjects = registry.routedProjectIds(NODE_RULE)
        val projectsByInstance = registry.projectsByInstance(NODE_RULE)
        val shardRoutedCollections = registry.shardRoutedCollections(NODE_RULE)
        val shardsByInstance = registry.shardsByInstance(NODE_RULE)

        val groups = mutableListOf<ScatterGroup>()

        val defaultCollections = collectionNames.filter { it !in shardRoutedCollections }
        val defaultPatch: (Query) -> Query = if (routedProjects.isNotEmpty()) {
            { q -> Query.of(q).addCriteria(Criteria.where(PROJECT_FIELD).nin(routedProjects)) }
        } else {
            { it }
        }
        if (defaultCollections.isNotEmpty()) {
            groups += ScatterGroup(defaultReadTemplate(), defaultCollections, defaultPatch)
        }

        val allInstanceNames = (projectsByInstance.keys + shardsByInstance.keys).toSet()
        allInstanceNames.forEach { instanceName ->
            val tmpl = heavyReadTemplate(instanceName) ?: return@forEach
            val shardCollections = shardsByInstance[instanceName].orEmpty()
                .filter { it in collectionNames }
            if (shardCollections.isNotEmpty()) {
                groups += ScatterGroup(tmpl, shardCollections) { it }
            }
            val projects = projectsByInstance[instanceName].orEmpty()
            val projectCollections = collectionNames.filter { it !in shardRoutedCollections }
            if (projects.isEmpty() || projectCollections.isEmpty()) return@forEach
            groups += ScatterGroup(tmpl, projectCollections) { q ->
                Query.of(q).addCriteria(Criteria.where(PROJECT_FIELD).`in`(projects))
            }
        }

        return groups
    }

    companion object {
        private const val DEFAULT_TIMEOUT_SECONDS = 5L
        private const val MAX_SCATTER_OFFSET = 10_000L
        private const val MAX_SCATTER_FETCH = 10_000
        private const val NODE_RULE = "node"
        private const val PROJECT_FIELD = "projectId"
        private val logger = LoggerFactory.getLogger(NodeScatterQueryService::class.java)
    }
}
