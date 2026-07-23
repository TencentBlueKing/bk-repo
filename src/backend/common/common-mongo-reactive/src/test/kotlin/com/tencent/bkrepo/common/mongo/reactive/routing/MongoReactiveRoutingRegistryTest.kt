package com.tencent.bkrepo.common.mongo.reactive.routing

import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.DisposableBean

/**
 * Reactive/Sync Registry 行为对齐测试（P2-16）：连接池参数与 shutdown 生命周期。
 */
class MongoReactiveRoutingRegistryTest {

    @Test
    fun `reactive registry builds templates from instance config and supports shutdown`() {
        val properties = MongoMultiInstanceProperties().apply {
            rules = mapOf(
                "node" to MongoMultiInstanceProperties.RoutingRule(
                    collectionPrefix = "node_",
                    instances = mapOf(
                        "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(
                            uri = "mongodb://127.0.0.1:27017/bkrepo",
                            maxPoolSize = 42,
                            minPoolSize = 2,
                        ),
                    ),
                ),
            )
        }
        val registry = MongoReactiveRoutingRegistry(properties)
        assertNotNull(registry.resolveRuleName("node_0"))
        assert(registry is DisposableBean)
        assertDoesNotThrow { registry.destroy() }
    }
}
