package com.tencent.bkrepo.opdata.routing

import com.tencent.bkrepo.common.api.constant.MS_AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.ReadinessCheckItem
import com.tencent.bkrepo.common.mongo.api.routing.RoutingReadinessResult
import com.tencent.bkrepo.common.mongo.routing.NodeDirectMongoAuditor
import com.tencent.bkrepo.common.security.constant.MS_AUTH_HEADER_SECURITY_TOKEN
import com.tencent.bkrepo.common.security.service.ServiceAuthManager
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

/**
 * G-34 路由就绪聚合器（方案 B）。
 *
 * 两层验证：
 * 1. 代码级检查（read）—— 每服务只取 1 个实例，同服务实例代码同构
 * 2. 本地 config-version 就绪（M5-03）—— 仅探测本实例，集群 100% Pod 由运维 SOP 确认
 *
 * 本地：INFRA + M5 + G-34-CI + opdata A3
 * 远程：auth(A1) + replication(A2) + job(A5/A6/B/C/D2) + repository(A4/D1/D3)
 */
@Service
class RoutingReadinessAggregator(
    private val discoveryClient: DiscoveryClient,
    private val serviceAuthManager: ServiceAuthManager,
    private val opdataProbe: OpdataRoutingReadinessProbe,
    private val registry: MongoRoutingRegistry? = null,
    @Value("\${service.prefix:}")
    private val servicePrefix: String = "",
) {

    fun aggregate(): RoutingReadinessResult {
        val allChecks = mutableListOf<ReadinessCheckItem>()

        // ── 本地基础设施 & M5 ──
        allChecks += item("INFRA-01", "MongoRoutingRegistry bean", registry != null)
        allChecks += item(
            "INFRA-02", "node routing-enabled",
            registry?.isRoutingEnabled(NODE_RULE) == true,
        )
        allChecks += item(
            "M5-03", "local config-version up to date",
            registry?.isConfigUpToDate() != false,
        )

        val auditViolations = NodeDirectMongoAuditor.audit()
        allChecks += item(
            "G-34-CI", "no direct node_ mongo access",
            auditViolations.isEmpty(),
            auditViolations.take(5).joinToString().ifEmpty { null },
        )
        allChecks += opdataProbe.checkAll()

        // ── 远程服务：每服务只取 1 个实例（代码级检查，实例间同构）──
        for (svc in listOf("auth", "replication", "job", "repository")) {
            allChecks += fetchServiceReadiness(svc)
        }

        return RoutingReadinessResult(
            ready = allChecks.all { it.passed },
            checks = allChecks,
        )
    }

    // ──── 远程调用（仅取第 1 个可用实例）────

    private fun fetchServiceReadiness(service: String): List<ReadinessCheckItem> {
        val name = if (servicePrefix.isNotBlank()) "$servicePrefix$service" else service
        val instances = discoveryClient.getInstances(name)

        if (instances.isEmpty()) {
            return listOf(unreachableItem(service, "no instances registered"))
        }

        val instance = instances.first()
        val url = instance.uri.toString().trimEnd('/') + "/routing/readiness"

        return try {
            val headers = HttpHeaders().apply {
                add(MS_AUTH_HEADER_SECURITY_TOKEN, serviceAuthManager.getSecurityToken())
                add(MS_AUTH_HEADER_UID, SYSTEM_USER)
            }
            val response = restTemplate.exchange(
                url, HttpMethod.GET, HttpEntity<Any>(headers), Response::class.java,
            ).body
            if (response?.data == null) {
                listOf(unreachableItem(service, "empty response"))
            } else {
                parseChecks(response)
            }
        } catch (e: Exception) {
            logger.warn("Fetch readiness from $service failed: ${e.message}")
            listOf(unreachableItem(service, e.message ?: "unknown"))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseChecks(response: Response<*>): List<ReadinessCheckItem> {
        return try {
            val data = response.data as? Map<String, Any?> ?: return emptyList()
            val checksRaw = data["checks"] as? List<Map<String, Any?>> ?: return emptyList()
            checksRaw.map { check ->
                ReadinessCheckItem(
                    id = check["id"]?.toString() ?: "?",
                    description = check["description"]?.toString() ?: "",
                    passed = check["passed"] as? Boolean ?: false,
                    detail = check["detail"]?.toString(),
                )
            }
        } catch (e: Exception) {
            logger.warn("Parse readiness response failed: ${e.message}")
            emptyList()
        }
    }

    // ──── helpers ────

    private fun item(id: String, desc: String, passed: Boolean, detail: String? = null) =
        ReadinessCheckItem(id = id, description = desc, passed = passed, detail = detail)

    private fun unreachableItem(service: String, reason: String) =
        ReadinessCheckItem(
            id = "REMOTE-$service",
            description = "remote service $service unreachable",
            passed = false,
            detail = reason,
        )

    companion object {
        private val logger = LoggerFactory.getLogger(RoutingReadinessAggregator::class.java)
        private const val NODE_RULE = "node"
        private val restTemplate = RestTemplate()
    }
}