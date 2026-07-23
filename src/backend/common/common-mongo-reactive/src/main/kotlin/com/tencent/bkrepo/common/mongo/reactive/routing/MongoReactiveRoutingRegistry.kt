package com.tencent.bkrepo.common.mongo.reactive.routing

import com.mongodb.ConnectionString
import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import com.tencent.bkrepo.common.mongo.api.routing.RuleRoutingState
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import com.tencent.bkrepo.common.mongo.routing.MongoRoutingContext
import com.tencent.bkrepo.common.mongo.routing.RoutingEffectiveState
import com.tencent.bkrepo.common.mongo.api.routing.RouteTarget
import io.micrometer.core.instrument.binder.mongodb.MongoMetricsConnectionPoolListener
import org.springframework.beans.factory.DisposableBean
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.core.query.Query

data class ReactiveReadRoute(
    val template: ReactiveMongoTemplate,
    val fallbackTemplate: ReactiveMongoTemplate? = null,
    val fallbackToDefault: Boolean = false,
)

data class ReactiveWriteRoute(
    val primary: ReactiveMongoTemplate,
    val secondary: ReactiveMongoTemplate? = null,
    val secondaryTarget: RouteTarget? = null,
    val fallbackTemplate: ReactiveMongoTemplate? = null,
    val fallbackToDefault: Boolean = false,
    val routingKey: String? = null,
    val ruleName: String? = null,
    val isDefaultInstance: Boolean = false,
    val syncSecondaryWrite: Boolean = false,
)

/**
 * 多实例响应式路由注册表，与 [com.tencent.bkrepo.common.mongo.routing.MongoRoutingRegistry] 逻辑对齐。
 */
class MongoReactiveRoutingRegistry(
    private val properties: MongoMultiInstanceProperties,
    private val poolMetricsListener: MongoMetricsConnectionPoolListener? = null,
) : DisposableBean {

    private val mongoClients = mutableListOf<MongoClient>()
    private val primaryTemplates: Map<String, Map<String, ReactiveMongoTemplate>>
    private val prefixIndex: List<Triple<String, String, MongoMultiInstanceProperties.RoutingRule>>
    private val fieldCache = java.util.concurrent.ConcurrentHashMap<String, java.lang.reflect.Field?>()

    init {
        primaryTemplates = buildPrimaryTemplates()
        prefixIndex = properties.rules.entries
            .filter { it.value.collectionPrefix.isNotBlank() }
            .map { Triple(it.value.collectionPrefix, it.key, it.value) }
            .sortedByDescending { it.first.length }
    }

    fun routeWrite(
        collectionName: String,
        context: Any?,
        defaultTemplate: ReactiveMongoTemplate,
    ): ReactiveMongoTemplate? {
        val entry = prefixIndex.firstOrNull { collectionName.startsWith(it.first) } ?: return null
        if (effectiveRoutingState(entry.second, entry.third) == RuleRoutingState.OFF) return null
        return resolveWriteRoute(collectionName, context, defaultTemplate).primary
    }

    fun routeRead(
        collectionName: String,
        context: Any?,
        defaultTemplate: ReactiveMongoTemplate,
    ): ReactiveMongoTemplate? {
        val entry = prefixIndex.firstOrNull { collectionName.startsWith(it.first) } ?: return null
        if (effectiveRoutingState(entry.second, entry.third) == RuleRoutingState.OFF) return null
        return resolveReadRoute(collectionName, context, defaultTemplate).template
    }

    fun resolveReadRoute(
        collectionName: String,
        context: Any?,
        defaultTemplate: ReactiveMongoTemplate,
    ): ReactiveReadRoute {
        val entry = prefixIndex.firstOrNull { collectionName.startsWith(it.first) }
            ?: return ReactiveReadRoute(defaultTemplate)
        val (_, ruleName, rule) = entry
        if (effectiveRoutingState(ruleName, rule) == RuleRoutingState.OFF) {
            return ReactiveReadRoute(defaultTemplate)
        }
        return when (rule.routingType) {
            MongoMultiInstanceProperties.RoutingType.NONE -> {
                if (effectiveRoutingState(ruleName, rule) == RuleRoutingState.DUAL_WRITE) {
                    return ReactiveReadRoute(defaultTemplate)
                }
                val instanceName = rule.instances.keys.firstOrNull()
                    ?: return ReactiveReadRoute(defaultTemplate)
                val template = primaryTemplates[ruleName]?.get(instanceName)
                    ?: return ReactiveReadRoute(defaultTemplate)
                ReactiveReadRoute(
                    template = template,
                    fallbackTemplate = defaultTemplate,
                    fallbackToDefault = shouldFallbackToDefault(ruleName, rule, instanceName),
                )
            }
            MongoMultiInstanceProperties.RoutingType.PROJECT,
            MongoMultiInstanceProperties.RoutingType.COLLECTION -> {
                val key = extractKey(context, rule.routingKeyField) ?: MongoRoutingContext.get(ruleName)
                val hitProjectRouting = key != null && key in rule.projectRouting
                val instanceName = rule.projectRouting[key]
                    ?: rule.shardRouting[collectionName]
                    ?: return ReactiveReadRoute(defaultTemplate)
                if (hitProjectRouting) {
                    if (isProjectInDualWrite(ruleName, key!!)) {
                        return ReactiveReadRoute(defaultTemplate)
                    }
                    if (!shouldWriteToHeavy(ruleName, rule, key)) {
                        return ReactiveReadRoute(defaultTemplate)
                    }
                }
                val template = primaryTemplates[ruleName]?.get(instanceName)
                    ?: return ReactiveReadRoute(defaultTemplate)
                ReactiveReadRoute(
                    template = template,
                    fallbackTemplate = defaultTemplate,
                    fallbackToDefault = shouldFallbackToDefault(ruleName, rule, instanceName, key),
                )
            }
        }
    }

    fun resolveWriteRoute(
        collectionName: String,
        context: Any?,
        defaultTemplate: ReactiveMongoTemplate,
    ): ReactiveWriteRoute {
        val entry = prefixIndex.firstOrNull { collectionName.startsWith(it.first) }
            ?: return ReactiveWriteRoute(defaultTemplate)
        val (_, ruleName, rule) = entry
        if (effectiveRoutingState(ruleName, rule) == RuleRoutingState.OFF) {
            return ReactiveWriteRoute(defaultTemplate)
        }
        return when (rule.routingType) {
            MongoMultiInstanceProperties.RoutingType.NONE -> {
                val instanceName = rule.instances.keys.firstOrNull()
                    ?: return ReactiveWriteRoute(defaultTemplate)
                val instanceTmpl = primaryTemplates[ruleName]?.get(instanceName)
                    ?: return ReactiveWriteRoute(defaultTemplate)
                if (effectiveRoutingState(ruleName, rule) == RuleRoutingState.DUAL_WRITE) {
                    // 模式一双写期与模式二统一 Default-first（§1.3.1）
                    ReactiveWriteRoute(
                        primary = defaultTemplate,
                        secondary = instanceTmpl,
                        secondaryTarget = RouteTarget(ruleName, instanceName = instanceName),
                        ruleName = ruleName,
                        isDefaultInstance = true,
                        syncSecondaryWrite = true,
                    )
                } else {
                    ReactiveWriteRoute(
                        primary = instanceTmpl,
                        fallbackTemplate = defaultTemplate,
                        fallbackToDefault = shouldFallbackToDefault(ruleName, rule, instanceName),
                        ruleName = ruleName,
                        isDefaultInstance = false,
                    )
                }
            }
            MongoMultiInstanceProperties.RoutingType.PROJECT,
            MongoMultiInstanceProperties.RoutingType.COLLECTION -> {
                val key = extractKey(context, rule.routingKeyField)
                    ?: MongoRoutingContext.get(ruleName)
                    ?: if (rule.routingType == MongoMultiInstanceProperties.RoutingType.PROJECT) {
                        throw IllegalStateException(
                            "Routing key '${rule.routingKeyField}' is required for write on " +
                                "collection '$collectionName' with rule '$ruleName'"
                        )
                    } else {
                        null
                    }
                val hitProjectRouting = key != null && key in rule.projectRouting
                val instanceName = rule.projectRouting[key]
                    ?: rule.shardRouting[collectionName]
                    ?: return ReactiveWriteRoute(defaultTemplate)
                if (hitProjectRouting && !shouldWriteToHeavy(ruleName, rule, key)) {
                    return ReactiveWriteRoute(
                        primary = defaultTemplate,
                        routingKey = key,
                        ruleName = ruleName,
                        isDefaultInstance = true,
                    )
                }
                val instanceTmpl = primaryTemplates[ruleName]?.get(instanceName)
                    ?: return ReactiveWriteRoute(defaultTemplate)
                val dualWrite = if (hitProjectRouting) {
                    isProjectInDualWrite(ruleName, key!!)
                } else {
                    effectiveRoutingState(ruleName, rule) == RuleRoutingState.DUAL_WRITE
                }
                if (dualWrite) {
                    // 模式二双写：Default 为主路径（先写），Heavy 为副路径（§3.6.3）。
                    // Default 持有全量历史，唯一键冲突在主路径同步 fail-fast 暴露；
                    // 若空 Heavy 先写会成功、Default 后写冲突，导致两侧不可收敛地分叉。
                    ReactiveWriteRoute(
                        primary = defaultTemplate,
                        secondary = instanceTmpl,
                        secondaryTarget = RouteTarget(ruleName, instanceName = instanceName),
                        routingKey = key,
                        ruleName = ruleName,
                        isDefaultInstance = true,
                        syncSecondaryWrite = true,
                    )
                } else {
                    ReactiveWriteRoute(
                        primary = instanceTmpl,
                        fallbackTemplate = defaultTemplate,
                        fallbackToDefault = shouldFallbackToDefault(ruleName, rule, instanceName, key),
                        routingKey = key,
                        ruleName = ruleName,
                        isDefaultInstance = false,
                    )
                }
            }
        }
    }

    fun resolveRuleName(collectionName: String): String? =
        prefixIndex.firstOrNull { collectionName.startsWith(it.first) }?.second

    fun isProjectRoutedOut(ruleName: String, projectId: String): Boolean {
        val rule = properties.rules[ruleName] ?: return false
        if (effectiveRoutingState(ruleName, rule) == RuleRoutingState.OFF) return false
        if (projectId !in rule.projectRouting) return false
        if (isProjectInDualWrite(ruleName, projectId)) return false
        return effectiveRoutingState(ruleName, rule) == RuleRoutingState.ROUTED
    }

    /** G-02：迁出项目禁止写 Default 僵尸副本 */
    fun assertWriteNotZombie(
        route: ReactiveWriteRoute,
        collectionName: String,
        defaultTemplate: ReactiveMongoTemplate,
    ) {
        if (isZombieWrite(route)) {
            val msg = "WRITE_PROTECTION: Attempted reactive write on Default zombie replica. " +
                "projectId=${route.routingKey}, collection=$collectionName, rule=${route.ruleName}"
            throw IllegalStateException(msg)
        }
    }

    private fun isZombieWrite(route: ReactiveWriteRoute): Boolean {
        val key = route.routingKey ?: return false
        val name = route.ruleName ?: return false
        return route.isDefaultInstance && isProjectRoutedOut(name, key)
    }

    private fun isProjectInDualWrite(ruleName: String, projectId: String): Boolean {
        val rule = properties.rules[ruleName] ?: return false
        if (effectiveRoutingState(ruleName, rule) != RuleRoutingState.DUAL_WRITE) return false
        return projectId in rule.projectRouting
    }

    private fun shouldWriteToHeavy(
        ruleName: String,
        rule: MongoMultiInstanceProperties.RoutingRule,
        projectId: String?,
    ): Boolean {
        if (projectId == null || projectId !in rule.projectRouting) return false
        return isProjectInDualWrite(ruleName, projectId) || isProjectRoutedOut(ruleName, projectId)
    }

    private fun shouldFallbackToDefault(
        ruleName: String,
        rule: MongoMultiInstanceProperties.RoutingRule,
        instanceName: String,
        projectId: String? = null,
    ): Boolean {
        val routingKey = projectId ?: MongoRoutingContext.get(ruleName)
        if (routingKey != null && isProjectRoutedOut(ruleName, routingKey)) {
            return false
        }
        return effectiveRoutingState(ruleName, rule) != RuleRoutingState.DUAL_WRITE &&
            (rule.instances[instanceName]?.fallbackBeforeCleanup == true)
    }

    private fun effectiveRoutingState(
        ruleName: String,
        rule: MongoMultiInstanceProperties.RoutingRule,
    ): RuleRoutingState = RoutingEffectiveState.effectiveRoutingState(rule, ruleName = ruleName)

    private fun extractKey(context: Any?, fieldPath: String): String? = when (context) {
        is Query -> extractFromQueryObject(context.queryObject, fieldPath)
        is String -> context
        null -> null
        else -> {
            val cacheKey = "${System.identityHashCode(context.javaClass)}:$fieldPath"
            val cachedField = fieldCache.getOrPut(cacheKey) {
                runCatching {
                    context.javaClass.getDeclaredField(fieldPath).also { it.isAccessible = true }
                }.getOrNull()
            }
            cachedField?.let {
                runCatching { it.get(context) as? String }.getOrNull()
            }
        }
    }

    private fun extractFromQueryObject(queryObject: org.bson.Document, fieldPath: String): String? {
        if (fieldPath.contains('.')) {
            val parts = fieldPath.split('.', limit = 2)
            val nested = queryObject[parts[0]]
            if (nested is org.bson.Document) return extractFromQueryObject(nested, parts[1])
            if (nested is Map<*, *>) return nested[parts[1]] as? String
            return null
        } else {
            queryObject[fieldPath]?.let { return it as? String }
        }
        (queryObject["\$and"] as? List<*>)?.forEach { sub ->
            if (sub is org.bson.Document) {
                extractFromQueryObject(sub, fieldPath)?.let { return it }
            }
        }
        (queryObject["\$or"] as? List<*>)?.mapNotNull { sub ->
            (sub as? org.bson.Document)?.let { extractFromQueryObject(it, fieldPath) }
        }?.distinct()?.singleOrNull()?.let { return it }
        return null
    }

    private fun buildPrimaryTemplates(): Map<String, Map<String, ReactiveMongoTemplate>> =
        properties.rules.entries
            .associate { (ruleName, rule) ->
                ruleName to rule.instances.mapValues { (_, cfg) ->
                    reactiveTemplate(cfg.uri, cfg)
                }
            }

    private fun reactiveTemplate(
        uri: String,
        cfg: MongoMultiInstanceProperties.RoutingRule.InstanceConfig,
    ): ReactiveMongoTemplate {
        val connectionString = ConnectionString(uri)
        val settingsBuilder = com.mongodb.MongoClientSettings.builder()
            .applyConnectionString(connectionString)
            .applyToConnectionPoolSettings { builder ->
                builder.maxSize(cfg.maxPoolSize).minSize(cfg.minPoolSize)
                poolMetricsListener?.let { builder.addConnectionPoolListener(it) }
            }
        val client = MongoClients.create(settingsBuilder.build())
        mongoClients.add(client)
        val database = connectionString.database
            ?: error("MongoDB URI must include database: $uri")
        return ReactiveMongoTemplate(SimpleReactiveMongoDatabaseFactory(client, database))
    }

    override fun destroy() {
        mongoClients.distinct().forEach { client ->
            runCatching { client.close() }
                .onFailure { logger.warn("Failed to close reactive MongoClient: {}", it.message) }
        }
        mongoClients.clear()
    }

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(MongoReactiveRoutingRegistry::class.java)
    }
}