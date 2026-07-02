package com.tencent.bkrepo.common.mongo.routing

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * §3.19.2 / G-34：禁止非白名单模块直连 Default `node_*` 集合。
 */
class NodeDirectMongoAuditTest {

    @Test
    fun `no direct mongoTemplate access to node collections outside allowlist`() {
        val violations = NodeDirectMongoAuditor.audit()
        assertTrue(
            violations.isEmpty(),
            "Direct node_ mongoTemplate usage outside allowlist:\n${violations.joinToString("\n")}",
        )
    }
}
