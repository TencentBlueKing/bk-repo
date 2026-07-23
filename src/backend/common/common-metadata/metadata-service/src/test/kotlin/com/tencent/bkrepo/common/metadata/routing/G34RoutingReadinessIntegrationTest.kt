package com.tencent.bkrepo.common.metadata.routing

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** G-34：metadata 模块 P0 探针。 */
class G34RoutingReadinessIntegrationTest {

    @Test
    fun `P0 metadata probes pass`() {
        val ids = listOf("A4", "D1", "D3")
        val failures = ids.mapNotNull { id ->
            if (!P0RoutingReadinessProbes.isApplicable(id)) return@mapNotNull null
            if (P0RoutingReadinessProbes.check(id)) null else id
        }
        assertTrue(failures.isEmpty(), failures.joinToString())
    }
}
