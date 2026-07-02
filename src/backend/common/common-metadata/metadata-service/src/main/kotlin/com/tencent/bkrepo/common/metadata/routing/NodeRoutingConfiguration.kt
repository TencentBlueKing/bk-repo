package com.tencent.bkrepo.common.metadata.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.routing.MongoDualWriteCompensationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoTemplate
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Configuration
class NodeRoutingConfiguration(
    private val defaultMongoTemplate: MongoTemplate,
) {

    @Value("\${spring.data.mongodb.multi-instance.rules.node.scatter-query.default-mode:STRICT}")
    private lateinit var defaultMode: String

    @Value("\${spring.data.mongodb.multi-instance.rules.node.scatter-query.timeout-seconds:5}")
    private var timeoutSeconds: Long = 5L

    @Bean
    @ConditionalOnBean(MongoRoutingRegistry::class)
    fun nodeMongoOperations(
        registry: MongoRoutingRegistry,
        compensationService: MongoDualWriteCompensationService?,
    ): NodeMongoOperations = DefaultNodeMongoOperations(registry, defaultMongoTemplate, compensationService)

    @Bean("nodeMongoOperations")
    @ConditionalOnMissingBean(MongoRoutingRegistry::class)
    fun nodeMongoOperationsSimple(): NodeMongoOperations = SimpleNodeMongoOperations(defaultMongoTemplate)

    @Bean
    @ConditionalOnBean(MongoRoutingRegistry::class)
    fun nodeScatterQueryService(
        registry: MongoRoutingRegistry,
        metrics: org.springframework.beans.factory.ObjectProvider<com.tencent.bkrepo.common.mongo.routing.MongoRoutingMetrics>,
        scatterTemplates: org.springframework.beans.factory.ObjectProvider<com.tencent.bkrepo.common.mongo.routing.ScatterMongoTemplateProvider>,
        projectIdCache: org.springframework.beans.factory.ObjectProvider<Sha256ProjectIdCache>,
    ): NodeScatterQueryService =
        nodeScatterQueryService(
            registry,
            NodeScatterQueryService.ScatterMode.valueOf(defaultMode.uppercase()),
            metrics.ifAvailable,
            scatterTemplates.ifAvailable,
            projectIdCache.ifAvailable,
        )

    @Bean("degradeNodeScatterQueryService")
    @ConditionalOnBean(MongoRoutingRegistry::class)
    fun degradeNodeScatterQueryService(
        registry: MongoRoutingRegistry,
        metrics: org.springframework.beans.factory.ObjectProvider<com.tencent.bkrepo.common.mongo.routing.MongoRoutingMetrics>,
        scatterTemplates: org.springframework.beans.factory.ObjectProvider<com.tencent.bkrepo.common.mongo.routing.ScatterMongoTemplateProvider>,
        projectIdCache: org.springframework.beans.factory.ObjectProvider<Sha256ProjectIdCache>,
    ): NodeScatterQueryService =
        nodeScatterQueryService(
            registry,
            NodeScatterQueryService.ScatterMode.DEGRADE,
            metrics.ifAvailable,
            scatterTemplates.ifAvailable,
            projectIdCache.ifAvailable,
        )

    private fun nodeScatterQueryService(
        registry: MongoRoutingRegistry,
        mode: NodeScatterQueryService.ScatterMode,
        metrics: com.tencent.bkrepo.common.mongo.routing.MongoRoutingMetrics? = null,
        scatterTemplates: com.tencent.bkrepo.common.mongo.routing.ScatterMongoTemplateProvider? = null,
        projectIdCache: Sha256ProjectIdCache? = null,
    ): NodeScatterQueryService = NodeScatterQueryService(
        defaultTemplate = defaultMongoTemplate,
        registry = registry,
        executor = ThreadPoolExecutor(
            2, 20, 60L, TimeUnit.SECONDS,
            ArrayBlockingQueue(200),
            { r -> Thread(r, "node-scatter").apply { isDaemon = true } },
            ThreadPoolExecutor.CallerRunsPolicy(),
        ),
        timeoutSeconds = timeoutSeconds,
        mode = mode,
        metrics = metrics,
        scatterTemplates = scatterTemplates,
        projectIdCache = projectIdCache,
    )
}
