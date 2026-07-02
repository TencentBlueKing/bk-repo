package com.tencent.bkrepo.common.mongo.routing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * CompensationHealthController 单元测试（Spec §25.2.4 E-02）。
 *
 * 覆盖健康检查场景：
 * 1. pendingCount < MAX(500) + age < 30min → healthy=true
 * 2. pendingCount >= 500 → healthy=false
 * 3. oldestPendingAgeSeconds >= 1800 → healthy=false
 */
class CompensationHealthControllerTest {

    private lateinit var healthChecker: CompensationHealthChecker
    private lateinit var controller: CompensationHealthController

    @BeforeEach
    fun setUp() {
        healthChecker = mock()
        controller = CompensationHealthController(healthChecker)
    }

    @Test
    fun `health returns healthy when pending count and age are under thresholds`() {
        whenever(healthChecker.check("node")).thenReturn(
            CompensationHealthChecker.CompensationHealthStatus(
                ruleName = "node",
                pendingCount = 50L,
                oldestPendingAgeSeconds = 0L,
                healthy = true,
            ),
        )

        val response = controller.health("node")
        assertEquals("node", response.ruleName)
        assertEquals(50L, response.pendingCount)
        assertTrue(response.healthy)
    }

    @Test
    fun `health returns unhealthy when pending count exceeds MAX_HEALTHY_PENDING`() {
        whenever(healthChecker.check("node")).thenReturn(
            CompensationHealthChecker.CompensationHealthStatus(
                ruleName = "node",
                pendingCount = 600L,
                oldestPendingAgeSeconds = 0L,
                healthy = false,
            ),
        )

        val response = controller.health("node")
        assertEquals(600L, response.pendingCount)
        assertFalse(response.healthy)
    }

    @Test
    fun `health returns unhealthy when oldest task age exceeds MAX_HEALTHY_AGE`() {
        whenever(healthChecker.check("node")).thenReturn(
            CompensationHealthChecker.CompensationHealthStatus(
                ruleName = "node",
                pendingCount = 10L,
                oldestPendingAgeSeconds = 3601L,
                healthy = false,
            ),
        )

        val response = controller.health("node")
        assertEquals(10L, response.pendingCount)
        assertTrue(response.oldestPendingAgeSeconds > 1800)
        assertFalse(response.healthy)
    }

    @Test
    fun `allHealth returns health status for all rules`() {
        whenever(healthChecker.check("node")).thenReturn(
            CompensationHealthChecker.CompensationHealthStatus("node", 0L, 0L, true),
        )
        whenever(healthChecker.check("artifact-oplog")).thenReturn(
            CompensationHealthChecker.CompensationHealthStatus("artifact-oplog", 0L, 0L, true),
        )

        val responses = controller.allHealth()
        assertEquals(2, responses.size)
        assertEquals(setOf("node", "artifact-oplog"), responses.map { it.ruleName }.toSet())
        assertTrue(responses.all { it.healthy })
    }
}
