package com.tencent.bkrepo.common.metadata.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.ReadinessCheckItem
import com.tencent.bkrepo.common.mongo.api.routing.RoutingReadinessChecker
import com.tencent.bkrepo.common.mongo.api.routing.RoutingReadinessResult
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import org.springframework.stereotype.Component

@Component
class DefaultRoutingReadinessChecker(
    private val registry: MongoRoutingRegistry? = null,
    private val scatterQueryService: NodeScatterQueryService? = null,
    private val nodeBatchQueryHelper: NodeBatchQueryHelper? = null,
    private val properties: MongoMultiInstanceProperties,
) : RoutingReadinessChecker {

    override fun check(): RoutingReadinessResult {
        val checks = mutableListOf<ReadinessCheckItem>()
        checks += item("INFRA-01", "MongoRoutingRegistry bean", registry != null)
        checks += item(
            "INFRA-02",
            "node routing-enabled",
            registry?.isRoutingEnabled(NODE_RULE) == true,
        )
        checks += item("M5-01", "NodeScatterQueryService", scatterQueryService != null)
        checks += item("M5-02", "NodeBatchQueryHelper", nodeBatchQueryHelper != null)
        checks += item(
            "M5-03",
            "local config-version up to date",
            registry?.isConfigUpToDate() != false,
        )
        checks += item(
            "M5-05",
            "G-43 scatter dedicated pool",
            classExists("com.tencent.bkrepo.common.mongo.routing.ScatterMongoTemplateProvider"),
        )
        val routingActive = registry?.isRoutingEnabled(NODE_RULE) == true
        LOCAL_P0_MANIFEST.forEach { (id, desc) ->
            // 路由启用后必须通过实际 P0 探针，禁止配置项旁路（防 G-34 假绿）
            val passed = !routingActive || P0RoutingReadinessProbes.check(id)
            checks += item(id, desc, passed)
        }
        return RoutingReadinessResult(
            ready = checks.all { it.passed },
            checks = checks,
        )
    }

    private fun item(
        id: String,
        description: String,
        passed: Boolean,
        detail: String? = null,
    ): ReadinessCheckItem =
        ReadinessCheckItem(id = id, description = description, passed = passed, detail = detail)

    private fun classExists(name: String): Boolean =
        runCatching { Class.forName(name) }.isSuccess

    companion object {
        private const val NODE_RULE = "node"

        /** repository 服务本地 P0 检查项（A4/D1/D3），跨服务项由 opdata 聚合 */
        val LOCAL_P0_MANIFEST: Map<String, String> = linkedMapOf(
            "A4" to "metadata RNodeDao.pageBySha256 scatter",
            "D1" to "NodeModifyEventListener async routing",
            "D3" to "async write paths explicit projectId",
        )
    }
}
