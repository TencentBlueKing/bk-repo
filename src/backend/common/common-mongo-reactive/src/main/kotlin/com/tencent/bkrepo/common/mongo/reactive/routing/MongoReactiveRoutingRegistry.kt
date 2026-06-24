package com.tencent.bkrepo.common.mongo.reactive.routing

import com.mongodb.ConnectionString
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import com.tencent.bkrepo.common.mongo.routing.MongoRoutingContext
import com.tencent.bkrepo.common.mongo.api.routing.RouteTarget
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
    val syncSecondaryWrite: Boolean = false,
)

/**
 * 多实例响应式路由注册表，与 [com.tencent.bkrepo.common.mongo.routing.MongoRoutingRegistry] 逻辑对齐。
 */
class MongoReactiveRoutingRegistry(
    private val properties: MongoMultiInstanceProperties,
) {

    private val primaryTemplates: Map<String, Map<String, ReactiveMongoTemplate>>
    private val secondaryTemplates: Map<String, Map<String, ReactiveMongoTemplate>>
    private val prefixIndex: List<Triple<String, String, MongoMultiInstanceProperties.RoutingRule>>
    private val fieldCache = java.util.concurrent.ConcurrentHashMap<String, java.lang.reflect.Field?>()

    init {
        primaryTemplates = buildPrimaryTemplates()
        secondaryTemplates = buildSecondaryTemplates()
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
        if (!entry.third.routingEnabled) return null
        return resolveWriteRoute(collectionName, context, defaultTemplate).primary
    }

    fun routeRead(
        collectionName: String,
        context: Any?,
        defaultTemplate: ReactiveMongoTemplate,
    ): ReactiveMongoTemplate? {
        val entry = prefixIndex.firstOrNull { collectionName.startsWith(it.first) } ?: return null
        if (!entry.third.routingEnabled) return null
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
        if (!rule.routingEnabled) return ReactiveReadRoute(defaultTemplate)
        return when (rule.routingType) {
            MongoMultiInstanceProperties.RoutingType.NONE -> {
                if (rule.dualWrite) return ReactiveReadRoute(defaultTemplate)
                val instanceName = rule.instances.keys.firstOrNull()
                    ?: return ReactiveReadRoute(defaultTemplate)
                val template = secondaryTemplates[ruleName]?.get(instanceName)
                    ?: return ReactiveReadRoute(defaultTemplate)
                ReactiveReadRoute(
                    template = template,
                    fallbackTemplate = defaultTemplate,
                    fallbackToDefault = shouldFallbackToDefault(rule, instanceName),
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
                val template = secondaryTemplates[ruleName]?.get(instanceName)
                    ?: return ReactiveReadRoute(defaultTemplate)
                ReactiveReadRoute(
                    template = template,
                    fallbackTemplate = defaultTemplate,
                    fallbackToDefault = shouldFallbackToDefault(rule, instanceName),
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
        if (!rule.routingEnabled) return ReactiveWriteRoute(defaultTemplate)
        return when (rule.routingType) {
            MongoMultiInstanceProperties.RoutingType.NONE -> {
                val instanceName = rule.instances.keys.firstOrNull()
                    ?: return ReactiveWriteRoute(defaultTemplate)
                val instanceTmpl = primaryTemplates[ruleName]?.get(instanceName)
                    ?: return ReactiveWriteRoute(defaultTemplate)
                if (rule.dualWrite) {
                    ReactiveWriteRoute(
                        primary = defaultTemplate,
                        secondary = instanceTmpl,
                        secondaryTarget = RouteTarget(ruleName, instanceName, useDefault = false),
                        ruleName = ruleName,
                    )
                } else {
                    ReactiveWriteRoute(
                        primary = instanceTmpl,
                        fallbackTemplate = defaultTemplate,
                        fallbackToDefault = shouldFallbackToDefault(rule, instanceName),
                        ruleName = ruleName,
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
                    )
                }
                val instanceTmpl = primaryTemplates[ruleName]?.get(instanceName)
                    ?: return ReactiveWriteRoute(defaultTemplate)
                if (hitProjectRouting && isProjectInDualWrite(ruleName, key!!)) {
                    ReactiveWriteRoute(
                        primary = instanceTmpl,
                        secondary = defaultTemplate,
                        secondaryTarget = RouteTarget(ruleName, useDefault = true),
                        routingKey = key,
                        ruleName = ruleName,
                        syncSecondaryWrite = true,
                    )
                } else if (!hitProjectRouting && rule.dualWrite) {
                    ReactiveWriteRoute(
                        primary = instanceTmpl,
                        secondary = defaultTemplate,
                        secondaryTarget = RouteTarget(ruleName, useDefault = true),
                        routingKey = key,
                        ruleName = ruleName,
                    )
                } else {
                    ReactiveWriteRoute(
                        primary = instanceTmpl,
                        fallbackTemplate = defaultTemplate,
                        fallbackToDefault = shouldFallbackToDefault(rule, instanceName),
                        routingKey = key,
                        ruleName = ruleName,
                    )
                }
            }
        }
    }

    fun resolveRuleName(collectionName: String): String? =
        prefixIndex.firstOrNull { collectionName.startsWith(it.first) }?.second

    fun isProjectRoutedOut(ruleName: String, projectId: String): Boolean {
        val rule = properties.rules[ruleName] ?: return false
        if (!rule.routingEnabled) return false
        if (projectId !in rule.projectRouting) return false
        if (isProjectInDualWrite(ruleName, projectId)) return false
        return !rule.dualWrite
    }

    /** G-02：迁出项目禁止写 Default 僵尸副本 */
    fun assertWriteNotZombie(
        route: ReactiveWriteRoute,
        collectionName: String,
        defaultTemplate: ReactiveMongoTemplate,
    ) {
        if (route.primary === defaultTemplate && route.routingKey != null && route.ruleName != null &&
            isProjectRoutedOut(route.ruleName, route.routingKey)
        ) {
            val msg = "WRITE_PROTECTION: Attempted reactive write on Default zombie replica. " +
                "projectId=${route.routingKey}, collection=$collectionName, rule=${route.ruleName}"
            throw IllegalStateException(msg)
        }
    }

    private fun isProjectInDualWrite(ruleName: String, projectId: String): Boolean {
        val rule = properties.rules[ruleName] ?: return false
        if (!rule.routingEnabled || !rule.dualWrite) return false
        if (projectId !in rule.projectRouting) return false
        return true
    }

    private fun shouldWriteToHeavy(
        ruleName: String,
        rule: MongoMultiInstanceProperties.RoutingRule,
        projectId: String?,
    ): Boolean {
        if (projectId == null || projectId !in rule.projectRouting) return true
        return true
    }

    private fun shouldFallbackToDefault(
        rule: MongoMultiInstanceProperties.RoutingRule,
        instanceName: String,
    ): Boolean = !rule.dualWrite && (rule.instances[instanceName]?.fallbackBeforeCleanup == true)

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
        return null
    }

    private fun templatesFor(ruleName: String, primary: Boolean) =
        if (primary) primaryTemplates[ruleName] ?: emptyMap()
        else secondaryTemplates[ruleName] ?: emptyMap()

    private fun buildPrimaryTemplates(): Map<String, Map<String, ReactiveMongoTemplate>> =
        properties.rules.entries
            .filter { it.value.routingEnabled }
            .associate { (ruleName, rule) ->
                ruleName to rule.instances.mapValues { (_, cfg) ->
                    reactiveTemplate(cfg.uri)
                }
            }

    private fun buildSecondaryTemplates(): Map<String, Map<String, ReactiveMongoTemplate>> =
        properties.rules.entries
            .filter { it.value.routingEnabled }
            .associate { (ruleName, rule) ->
                ruleName to rule.instances.mapValues { (instanceName, cfg) ->
                    if (cfg.secondaryUri.isNotBlank()) {
                        reactiveTemplate(cfg.secondaryUri)
                    } else {
                        primaryTemplates[ruleName]?.get(instanceName)
                            ?: reactiveTemplate(cfg.uri)
                    }
                }
            }

    private fun reactiveTemplate(uri: String): ReactiveMongoTemplate =
        ReactiveMongoTemplate(SimpleReactiveMongoDatabaseFactory(ConnectionString(uri)))
}