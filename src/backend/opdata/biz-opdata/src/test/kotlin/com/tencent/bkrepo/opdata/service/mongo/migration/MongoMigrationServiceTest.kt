package com.tencent.bkrepo.opdata.service.mongo.migration

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.mongo.api.routing.MigrationPhase
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.RoutingReadinessResult
import com.tencent.bkrepo.common.mongo.dao.MigrationSyncStateDao
import com.tencent.bkrepo.common.mongo.dao.RoutingConfigDao
import com.tencent.bkrepo.common.mongo.model.TMigrationSyncState
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import com.tencent.bkrepo.opdata.api.mongo.migration.MigrationBindingRequest
import com.tencent.bkrepo.opdata.api.mongo.migration.MigrationProjectRequest
import com.tencent.bkrepo.opdata.routing.RoutingReadinessAggregator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.mongodb.core.MongoTemplate

class MongoMigrationServiceTest {

    private val registry: MongoRoutingRegistry = mockk(relaxed = true)
    private val properties = MongoMultiInstanceProperties()
    private val syncStateDao: MigrationSyncStateDao = mockk(relaxed = true)
    private val routingConfigDao: RoutingConfigDao = mockk(relaxed = true)
    private val mongoTemplate: MongoTemplate = mockk(relaxed = true)

    private val service = MongoMigrationService(
        registry = registry,
        properties = properties,
        syncStateDao = syncStateDao,
        routingConfigDao = routingConfigDao,
        mongoTemplate = mongoTemplate,
        initValidator = null,
        migrationGate = null,
        sidecarVerifier = null,
        compensationService = null,
        compensationHealthChecker = null,
        readinessAggregator = null,
    )

    @Test
    fun `cleanup sets phase CLEANUP_READY not CLEANED`() {
        val request = MigrationProjectRequest(ruleName = "node", projectId = "p1")
        every { syncStateDao.findByProjectId("p1") } returns TMigrationSyncState(
            projectId = "p1",
            ruleName = "node",
            targetInstance = "heavy",
            phase = MigrationPhase.ROUTED,
        )

        service.cleanup(request)

        verify(exactly = 1) { syncStateDao.updatePhase("p1", MigrationPhase.CLEANUP_READY) }
        verify(exactly = 0) { syncStateDao.updatePhase("p1", MigrationPhase.CLEANED) }
    }

    @Test
    fun `cleanup rejects project not in ROUTED phase`() {
        val request = MigrationProjectRequest(ruleName = "node", projectId = "p1")
        every { syncStateDao.findByProjectId("p1") } returns TMigrationSyncState(
            projectId = "p1",
            ruleName = "node",
            targetInstance = "heavy",
            phase = MigrationPhase.DUAL_WRITE,
        )

        val ex = assertThrows<ErrorCodeException> { service.cleanup(request) }
        assertTrue(ex.params.any { it.toString().contains("ROUTED") })
        verify(exactly = 0) { syncStateDao.updatePhase("p1", any()) }
    }

    @Test
    fun `assertNodeReadiness returns 409 conflict when G-34 not passed`() {
        val notReadyAggregator: RoutingReadinessAggregator = mockk()
        every { notReadyAggregator.aggregate() } returns RoutingReadinessResult(ready = false, checks = emptyList())
        val gatedService = MongoMigrationService(
            registry = registry,
            properties = properties,
            syncStateDao = syncStateDao,
            routingConfigDao = routingConfigDao,
            mongoTemplate = mongoTemplate,
            initValidator = null,
            migrationGate = null,
            sidecarVerifier = null,
            compensationService = null,
            compensationHealthChecker = null,
            readinessAggregator = notReadyAggregator,
        )
        val request = MigrationBindingRequest(ruleName = "node", projectId = "p1", targetInstance = "heavy")

        val ex = assertThrows<ErrorCodeException> { gatedService.binding(request) }

        assertEquals(HttpStatus.CONFLICT, ex.status)
        assertTrue(ex.params.any { it.toString().contains("ROUTING_READINESS_BLOCKED") })
    }
}