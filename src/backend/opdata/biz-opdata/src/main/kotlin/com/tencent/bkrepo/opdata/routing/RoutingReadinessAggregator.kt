package com.tencent.bkrepo.opdata.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.ReadinessCheckItem
import com.tencent.bkrepo.common.mongo.api.routing.RoutingReadinessResult
import org.springframework.stereotype.Service

/**
 * G-34 路由就绪检查。
 *
 * 运行时检查项：INFRA（registry bean + routing enabled）+ M5（config-version）。
 * P0 代码级清单由 CI 集成测试 `P0RoutingReadinessProbes` 在构建阶段验证。
 */
@Service
class RoutingReadinessAggregator(
    private val registry: MongoRoutingRegistry? = null,
) {
    fun aggregate(): RoutingReadinessResult {
        val checks = listOf(
            item("INFRA-01", "MongoRoutingRegistry bean", registry != null),
            item("INFRA-02", "node routing-enabled", registry?.isRoutingEnabled(NODE_RULE) == true),
            item("M5-03", "local config-version up to date", registry?.isConfigUpToDate() != false),
        )
        return RoutingReadinessResult(ready = checks.all { it.passed }, checks = checks)
    }

    private fun item(id: String, desc: String, passed: Boolean, detail: String? = null) =
        ReadinessCheckItem(id = id, description = desc, passed = passed, detail = detail)

    companion object {
        private const val NODE_RULE = "node"
    }
}