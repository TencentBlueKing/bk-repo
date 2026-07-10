package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.BindingType
import com.tencent.bkrepo.common.mongo.api.routing.InstanceBinding
import com.tencent.bkrepo.common.mongo.api.routing.InstanceTier
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.ReadRoute
import com.tencent.bkrepo.common.mongo.api.routing.RuleRoutingState
import com.tencent.bkrepo.common.mongo.api.routing.RouteTarget
import com.tencent.bkrepo.common.mongo.api.routing.RoutedTemplate
import com.tencent.bkrepo.common.mongo.api.routing.WriteRoute
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import io.micrometer.core.instrument.binder.mongodb.MongoMetricsConnectionPoolListener
import org.springframework.beans.factory.DisposableBean
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.convert.MongoConverter
import org.springframework.data.mongodb.core.query.Query

/** M1 多实例路由注册表实现。 */
class DefaultMongoRoutingRegistry(
    private val properties: MongoMultiInstanceProperties,
    private val mongoConverter: MongoConverter? = null,
    private val poolMetricsListener: MongoMetricsConnectionPoolListener? = null,
) : MongoRoutingRegistry, DisposableBean {

    private val mongoClients = mutableListOf<MongoClient>()

    override fun historicalSyncStrategy(ruleName: String): String =
        properties.rules[ruleName]?.migration?.historicalSyncStrategy?.uppercase()
            ?: defaultHistoricalSyncStrategy(ruleName)

    private fun defaultHistoricalSyncStrategy(ruleName: String): String {
        val rule = properties.rules[ruleName]
        if (rule != null) {
            return when (rule.routingType) {
                MongoMultiInstanceProperties.RoutingType.NONE -> "NONE"
                else -> "JOB_ONLY"
            }
        }
        return if (ruleName.contains("oplog")) "NONE" else "JOB_ONLY"
    }

    private val primaryTemplates: Map<String, Map<String, MongoTemplate>>

    /** 按前缀长度降序排列，优先匹配最长前缀 */
    private val prefixIndex: List<Pair<String, String>>

    /** entity class → routingKeyField → cached Field */
    internal val fieldCache = java.util.concurrent.ConcurrentHashMap<String, java.lang.reflect.Field>()
    internal val missingFieldCache = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    init {
        primaryTemplates = buildPrimaryTemplates()
        prefixIndex = properties.rules.entries
            .filter { it.value.collectionPrefix.isNotBlank() }
            .map { it.value.collectionPrefix to it.key }
            .sortedByDescending { it.first.length }
    }

    /**
     * 写操作路由：返回目标 Primary MongoTemplate，未命中返回 null（调用方使用 Default）。
     */
    override fun routeWrite(collectionName: String, context: Any?): MongoTemplate? =
        resolveTemplate(collectionName, context, primary = true)

    override fun routeRead(collectionName: String, context: Any?): MongoTemplate? =
        resolveTemplate(collectionName, context, primary = false)

    override fun resolve(ruleName: String, projectId: String): RoutedTemplate {
        val rule = properties.rules[ruleName] ?: error("Unknown routing rule: $ruleName")
        val instanceName = rule.projectRouting[projectId]
            ?: error("Project '$projectId' not in project-routing for rule '$ruleName'")
        return toRoutedTemplate(ruleName, instanceName)
    }

    override fun resolveByCollection(ruleName: String, collectionName: String): RoutedTemplate? {
        val rule = properties.rules[ruleName] ?: return null
        if (effectiveRoutingState(ruleName, rule) == RuleRoutingState.OFF) return null
        val instanceName = rule.shardRouting[collectionName] ?: return null
        return toRoutedTemplate(ruleName, instanceName)
    }

    override fun resolveOffload(ruleName: String): RoutedTemplate {
        val rule = properties.rules[ruleName]
            ?: error("Unknown routing rule: $ruleName")
        require(rule.routingType == MongoMultiInstanceProperties.RoutingType.NONE) {
            "Rule '$ruleName' must have routing-type=NONE, got ${rule.routingType}"
        }
        val instanceName = rule.instances.keys.firstOrNull()
            ?: error("Offload rule '$ruleName' has no instances configured")
        return toRoutedTemplate(ruleName, instanceName)
    }

    override fun listInstances(ruleName: String): List<InstanceBinding> {
        val rule = properties.rules[ruleName] ?: return emptyList()
        val projectsByInstance = rule.projectRouting.entries.groupBy({ it.value }, { it.key })
        val tier = if (rule.routingType == MongoMultiInstanceProperties.RoutingType.NONE) {
            InstanceTier.OFFLOAD
        } else {
            InstanceTier.HEAVY
        }
        val businessProjectIds = rule.businessRouting.keys.flatMap { businessId ->
            expandBusinessGroupProjects(ruleName, businessId)
        }.toSet()
        val dedicated = rule.instances.keys.map { instanceId ->
            InstanceBinding(
                instanceId = instanceId,
                tier = tier,
                bindingType = BindingType.DEDICATED,
                projectIds = projectsByInstance[instanceId].orEmpty()
                    .filter { it !in businessProjectIds }
                    .toSet(),
            )
        }
        val business = rule.businessRouting.map { (businessId, instanceId) ->
            InstanceBinding(
                instanceId = instanceId,
                tier = tier,
                bindingType = BindingType.BUSINESS_GROUP,
                businessId = businessId,
                projectIds = expandBusinessGroupProjects(ruleName, businessId),
            )
        }
        return dedicated + business
    }

    override fun getRoutedProjectIds(ruleName: String): Set<String> {
        val rule = properties.rules[ruleName] ?: return emptySet()
        if (effectiveRoutingState(ruleName, rule) == RuleRoutingState.OFF) return emptySet()
        return rule.projectRouting.keys.filterTo(mutableSetOf()) {
            isProjectRoutedOut(ruleName, it)
        }
    }

    override fun expandBusinessGroupProjects(ruleName: String, businessId: String): Set<String> {
        val rule = properties.rules[ruleName] ?: return emptySet()
        val instanceName = rule.businessRouting[businessId] ?: return emptySet()
        return rule.projectRouting.filter { it.value == instanceName }.keys
    }

    override fun resolveReadRoute(
        collectionName: String,
        context: Any?,
        defaultTemplate: MongoTemplate,
    ): ReadRoute {
        val entry = prefixIndex.firstOrNull { collectionName.startsWith(it.first) }
            ?: return ReadRoute(defaultTemplate)
        val (_, ruleName) = entry
        val rule = properties.rules[ruleName] ?: return ReadRoute(defaultTemplate)
        if (effectiveRoutingState(ruleName, rule) == RuleRoutingState.OFF) return ReadRoute(defaultTemplate)
        return when (rule.routingType) {
            MongoMultiInstanceProperties.RoutingType.NONE -> {
                if (effectiveRoutingState(ruleName, rule) == RuleRoutingState.DUAL_WRITE)
                    return ReadRoute(defaultTemplate)
                val instanceName = rule.instances.keys.firstOrNull() ?: return ReadRoute(defaultTemplate)
                val template = primaryTemplates[ruleName]?.get(instanceName) ?: return ReadRoute(defaultTemplate)
                ReadRoute(
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
                    ?: return ReadRoute(defaultTemplate)
                if (hitProjectRouting) {
                    if (shouldReadFromDefaultDuringMigration(ruleName, rule, key)) {
                        return ReadRoute(defaultTemplate)
                    }
                    if (!shouldWriteToHeavy(ruleName, rule, key)) {
                        return ReadRoute(defaultTemplate)
                    }
                }
                val template = primaryTemplates[ruleName]?.get(instanceName) ?: return ReadRoute(defaultTemplate)
                ReadRoute(
                    template = template,
                    fallbackTemplate = defaultTemplate,
                    fallbackToDefault = shouldFallbackToDefault(ruleName, rule, instanceName, key),
                )
            }
        }
    }

    /**
     * 解析写操作路由，感知双写方向。
     * - NONE + dualWrite=true  → primary=Default，secondary=实例（Default 先写，同步）
     * - NONE + dualWrite=false → primary=实例，secondary=null
     * - PROJECT + dualWrite=true  → primary=实例，secondary=Default（实例先写）
     * - PROJECT + dualWrite=false → primary=实例，secondary=null
     *
     * @param defaultTemplate 调用方自身的 Default MongoTemplate（回退模板）
     */
    override fun resolveWriteRoute(
        collectionName: String,
        context: Any?,
        defaultTemplate: MongoTemplate,
    ): WriteRoute {
        val entry = prefixIndex.firstOrNull { collectionName.startsWith(it.first) }
            ?: return WriteRoute(defaultTemplate)
        val (_, ruleName) = entry
        val rule = properties.rules[ruleName] ?: return WriteRoute(defaultTemplate)
        if (effectiveRoutingState(ruleName, rule) == RuleRoutingState.OFF) return WriteRoute(defaultTemplate)
        return when (rule.routingType) {
            MongoMultiInstanceProperties.RoutingType.NONE -> {
                val instanceName = rule.instances.keys.firstOrNull()
                    ?: return WriteRoute(defaultTemplate)
                val instanceTmpl = primaryTemplates[ruleName]?.get(instanceName)
                    ?: return WriteRoute(defaultTemplate)
                if (effectiveRoutingState(ruleName, rule) == RuleRoutingState.DUAL_WRITE) {
                    // 模式一双写期与模式二统一 Default-first（§1.3.1）
                    WriteRoute(
                        primary = defaultTemplate,
                        secondary = instanceTmpl,
                        secondaryTarget = RouteTarget(ruleName, instanceName = instanceName),
                        ruleName = ruleName,
                        isDefaultInstance = true,
                        syncSecondaryWrite = true,
                    )
                } else {
                    WriteRoute(
                        primary = instanceTmpl,
                        fallbackTemplate = defaultTemplate,
                        fallbackToDefault = shouldFallbackToDefault(ruleName, rule, instanceName),
                        routingKey = null,
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
                    ?: return WriteRoute(defaultTemplate)
                if (hitProjectRouting && !shouldWriteToHeavy(ruleName, rule, key)) {
                    return WriteRoute(
                        primary = defaultTemplate,
                        routingKey = key,
                        ruleName = ruleName,
                        isDefaultInstance = true,
                    )
                }
                val instanceTmpl = primaryTemplates[ruleName]?.get(instanceName)
                    ?: return WriteRoute(defaultTemplate)
                val dualWrite = if (hitProjectRouting) {
                    isProjectInDualWrite(ruleName, key!!)
                } else {
                    effectiveRoutingState(ruleName, rule) == RuleRoutingState.DUAL_WRITE
                }
                if (dualWrite) {
                    // 模式二双写：Default 为主路径（先写），Heavy 为副路径（§3.6.3）。
                    // Default 持有全量历史，唯一键冲突在主路径同步 fail-fast 暴露；
                    // 若空 Heavy 先写会成功、Default 后写冲突，导致两侧不可收敛地分叉。
                    WriteRoute(
                        primary = defaultTemplate,
                        secondary = instanceTmpl,
                        secondaryTarget = RouteTarget(ruleName, instanceName = instanceName),
                        routingKey = key,
                        ruleName = ruleName,
                        isDefaultInstance = true,
                        syncSecondaryWrite = true,
                    )
                } else {
                    WriteRoute(
                        primary = instanceTmpl,
                        isDefaultInstance = false,
                        fallbackTemplate = defaultTemplate,
                        fallbackToDefault = shouldFallbackToDefault(ruleName, rule, instanceName, key),
                        routingKey = key,
                        ruleName = ruleName,
                    )
                }
            }
        }
    }

    // ─── 路由状态查询 ───────────────────────────────────────────────

    override fun isRoutingEnabled(ruleName: String): Boolean =
        properties.rules[ruleName]?.routingState != RuleRoutingState.OFF

    override fun isDualWrite(ruleName: String): Boolean =
        properties.rules[ruleName]?.let { effectiveRoutingState(ruleName, it) == RuleRoutingState.DUAL_WRITE } ?: false

    /**
     * 判断指定项目在指定规则下当前是否处于双写阶段（§3.5.1）。
     * 纯 Consul：`routing-state=DUAL_WRITE` 且 `projectId ∈ project-routing`；不读 DB phase。
     */
    override fun isProjectInDualWrite(ruleName: String, projectId: String): Boolean {
        val rule = properties.rules[ruleName] ?: return false
        if (effectiveRoutingState(ruleName, rule) != RuleRoutingState.DUAL_WRITE) return false
        return projectId in rule.projectRouting
    }

    /**
     * 判断指定规则是否为 NONE 整体迁移模式（§1.4.4a）。
     * NONE 模式下 update/delete 静默 matchedCount=0 是高危信号，需 fail-fast。
     */
    override fun isNoneRoutingMode(ruleName: String): Boolean {
        val rule = properties.rules[ruleName] ?: return false
        return rule.routingType == MongoMultiInstanceProperties.RoutingType.NONE
    }

    override fun listOffloadRuleNames(): List<String> =
        properties.rules.filter { it.value.routingType == MongoMultiInstanceProperties.RoutingType.NONE }.keys
            .toList()

    /**
     * 根据集合名查找匹配的规则名，未匹配返回 null。
     * 替代之前通过反射访问 [prefixIndex] 的实现。
     */
    override fun resolveRuleName(collectionName: String): String? =
        prefixIndex.firstOrNull { collectionName.startsWith(it.first) }?.second

    override fun projectsByInstance(ruleName: String): Map<String, Set<String>> {
        val rule = properties.rules[ruleName] ?: return emptyMap()
        if (effectiveRoutingState(ruleName, rule) == RuleRoutingState.OFF) return emptyMap()
        return rule.projectRouting.entries
            .filter { isProjectRoutedOut(ruleName, it.key) }
            .groupBy({ it.value }, { it.key })
            .mapValues { it.value.toSet() }
    }

    override fun shardRoutedCollections(ruleName: String): Set<String> {
        val rule = properties.rules[ruleName] ?: return emptySet()
        if (effectiveRoutingState(ruleName, rule) == RuleRoutingState.OFF) return emptySet()
        return rule.shardRouting.keys
    }

    override fun shardsByInstance(ruleName: String): Map<String, Set<String>> {
        val rule = properties.rules[ruleName] ?: return emptyMap()
        if (effectiveRoutingState(ruleName, rule) == RuleRoutingState.OFF) return emptyMap()
        return rule.shardRouting.entries
            .groupBy({ it.value }, { it.key })
            .mapValues { it.value.toSet() }
    }

    override fun primaryTemplateByInstance(ruleName: String, instanceName: String): MongoTemplate? =
        primaryTemplates[ruleName]?.get(instanceName)

    /** 返回 instanceName → primary MongoTemplate，供同步 Job 写入使用 */
    override fun allPrimaryTemplates(ruleName: String): Map<String, MongoTemplate> =
        primaryTemplates[ruleName] ?: emptyMap()

    /**
     * 返回规则下所有已配置的 project → instance 映射，
     * 不依赖 routingEnabled，供数据预同步阶段使用。
     */
    override fun allConfiguredProjectsByInstance(ruleName: String): Map<String, Set<String>> =
        properties.rules[ruleName]?.projectRouting.orEmpty()
            .entries.groupBy({ it.value }, { it.key })
            .mapValues { it.value.toSet() }

    // ─── 僵尸副本写保护 (§25.2.2 E-01) ──────────────────────────────

    /**
     * 返回规则下所有已配置的项目 ID 集合（含已迁出和未迁出），
     * 用于 §3.7.2 散发查询白名单切换（NOT IN → IN remaining）。
     */
    override fun allKnownProjectIds(ruleName: String): Set<String> =
        properties.rules[ruleName]?.projectRouting.orEmpty().keys

    /**
     * 判断指定项目在给定规则下是否已迁出（ROUTED）。
     * 纯 Consul：`routing-state=ROUTED` 且 `projectId ∈ project-routing`。
     */
    override fun isProjectRoutedOut(ruleName: String, projectId: String): Boolean {
        val rule = properties.rules[ruleName] ?: return false
        if (effectiveRoutingState(ruleName, rule) == RuleRoutingState.OFF) return false
        if (projectId !in rule.projectRouting) return false
        if (isProjectInDualWrite(ruleName, projectId)) return false
        return effectiveRoutingState(ruleName, rule) == RuleRoutingState.ROUTED
    }

    /**
     * 判断指定规则是否存在已迁出的项目。
     * 用于判断是否需要启用散射查询。
     */
    override fun hasRoutedProjects(ruleName: String): Boolean =
        getRoutedProjectIds(ruleName).isNotEmpty()

    override fun getConfigVersion(): Long = properties.configVersion

    override fun getMinConfigVersion(): Long = properties.minConfigVersion

    override fun isConfigUpToDate(): Boolean = properties.configVersion >= properties.minConfigVersion

    override fun validateOnStartup() {
        val heavyCount = properties.rules.values
            .filter {
                it.routingState != RuleRoutingState.OFF &&
                    it.routingType == MongoMultiInstanceProperties.RoutingType.PROJECT
            }
            .sumOf { it.instances.size }
        check(heavyCount <= MAX_HEAVY_INSTANCES) {
            "Heavy instance count $heavyCount exceeds limit $MAX_HEAVY_INSTANCES (§4.1)"
        }
        validateNodeBlockNodeBindingConsistency()
        properties.rules.forEach { (ruleName, rule) ->
            val knownInstances = rule.instances.keys
            rule.projectRouting.forEach { (projectId, instanceName) ->
                check(instanceName in knownInstances) {
                    "Rule '$ruleName': project '$projectId' → instance '$instanceName' " +
                        "not found in instances $knownInstances"
                }
                if (rule.routingType == MongoMultiInstanceProperties.RoutingType.PROJECT &&
                    rule.collectionPrefix.isNotBlank()
                ) {
                    val shardIdx = HashShardingUtils.shardingSequenceFor(
                        projectId,
                        rule.shardingCount,
                    )
                    val collectionName = "${rule.collectionPrefix}$shardIdx"
                    check(collectionName !in rule.shardRouting) {
                        "Rule '$ruleName': project '$projectId' hashes to collection '$collectionName' " +
                            "which is also configured in shard-routing (§13.3 mutual exclusion). " +
                            "Remove shard-routing entry or project-routing for partial migration."
                    }
                }
            }
            rule.shardRouting.forEach { (collectionName, instanceName) ->
                check(instanceName in knownInstances) {
                    "Rule '$ruleName': shard '$collectionName' → instance '$instanceName' " +
                        "not found in instances $knownInstances"
                }
            }
        }
    }

    // ─── private ───────────────────────────────────────────────────────────────

    /** G-39：node 与 block-node 的 project-routing 须完全一致。 */
    private fun validateNodeBlockNodeBindingConsistency() {
        val nodeRule = properties.rules[NODE_RULE] ?: return
        val blockNodeRule = properties.rules[BLOCK_NODE_RULE] ?: return
        if (nodeRule.routingState == RuleRoutingState.OFF &&
            blockNodeRule.routingState == RuleRoutingState.OFF
        ) {
            return
        }
        nodeRule.projectRouting.forEach { (projectId, nodeInstance) ->
            val blockInstance = blockNodeRule.projectRouting[projectId]
            check(blockInstance == nodeInstance) {
                "G-39: project '$projectId' maps to '$nodeInstance' in rule '$NODE_RULE' " +
                    "but ${blockInstance?.let { "'$it'" } ?: "is missing"} in rule '$BLOCK_NODE_RULE'"
            }
        }
        blockNodeRule.projectRouting.forEach { (projectId, blockInstance) ->
            val nodeInstance = nodeRule.projectRouting[projectId]
            check(nodeInstance == blockInstance) {
                "G-39: project '$projectId' maps to '$blockInstance' in rule '$BLOCK_NODE_RULE' " +
                    "but ${nodeInstance?.let { "'$it'" } ?: "is missing"} in rule '$NODE_RULE'"
            }
        }
    }

    private fun effectiveRoutingState(
        ruleName: String,
        rule: MongoMultiInstanceProperties.RoutingRule,
    ): RuleRoutingState = RoutingEffectiveState.effectiveRoutingState(rule, ruleName = ruleName)

    private fun toRoutedTemplate(ruleName: String, instanceName: String): RoutedTemplate {
        val primary = primaryTemplates[ruleName]?.get(instanceName)
            ?: error("Primary template not found: rule=$ruleName instance=$instanceName")
        return RoutedTemplate(instanceId = instanceName, primary = primary)
    }

    private fun resolveTemplate(collectionName: String, context: Any?, primary: Boolean): MongoTemplate? {
        val (_, ruleName) = prefixIndex
            .firstOrNull { collectionName.startsWith(it.first) } ?: return null
        val rule = properties.rules[ruleName] ?: return null
        if (effectiveRoutingState(ruleName, rule) == RuleRoutingState.OFF) return null

        return when (rule.routingType) {
            MongoMultiInstanceProperties.RoutingType.NONE -> {
                // 双写期 Default 是主路径，读写均走 Default（返回 null 由调用方使用其 Default）
                if (effectiveRoutingState(ruleName, rule) == RuleRoutingState.DUAL_WRITE) return null
                val instanceName = rule.instances.keys.firstOrNull() ?: return null
                primaryTemplates[ruleName]?.get(instanceName)
            }
            MongoMultiInstanceProperties.RoutingType.PROJECT,
            MongoMultiInstanceProperties.RoutingType.COLLECTION -> {
                val key = extractKey(context, rule.routingKeyField)
                    ?: MongoRoutingContext.get(ruleName)
                val instanceName = rule.projectRouting[key]
                    ?: rule.shardRouting[collectionName]
                    ?: return null
                primaryTemplates[ruleName]?.get(instanceName)
            }
        }
    }

    private fun shouldFallbackToDefault(
        ruleName: String,
        rule: MongoMultiInstanceProperties.RoutingRule,
        instanceName: String,
        projectId: String? = null,
    ): Boolean {
        // 模式一（NONE 整体迁移）ROUTED 后禁止 fallback Default（文档 §2.11）
        if (rule.routingType == MongoMultiInstanceProperties.RoutingType.NONE) return false
        val routingKey = projectId ?: MongoRoutingContext.get(ruleName)
        if (routingKey != null && isProjectRoutedOut(ruleName, routingKey)) {
            return false
        }
        return effectiveRoutingState(ruleName, rule) != RuleRoutingState.DUAL_WRITE &&
            (rule.instances[instanceName]?.fallbackBeforeCleanup == true)
    }

    private fun shouldWriteToHeavy(
        ruleName: String,
        rule: MongoMultiInstanceProperties.RoutingRule,
        projectId: String?,
    ): Boolean {
        if (projectId == null || projectId !in rule.projectRouting) return false
        return isProjectInDualWrite(ruleName, projectId) ||
            isProjectRoutedOut(ruleName, projectId)
    }

    private fun shouldReadFromDefaultDuringMigration(
        ruleName: String,
        rule: MongoMultiInstanceProperties.RoutingRule,
        projectId: String,
    ): Boolean = isProjectInDualWrite(ruleName, projectId)

    /**
     * 从 context 中提取路由键字段值。
     * 支持：Query（queryObject提取）、String、实体反射（含嵌套路径如 "metadata.projectId"）
     */
    private fun extractKey(context: Any?, fieldPath: String): String? = when (context) {
        is Query -> extractFromQueryObject(context.queryObject, fieldPath)
        is String -> context
        null -> null
        else -> {
            val cacheKey = "${context.javaClass.name}:$fieldPath"
            when {
                missingFieldCache.contains(cacheKey) -> null
                else -> {
                    val cachedField = fieldCache[cacheKey] ?: runCatching {
                        context.javaClass.getDeclaredField(fieldPath).also { it.isAccessible = true }
                    }.getOrNull()
                    when (cachedField) {
                        null -> {
                            missingFieldCache.add(cacheKey)
                            null
                        }
                        else -> {
                            fieldCache[cacheKey] = cachedField
                            runCatching { cachedField.get(context) as? String }.getOrNull()
                        }
                    }
                }
            }
        }
    }

    /**
     * 从 Query 的 queryObject 中递归提取字段值，支持嵌套路径。
     */
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

        // $and 组合：遍历子条件
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

    private fun buildPrimaryTemplates(): Map<String, Map<String, MongoTemplate>> =
        properties.rules.entries
            .associate { (ruleName, rule) ->
                ruleName to rule.instances.mapValues { (_, cfg) ->
                    val factory = createFactory(cfg.uri, cfg)
                    mongoConverter?.let { MongoTemplate(factory, it) } ?: MongoTemplate(factory)
                }
            }

    /**
     * 创建带连接池设置的 [SimpleMongoClientDatabaseFactory]，覆盖 URI 中的 maxPoolSize/minPoolSize。
     * 优先使用 [MongoMultiInstanceProperties.RoutingRule.InstanceConfig] 中的实例级配置（§7 连接池管理）。
     */
    private fun createFactory(
        uri: String,
        cfg: MongoMultiInstanceProperties.RoutingRule.InstanceConfig,
    ): SimpleMongoClientDatabaseFactory {
        val connectionString = com.mongodb.ConnectionString(uri)
        val settingsBuilder = com.mongodb.MongoClientSettings.builder()
            .applyConnectionString(connectionString)
            .applyToConnectionPoolSettings { builder ->
                builder.maxSize(cfg.maxPoolSize).minSize(cfg.minPoolSize)
                poolMetricsListener?.let { builder.addConnectionPoolListener(it) }
            }
        val settings = settingsBuilder.build()
        val client = MongoClients.create(settings)
        mongoClients.add(client)
        val database = connectionString.database
            ?: error("MongoDB URI must include database: $uri")
        return SimpleMongoClientDatabaseFactory(client, database)
    }

    override fun destroy() {
        MongoClientShutdownHandler.closeAll(mongoClients)
        mongoClients.clear()
    }

    companion object {
        private const val MAX_HEAVY_INSTANCES = 10
        private const val NODE_RULE = "node"
        private const val BLOCK_NODE_RULE = "block-node"
    }
}