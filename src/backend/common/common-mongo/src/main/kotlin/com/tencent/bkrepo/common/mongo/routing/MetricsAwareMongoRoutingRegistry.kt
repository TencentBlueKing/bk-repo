package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.ReadRoute
import com.tencent.bkrepo.common.mongo.api.routing.WriteRoute
import org.springframework.data.mongodb.core.MongoTemplate

/** M8：路由解析指标装饰器 */
class MetricsAwareMongoRoutingRegistry(
    private val delegate: MongoRoutingRegistry,
    private val metrics: MongoRoutingMetrics,
) : MongoRoutingRegistry by delegate {

    override fun resolveReadRoute(
        collectionName: String,
        context: Any?,
        defaultTemplate: MongoTemplate,
    ): ReadRoute {
        val route = delegate.resolveReadRoute(collectionName, context, defaultTemplate)
        recordRoute(collectionName, defaultTemplate, route.template, route.fallbackToDefault)
        return route
    }

    override fun resolveWriteRoute(
        collectionName: String,
        context: Any?,
        defaultTemplate: MongoTemplate,
    ): WriteRoute {
        val route = delegate.resolveWriteRoute(collectionName, context, defaultTemplate)
        recordRoute(collectionName, defaultTemplate, route.primary, route.fallbackToDefault)
        return route
    }

    private fun recordRoute(
        collectionName: String,
        defaultTemplate: MongoTemplate,
        targetTemplate: MongoTemplate,
        fallbackToDefault: Boolean,
    ) {
        val ruleName = delegate.resolveRuleName(collectionName) ?: return
        val hit = targetTemplate !== defaultTemplate
        metrics.recordRoutingQuery(ruleName, hit)
        if (fallbackToDefault) {
            metrics.recordFallback(ruleName)
        }
    }
}
