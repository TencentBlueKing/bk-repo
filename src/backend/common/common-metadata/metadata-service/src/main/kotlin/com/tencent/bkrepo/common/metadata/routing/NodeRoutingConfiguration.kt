package com.tencent.bkrepo.common.metadata.routing

import com.tencent.bkrepo.common.mongo.api.routing.DualWriteExecutor
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.routing.MongoDualWriteCompensationService
import com.tencent.bkrepo.common.mongo.routing.MongoRoutingMetrics
import com.tencent.bkrepo.common.mongo.routing.ScatterMongoTemplateProvider
import org.springframework.beans.factory.ObjectProvider
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
    fun nodeMongoOperations(
        registryProvider: ObjectProvider<MongoRoutingRegistry>,
        compensationServiceProvider: ObjectProvider<MongoDualWriteCompensationService>,
        dualWriteExecutor: ObjectProvider<DualWriteExecutor>,
    ): NodeMongoOperations {
        val registry = registryProvider.ifAvailable
        val compensationService = compensationServiceProvider.ifAvailable
        return if (registry != null && compensationService != null) {
            DefaultNodeMongoOperations(
                registry,
                defaultMongoTemplate,
                compensationService,
                dualWriteExecutor.ifAvailable,
            )
        } else {
            SimpleNodeMongoOperations(defaultMongoTemplate)
        }
    }

    @Bean
    fun nodeScatterQueryService(
        registryProvider: ObjectProvider<MongoRoutingRegistry>,
        batchQueryHelperProvider: ObjectProvider<NodeBatchQueryHelper>,
        metrics: ObjectProvider<MongoRoutingMetrics>,
        scatterTemplates: ObjectProvider<ScatterMongoTemplateProvider>,
    ): NodeScatterQueryService? {
        val registry = registryProvider.ifAvailable ?: return null
        return nodeScatterQueryService(
            registry,
            batchQueryHelperProvider.ifAvailable,
            NodeScatterQueryService.ScatterMode.valueOf(defaultMode.uppercase()),
            metrics.ifAvailable,
            scatterTemplates.ifAvailable,
        )
    }

    private fun nodeScatterQueryService(
        registry: MongoRoutingRegistry,
        batchQueryHelper: NodeBatchQueryHelper? = null,
        mode: NodeScatterQueryService.ScatterMode,
        metrics: com.tencent.bkrepo.common.mongo.routing.MongoRoutingMetrics? = null,
        scatterTemplates: com.tencent.bkrepo.common.mongo.routing.ScatterMongoTemplateProvider? = null,
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
        batchQueryHelper = batchQueryHelper,
    )
}
