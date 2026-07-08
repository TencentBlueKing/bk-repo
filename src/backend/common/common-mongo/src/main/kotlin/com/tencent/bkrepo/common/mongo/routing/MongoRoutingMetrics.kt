package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** M8：bkrepo.mongo.routing.* 指标注册（ponytail: 仅核心计数/深度，P99 等由后续接入） */
@Component
@ConditionalOnBean(MongoRoutingRegistry::class)
class MongoRoutingMetrics(
    private val meterRegistry: MeterRegistry,
    private val compensationService: MongoDualWriteCompensationService,
    private val routingRegistry: MongoRoutingRegistry,
    @Value("\${spring.application.name:unknown}") private val serviceName: String,
) {
    private val routingHits = ConcurrentHashMap<String, AtomicLong>()
    private val routingQueries = ConcurrentHashMap<String, AtomicLong>()

    private val fallbackCounter = Counter.builder("bkrepo.mongo.routing.fallback.count")
        .description("Routing fallback to default instance")
        .register(meterRegistry)

    private val contextLostCounter = Counter.builder("bkrepo.mongo.routing.context.lost.count")
        .description("Write routing context lost")
        .register(meterRegistry)

    private val keyExtractFailureCounter = Counter.builder("bkrepo.mongo.routing.key.extract.failure")
        .description("projectId extraction failures")
        .register(meterRegistry)

    private val noneMatchedCounter = Counter.builder("bkrepo.mongo.routing.none.matched.count")
        .description("NONE-mode dual-write primary matched (secondary may have missed)")
        .register(meterRegistry)

    init {
        listOf("node", "artifact-oplog").forEach { ruleName ->
            routingHits.computeIfAbsent(ruleName) { AtomicLong(0) }
            routingQueries.computeIfAbsent(ruleName) { AtomicLong(0) }
            val tags = listOf(Tag.of("ruleName", ruleName))
            Gauge.builder("bkrepo.mongo.routing.hit.rate") { hitRate(ruleName) }
                .tags(tags)
                .register(meterRegistry)
            Gauge.builder("bkrepo.mongo.routing.compensation.queue.depth") {
                compensationService.countPendingTasks(ruleName).toDouble()
            }
                .tags(tags)
                .register(meterRegistry)
            Gauge.builder("bkrepo_mongo_routing_config_version") {
                routingRegistry.getConfigVersion().toDouble()
            }
                .tags(listOf(Tag.of("rule", ruleName), Tag.of("service", serviceName)))
                .description("Local config-version for cluster consistency monitoring")
                .register(meterRegistry)
        }

        // P1-6: fieldCache 容量可见性
        val registry = routingRegistry as? DefaultMongoRoutingRegistry
        if (registry != null) {
            Gauge.builder("bkrepo.mongo.routing.field.cache.size") {
                registry.fieldCache.size.toDouble()
            }
                .description("Routing field cache entries (entity class → Field)")
                .register(meterRegistry)
            Gauge.builder("bkrepo.mongo.routing.field.cache.missing.size") {
                registry.missingFieldCache.size.toDouble()
            }
                .description("Routing field cache negative entries")
                .register(meterRegistry)
        }

        // P1-7: 双写线程池队列深度与活跃线程数
        Gauge.builder("bkrepo.mongo.routing.dual.write.executor.queue.size") {
            MongoDualWriteSupport.executor.queue.size.toDouble()
        }
            .description("Dual-write executor queue depth")
            .register(meterRegistry)
        Gauge.builder("bkrepo.mongo.routing.dual.write.executor.active.count") {
            MongoDualWriteSupport.executor.activeCount.toDouble()
        }
            .description("Dual-write executor active threads")
            .register(meterRegistry)
    }

    fun recordRoutingQuery(ruleName: String, hit: Boolean) {
        routingQueries.computeIfAbsent(ruleName) { AtomicLong(0) }.incrementAndGet()
        if (hit) {
            routingHits.computeIfAbsent(ruleName) { AtomicLong(0) }.incrementAndGet()
        }
    }

    fun recordFallback(ruleName: String) {
        fallbackCounter.increment()
    }

    fun recordContextLost() {
        contextLostCounter.increment()
    }

    fun recordKeyExtractFailure() {
        keyExtractFailureCounter.increment()
    }

    fun recordNoneMatched() {
        noneMatchedCounter.increment()
    }

    fun recordScatterQuery(nanos: Long, partial: Boolean) {
        scatterQueryTimer.record(nanos, java.util.concurrent.TimeUnit.NANOSECONDS)
        if (partial) scatterPartialCounter.increment()
    }

    private val scatterQueryTimer = io.micrometer.core.instrument.Timer.builder(
        "bkrepo.mongo.routing.scatter.query.rt",
    ).register(meterRegistry)

    private val scatterPartialCounter = Counter.builder(
        "bkrepo.mongo.routing.scatter.query.partial.count",
    ).register(meterRegistry)

    private fun hitRate(ruleName: String): Double {
        val total = routingQueries[ruleName]?.get() ?: 0L
        if (total == 0L) return 1.0
        val hits = routingHits[ruleName]?.get() ?: 0L
        return hits.toDouble() / total
    }
}
