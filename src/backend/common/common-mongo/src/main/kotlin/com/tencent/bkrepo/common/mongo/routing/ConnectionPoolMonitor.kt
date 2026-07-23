package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 多实例 Mongo 连接池观测（infra-ops §7.3）。
 * 读 Micrometer mongodb.driver.pool.* 指标告警，不动态重建连接池。
 */
@Component
@ConditionalOnBean(MongoRoutingRegistry::class)
class ConnectionPoolMonitor(
    private val meterRegistry: MeterRegistry,
) {

    @Scheduled(fixedDelayString = "\${bkrepo.mongo.routing.pool-monitor.interval-ms:60000}")
    fun checkConnectionPools() {
        meterRegistry.find("mongodb.driver.pool.waitqueuesize").gauges().forEach { gauge ->
            val waitQueue = gauge.value()
            if (waitQueue >= WAIT_QUEUE_ALARM) {
                val poolName = gauge.id.getTag("pool.name") ?: "unknown"
                logger.warn("Connection pool wait queue high: pool[$poolName] waitQueue=${waitQueue.toLong()}")
            }
        }
        meterRegistry.find("mongodb.driver.pool.checkedout").gauges().forEach { gauge ->
            val poolName = gauge.id.getTag("pool.name") ?: return@forEach
            val checkedOut = gauge.value()
            val maxSize = meterRegistry.find("mongodb.driver.pool.maxsize")
                .tag("pool.name", poolName)
                .gauge()
                ?.value()
                ?: return@forEach
            if (maxSize > 0 && checkedOut > maxSize * HIGH_WATER_RATIO) {
                logger.warn(
                    "Connection pool utilization high: pool[$poolName] " +
                        "checkedOut=${checkedOut.toLong()} maxSize=${maxSize.toLong()}",
                )
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ConnectionPoolMonitor::class.java)
        private const val WAIT_QUEUE_ALARM = 1.0
        private const val HIGH_WATER_RATIO = 0.9
    }
}
