package com.tencent.bkrepo.opdata.service.mongo.migration

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.mongo.api.routing.MigrationPhase
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.dao.MigrationSyncStateDao
import com.tencent.bkrepo.common.mongo.dao.RoutingConfigDao
import com.tencent.bkrepo.common.mongo.model.TMigrationSyncState
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties.RoutingRule
import com.tencent.bkrepo.opdata.api.mongo.migration.MigrationBindingRequest
import com.tencent.bkrepo.opdata.api.mongo.migration.MigrationProjectRequest
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.TestPropertySource

/**
 * 内存库集成测试（方案 a）：用嵌入式 MongoDB 验证状态机真实 DB 推进，
 * 覆盖 mock 测试验不到的「CLEANED 后 rollback 返回 409 fail-fast」（§2.8.1 核心可逆性分级）。
 *
 * 仅测 binding/start/cleanup/rollback 的真实 DB 写路径；route 门禁依赖 registry/sidecar 等，
 * 仍由 [MongoMigrationServiceTest]（mock）覆盖，二者互补不重复。
 */
@DataMongoTest
@Import(MigrationSyncStateDao::class, RoutingConfigDao::class)
@TestPropertySource(properties = ["de.flapdoodle.mongodb.embedded.version=4.0.2"])
class MongoMigrationServiceMemoryDbTest {

    @Autowired
    lateinit var mongoTemplate: MongoTemplate

    @Autowired
    lateinit var syncStateDao: MigrationSyncStateDao

    @Autowired
    lateinit var routingConfigDao: RoutingConfigDao

    /** relaxed mock：仅用于规则名判断，不参与真实 DB 路径 */
    private val registry: MongoRoutingRegistry = mockk(relaxed = true)

    private fun buildService(properties: MongoMultiInstanceProperties = MongoMultiInstanceProperties()) =
        MongoMigrationService(
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
    fun `binding then start advances phase PENDING to INITIAL_SYNC in real DB`() {
        // 用非 PROJECT_ROUTING_RULES 规则名，规避 G-34 就绪门禁，直达真实 DB 写路径
        val ruleName = "artifact-oplog"
        val projectId = "ut-project"
        val service = buildService()

        service.binding(MigrationBindingRequest(ruleName = ruleName, projectId = projectId, targetInstance = "oplog"))
        assertEquals(MigrationPhase.PENDING, syncStateDao.findByProjectId(projectId)?.phase)

        service.start(MigrationProjectRequest(ruleName = ruleName, projectId = projectId))
        assertEquals(MigrationPhase.INITIAL_SYNC, syncStateDao.findByProjectId(projectId)?.phase)
    }

    @Test
    fun `cleanup advances phase ROUTED to CLEANUP_READY in real DB`() {
        val projectId = "ut-project"
        syncStateDao.upsert(
            TMigrationSyncState(id = projectId, projectId = projectId, ruleName = "node",
                targetInstance = "heavy", phase = MigrationPhase.ROUTED),
        )
        val service = buildService()

        every { registry.isProjectRoutedOut("node", projectId) } returns true
        service.cleanup(MigrationProjectRequest(ruleName = "node", projectId = projectId))

        assertEquals(MigrationPhase.CLEANUP_READY, syncStateDao.findByProjectId(projectId)?.phase)
    }

    @Test
    fun `rollback after CLEANED returns 409 conflict fail-fast`() {
        val projectId = "ut-project"
        syncStateDao.upsert(
            TMigrationSyncState(id = projectId, projectId = projectId, ruleName = "node",
                targetInstance = "heavy", phase = MigrationPhase.CLEANED),
        )
        val service = buildService()

        val ex = assertThrows<ErrorCodeException> {
            service.rollback(MigrationProjectRequest(ruleName = "node", projectId = projectId))
        }

        // §2.8.1：4b 阶段 CLEANED 后 API 回滚不可用，须走灾难恢复 SOP
        assertEquals(HttpStatus.CONFLICT, ex.status)
        assertEquals(MigrationPhase.CLEANED, syncStateDao.findByProjectId(projectId)?.phase)
    }

    @Test
    fun `rollback from ROUTED returns 409 conflict fail-fast`() {
        val projectId = "ut-project"
        syncStateDao.upsert(
            TMigrationSyncState(id = projectId, projectId = projectId, ruleName = "node",
                targetInstance = "heavy", phase = MigrationPhase.ROUTED),
        )
        val service = buildService()

        val ex = assertThrows<ErrorCodeException> {
            service.rollback(MigrationProjectRequest(ruleName = "node", projectId = projectId))
        }

        assertEquals(HttpStatus.CONFLICT, ex.status)
        assertEquals(MigrationPhase.ROUTED, syncStateDao.findByProjectId(projectId)?.phase)
    }

    @Test
    fun `cleanup of offload rule drops Default collection and sets CLEANED in real DB`() {
        // 模式一（Offload）：cleanupDefaultOplogCollections 走真实 dropCollection
        val ruleName = "artifact-oplog"
        val projectId = ruleName
        every { registry.listOffloadRuleNames() } returns listOf(ruleName)
        every { registry.isRoutingEnabled(ruleName) } returns true
        every { registry.isDualWrite(ruleName) } returns false
        val properties = MongoMultiInstanceProperties().apply {
            rules = mapOf(ruleName to RoutingRule().apply { collectionPrefix = "artifact_oplog_" })
        }
        syncStateDao.upsert(
            TMigrationSyncState(id = projectId, projectId = projectId, ruleName = ruleName,
                targetInstance = "oplog", phase = MigrationPhase.ROUTED),
        )
        // 在 Default 上造一个待清理的月集合
        mongoTemplate.createCollection("artifact_oplog_202601")
        val service = buildService(properties)

        service.cleanup(MigrationProjectRequest(ruleName = ruleName, projectId = projectId))

        assertEquals(MigrationPhase.CLEANED, syncStateDao.findByProjectId(projectId)?.phase)
        assertFalse(mongoTemplate.collectionExists("artifact_oplog_202601"))
    }
}
