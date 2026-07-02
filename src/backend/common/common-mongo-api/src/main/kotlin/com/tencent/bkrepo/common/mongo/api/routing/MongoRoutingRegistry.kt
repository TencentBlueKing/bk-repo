package com.tencent.bkrepo.common.mongo.api.routing

import org.springframework.data.mongodb.core.MongoTemplate

/**
 * M0 路由契约：业务模块仅依赖本接口，禁止引用实现类。
 */
interface MongoRoutingRegistry {

    // ─── M0 核心 API（§3.1）────────────────────────────────────────

    fun resolve(ruleName: String, projectId: String): RoutedTemplate

    fun resolveByCollection(ruleName: String, collectionName: String): RoutedTemplate?

    fun resolveOplog(): RoutedTemplate

    fun listInstances(ruleName: String): List<InstanceBinding>

    fun getRoutedProjectIds(ruleName: String): Set<String>

    /** Tier-Biz：展开 business-routing 组内全部 projectId */
    fun expandBusinessGroupProjects(ruleName: String, businessId: String): Set<String>

    fun isProjectRoutedOut(ruleName: String, projectId: String): Boolean

    fun isProjectInDualWrite(ruleName: String, projectId: String): Boolean

    fun validateOnStartup()

    /** 写路由快捷方法：未命中返回 null，调用方使用 Default */
    fun routeWrite(collectionName: String, context: Any?): MongoTemplate?

    /** 读路由快捷方法：未命中返回 null，调用方使用 Default */
    fun routeRead(collectionName: String, context: Any?): MongoTemplate?

    // ─── M1 集合级路由（DAO / Job 写路径）────────────────────────────

    fun resolveReadRoute(
        collectionName: String,
        context: Any?,
        defaultTemplate: MongoTemplate,
    ): ReadRoute

    fun resolveWriteRoute(
        collectionName: String,
        context: Any?,
        defaultTemplate: MongoTemplate,
    ): WriteRoute

    fun resolveRuleName(collectionName: String): String?

    // ─── M5 散发查询 / Job 只读 API ─────────────────────────────────

    fun routedProjectIds(ruleName: String): Set<String> = getRoutedProjectIds(ruleName)

    fun projectsByInstance(ruleName: String): Map<String, Set<String>>

    fun shardRoutedCollections(ruleName: String): Set<String>

    fun shardsByInstance(ruleName: String): Map<String, Set<String>>

    fun allKnownProjectIds(ruleName: String): Set<String>

    fun primaryTemplateByInstance(ruleName: String, instanceName: String): MongoTemplate?

    fun secondaryTemplateByInstance(ruleName: String, instanceName: String): MongoTemplate?

    fun allPrimaryTemplates(ruleName: String): Map<String, MongoTemplate>

    fun allSecondaryTemplates(ruleName: String): Map<String, MongoTemplate>

    fun allConfiguredProjectsByInstance(ruleName: String): Map<String, Set<String>>

    // ─── M6 迁移 / 门控 ────────────────────────────────────────────

    fun isRoutingEnabled(ruleName: String): Boolean

    fun isDualWrite(ruleName: String): Boolean

    fun isNoneRoutingMode(ruleName: String): Boolean

    fun hasRoutedProjects(ruleName: String): Boolean

    fun migrationMode(ruleName: String): String

    /** §1.6.1 历史同步策略：NONE / DUMP / DUMP_THEN_JOB / JOB_ONLY */
    fun historicalSyncStrategy(ruleName: String): String

    fun getConfigVersion(): Long

    fun getMinConfigVersion(): Long

    fun isConfigUpToDate(): Boolean
}
