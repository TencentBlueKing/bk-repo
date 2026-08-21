package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.InstanceBinding
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.ReadRoute
import com.tencent.bkrepo.common.mongo.api.routing.RoutedTemplate
import com.tencent.bkrepo.common.mongo.api.routing.WriteRoute
import org.springframework.data.mongodb.core.MongoTemplate

/**
 * M1 短路实现：无 rules 或全部 routing-enabled=false 时恒返回 Default，行为与现网等价。
 */
class StandardRoutingRegistry(
    private val defaultTemplate: MongoTemplate,
    private val properties: MongoMultiInstanceProperties,
) : MongoRoutingRegistry {

    override fun resolve(ruleName: String, projectId: String): RoutedTemplate =
        defaultRouted()

    override fun resolveByCollection(ruleName: String, collectionName: String): RoutedTemplate? = null

    override fun resolveOffload(ruleName: String): RoutedTemplate = defaultRouted()

    override fun listInstances(ruleName: String): List<InstanceBinding> = emptyList()

    override fun getRoutedProjectIds(ruleName: String): Set<String> = emptySet()

    override fun expandBusinessGroupProjects(ruleName: String, businessId: String): Set<String> =
        emptySet()

    override fun isProjectRoutedOut(ruleName: String, projectId: String): Boolean = false

    override fun isProjectInDualWrite(ruleName: String, projectId: String): Boolean = false

    override fun validateOnStartup() {}

    override fun routeWrite(collectionName: String, context: Any?): MongoTemplate? = null

    override fun routeRead(collectionName: String, context: Any?): MongoTemplate? = null

    override fun resolveReadRoute(
        collectionName: String,
        context: Any?,
        defaultTemplate: MongoTemplate,
    ): ReadRoute = ReadRoute(this.defaultTemplate)

    override fun resolveWriteRoute(
        collectionName: String,
        context: Any?,
        defaultTemplate: MongoTemplate,
    ): WriteRoute = WriteRoute(this.defaultTemplate)

    override fun resolveRuleName(collectionName: String): String? = null

    override fun projectsByInstance(ruleName: String): Map<String, Set<String>> = emptyMap()

    override fun shardRoutedCollections(ruleName: String): Set<String> = emptySet()

    override fun shardsByInstance(ruleName: String): Map<String, Set<String>> = emptyMap()

    override fun allKnownProjectIds(ruleName: String): Set<String> = emptySet()

    override fun primaryTemplateByInstance(ruleName: String, instanceName: String): MongoTemplate? =
        null

    override fun allPrimaryTemplates(ruleName: String): Map<String, MongoTemplate> = emptyMap()

    override fun allConfiguredProjectsByInstance(ruleName: String): Map<String, Set<String>> =
        emptyMap()

    override fun isRoutingEnabled(ruleName: String): Boolean = false

    override fun isDualWrite(ruleName: String): Boolean = false

    override fun isNoneRoutingMode(ruleName: String): Boolean = false

    override fun listOffloadRuleNames(): List<String> = emptyList()

    override fun hasRoutedProjects(ruleName: String): Boolean = false

    override fun historicalSyncStrategy(ruleName: String): String = "NONE"

    override fun getConfigVersion(): Long = properties.configVersion

    override fun getMinConfigVersion(): Long = properties.minConfigVersion

    override fun isConfigUpToDate(): Boolean = properties.configVersion >= properties.minConfigVersion

    private fun defaultRouted(): RoutedTemplate =
        RoutedTemplate(DEFAULT_INSTANCE, defaultTemplate)

    companion object {
        private const val DEFAULT_INSTANCE = "default"
    }
}
