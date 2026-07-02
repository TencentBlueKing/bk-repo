package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import java.net.InetAddress
import java.time.LocalDateTime
import org.bson.Document
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component

/**
 * 集群 Pod config-version 心跳注册表（§3.10 / §3.20）。
 * ponytail: 存 Default `mongo_routing_pod_registry`，无 K8s API 依赖。
 */
@Component
@ConditionalOnBean(MongoRoutingRegistry::class)
class MongoRoutingPodRegistry(
    private val mongoTemplate: MongoTemplate,
    private val properties: MongoMultiInstanceProperties,
    private val registry: MongoRoutingRegistry,
    @Value("\${spring.application.name:bkrepo}") private val applicationName: String,
) {

    fun podId(): String = "$applicationName@${hostName()}"

    fun heartbeat() {
        if (!properties.podRegistry.enabled) return
        val now = LocalDateTime.now()
        val query = Query(Criteria.where(FIELD_ID).`is`(podId()))
        val update = Update()
            .set(FIELD_APPLICATION, applicationName)
            .set(FIELD_HOST, hostName())
            .set(FIELD_CONFIG_VERSION, registry.getConfigVersion())
            .set(FIELD_MIN_CONFIG_VERSION, registry.getMinConfigVersion())
            .set(FIELD_LAST_SEEN, now)
        mongoTemplate.upsert(query, update, COLLECTION)
    }

    /**
     * 所有仍在线的 Pod 均已达到 [minConfigVersion] 时返回 passed=true。
     * [minConfigVersion]==0 时短路通过（未启用版本门禁）。
     */
    fun verifyClusterUpToDate(minConfigVersion: Long): ClusterPodCheck {
        if (!properties.podRegistry.enabled || minConfigVersion <= 0L) {
            return ClusterPodCheck(passed = true)
        }
        if (!registry.isConfigUpToDate()) {
            return ClusterPodCheck(
                passed = false,
                reason = "local configVersion=${registry.getConfigVersion()} < min=$minConfigVersion",
            )
        }
        val cutoff = LocalDateTime.now().minusSeconds(properties.podRegistry.staleSeconds)
        val live = mongoTemplate.find(
            Query(Criteria.where(FIELD_LAST_SEEN).gte(cutoff)),
            Document::class.java,
            COLLECTION,
        )
        val behind = live.mapNotNull { doc ->
            val id = doc.getString(FIELD_ID) ?: return@mapNotNull null
            val version = doc.getLong(FIELD_CONFIG_VERSION) ?: 0L
            if (version < minConfigVersion) "$id(v=$version)" else null
        }
        if (behind.isNotEmpty()) {
            return ClusterPodCheck(
                passed = false,
                reason = "pods behind minConfigVersion=$minConfigVersion: ${behind.joinToString()}",
                livePodCount = live.size,
                behindPods = behind,
            )
        }
        return ClusterPodCheck(passed = true, livePodCount = live.size)
    }

    data class ClusterPodCheck(
        val passed: Boolean,
        val reason: String? = null,
        val livePodCount: Int = 0,
        val behindPods: List<String> = emptyList(),
    )

    private fun hostName(): String = runCatching {
        InetAddress.getLocalHost().hostName
    }.getOrDefault("unknown")

    companion object {
        const val COLLECTION = "mongo_routing_pod_registry"
        const val FIELD_ID = "_id"
        const val FIELD_APPLICATION = "application"
        const val FIELD_HOST = "host"
        const val FIELD_CONFIG_VERSION = "configVersion"
        const val FIELD_MIN_CONFIG_VERSION = "minConfigVersion"
        const val FIELD_LAST_SEEN = "lastSeen"
    }
}
