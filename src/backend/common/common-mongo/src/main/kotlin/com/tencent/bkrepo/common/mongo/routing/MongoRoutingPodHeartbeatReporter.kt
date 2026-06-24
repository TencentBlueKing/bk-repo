package com.tencent.bkrepo.common.mongo.routing

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** 周期性上报本 Pod config-version，供集群门禁校验。 */
@Component
@ConditionalOnBean(MongoRoutingPodRegistry::class)
class MongoRoutingPodHeartbeatReporter(
    private val podRegistry: MongoRoutingPodRegistry,
) {

    @Scheduled(
        fixedDelayString = "\${spring.data.mongodb.multi-instance.pod-registry.heartbeat-interval-ms:30000}",
        initialDelayString = "\${spring.data.mongodb.multi-instance.pod-registry.heartbeat-interval-ms:30000}",
    )
    fun report() {
        try {
            podRegistry.heartbeat()
        } catch (e: Exception) {
            logger.warn("Pod config-version heartbeat failed: {}", e.message)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MongoRoutingPodHeartbeatReporter::class.java)
    }
}
