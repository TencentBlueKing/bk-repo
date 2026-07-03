package com.tencent.bkrepo.common.metadata.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DefaultRoutingReadinessCheckerTest {

    @Test
    fun `P0 items reflect actual probe result, no config bypass when routing active`() {
        val registry: MongoRoutingRegistry = mock()
        whenever(registry.isRoutingEnabled("node")).thenReturn(true)
        val checker = DefaultRoutingReadinessChecker(
            registry = registry,
            scatterQueryService = null,
            nodeBatchQueryHelper = null,
            properties = MongoMultiInstanceProperties(),
        )

        val result = checker.check()

        DefaultRoutingReadinessChecker.LOCAL_P0_MANIFEST.forEach { (id, _) ->
            val item = result.checks.first { it.id == id }
            // 路由启用时 P0 项 passed 必须等于实际探针结果，不存在配置旁路
            assertEquals(P0RoutingReadinessProbes.check(id), item.passed, "P0 item $id bypassed probe")
        }
    }
}
