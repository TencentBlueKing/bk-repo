package com.tencent.bkrepo.job.batch.routing

import com.tencent.bkrepo.common.metadata.routing.DefaultRoutingReadinessChecker
import com.tencent.bkrepo.common.metadata.routing.P0RoutingReadinessProbes
import com.tencent.bkrepo.common.mongo.routing.NodeDirectMongoAuditor
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** G-34：P0 改造矩阵 + 直连审计 E2E 验收（biz-job 全 classpath）。 */
class G34RoutingReadinessIntegrationTest {

    @Test
    fun `P0 manifest probes all pass for classes on classpath`() {
        val failures = DefaultRoutingReadinessChecker.P0_MANIFEST.mapNotNull { (id, desc) ->
            if (!P0RoutingReadinessProbes.isApplicable(id)) return@mapNotNull null
            if (P0RoutingReadinessProbes.check(id)) null else "$id: $desc"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `node direct mongo audit passes`() {
        val violations = NodeDirectMongoAuditor.audit()
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }
}
