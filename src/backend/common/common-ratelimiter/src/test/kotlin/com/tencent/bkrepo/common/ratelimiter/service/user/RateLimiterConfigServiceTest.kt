package com.tencent.bkrepo.common.ratelimiter.service.user

import com.tencent.bkrepo.common.ratelimiter.model.RateLimitCreatOrUpdateRequest
import com.tencent.bkrepo.common.ratelimiter.model.TRateLimit
import com.tencent.bkrepo.common.ratelimiter.repository.RateLimitRepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever
import java.time.Duration

/**
 * RateLimiterConfigService 单元测试（纯 Mockito，无 Spring 上下文）
 *
 * 核心验证点：
 *   1. 每个公共方法正确委托给 RateLimitRepository
 *   2. create/update 正确构建 TRateLimit 实体（字段映射无误）
 *   3. host 在无 Spring 注入时使用默认值 "127.0.0.1"
 */
class RateLimiterConfigServiceTest {

    private lateinit var repo: RateLimitRepository
    private lateinit var svc: RateLimiterConfigService

    private val sampleEntity = TRateLimit(
        id = "id1",
        resource = "/proj/repo",
        limitDimension = "URL",
        algo = "FIXED_WINDOW",
        limit = 100,
        duration = Duration.ofSeconds(1),
        scope = "LOCAL",
        moduleName = listOf("generic"),
    )

    private val sampleRequest = RateLimitCreatOrUpdateRequest(
        id = null,
        resource = "/proj/repo",
        limitDimension = "URL",
        algo = "FIXED_WINDOW",
        limit = 100,
        duration = 1L,
        capacity = null,
        scope = "LOCAL",
        moduleName = listOf("generic"),
        keepConnection = true,
    )

    @BeforeEach
    fun setup() {
        repo = mock(RateLimitRepository::class.java)
        svc = RateLimiterConfigService(repo)
    }

    // ─── host default ────────────────────────────────────────────────────────────

    @Test
    fun `host defaults to 127 dot 0 dot 0 dot 1 when spring cloud ip is not injected`() {
        Assertions.assertEquals("127.0.0.1", svc.host)
    }

    // ─── list ────────────────────────────────────────────────────────────────────

    @Test
    fun `list — delegates to repository findAll`() {
        whenever(repo.findAll()).thenReturn(listOf(sampleEntity))

        val result = svc.list()

        verify(repo).findAll()
        Assertions.assertEquals(1, result.size)
        Assertions.assertEquals("id1", result.first().id)
    }

    @Test
    fun `list — returns empty list when repository returns empty`() {
        whenever(repo.findAll()).thenReturn(emptyList())

        val result = svc.list()

        Assertions.assertTrue(result.isEmpty())
    }

    // ─── create ──────────────────────────────────────────────────────────────────

    @Test
    fun `create — delegates to repository insert with correct field mapping`() {
        val captor = argumentCaptor<TRateLimit>()

        svc.create(sampleRequest)

        verify(repo).insert(captor.capture())
        val inserted = captor.firstValue
        Assertions.assertNull(inserted.id, "id should be null for new entity")
        Assertions.assertEquals(sampleRequest.resource, inserted.resource)
        Assertions.assertEquals(sampleRequest.limitDimension, inserted.limitDimension)
        Assertions.assertEquals(sampleRequest.algo, inserted.algo)
        Assertions.assertEquals(sampleRequest.limit, inserted.limit)
        Assertions.assertEquals(Duration.ofSeconds(sampleRequest.duration), inserted.duration)
        Assertions.assertEquals(sampleRequest.scope, inserted.scope)
    }

    // ─── checkExist (id) ─────────────────────────────────────────────────────────

    @Test
    fun `checkExist by id — returns true when repository returns true`() {
        whenever(repo.existsById("id1")).thenReturn(true)

        Assertions.assertTrue(svc.checkExist("id1"))
        verify(repo).existsById("id1")
    }

    @Test
    fun `checkExist by id — returns false when repository returns false`() {
        whenever(repo.existsById("missing")).thenReturn(false)

        Assertions.assertFalse(svc.checkExist("missing"))
    }

    // ─── checkExist (request) ────────────────────────────────────────────────────

    @Test
    fun `checkExist by request — delegates resource and dimension to repository`() {
        whenever(repo.existsByResourceAndLimitDimension("/proj/repo", "URL")).thenReturn(true)

        val result = svc.checkExist(sampleRequest)

        verify(repo).existsByResourceAndLimitDimension("/proj/repo", "URL")
        Assertions.assertTrue(result)
    }

    // ─── delete ──────────────────────────────────────────────────────────────────

    @Test
    fun `delete — delegates to repository removeById`() {
        svc.delete("id1")

        verify(repo).removeById("id1")
    }

    // ─── getById ─────────────────────────────────────────────────────────────────

    @Test
    fun `getById — returns entity from repository`() {
        whenever(repo.findById("id1")).thenReturn(sampleEntity)

        val result = svc.getById("id1")

        verify(repo).findById("id1")
        Assertions.assertNotNull(result)
        Assertions.assertEquals("id1", result?.id)
    }

    @Test
    fun `getById — returns null when entity not found`() {
        whenever(repo.findById("missing")).thenReturn(null)

        val result = svc.getById("missing")

        Assertions.assertNull(result)
    }

    // ─── findByModuleNameAndLimitDimension ────────────────────────────────────────

    @Test
    fun `findByModuleNameAndLimitDimension — delegates to repository`() {
        whenever(repo.findByModuleNameAndLimitDimension("generic", "URL"))
            .thenReturn(listOf(sampleEntity))

        val result = svc.findByModuleNameAndLimitDimension("generic", "URL")

        verify(repo).findByModuleNameAndLimitDimension("generic", "URL")
        Assertions.assertEquals(1, result.size)
    }

    // ─── findByResourceAndLimitDimension ─────────────────────────────────────────

    @Test
    fun `findByResourceAndLimitDimension — delegates to repository`() {
        whenever(repo.findByResourceAndLimitDimension("/proj/repo", "URL"))
            .thenReturn(listOf(sampleEntity))

        val result = svc.findByResourceAndLimitDimension("/proj/repo", "URL")

        verify(repo).findByResourceAndLimitDimension("/proj/repo", "URL")
        Assertions.assertEquals(1, result.size)
    }

    // ─── update ──────────────────────────────────────────────────────────────────

    @Test
    fun `update without targets — delegates to repository save`() {
        val updateRequest = sampleRequest.copy(id = "id1", targets = null)
        // Stub save to return non-null so the ?: run branch is NOT triggered
        whenever(repo.save(any())).thenReturn(sampleEntity)

        svc.update(updateRequest)

        verify(repo).save(any())
    }

    @Test
    fun `update with targets — delegates to repository save with targets`() {
        val updateRequest = sampleRequest.copy(id = "id1", targets = listOf("10.0.0.1"))
        val captor = argumentCaptor<TRateLimit>()
        // Stub save to return non-null so the ?: run branch is NOT triggered:
        // targets?.let { save(withTargets) } returns non-null → run block skipped
        whenever(repo.save(any())).thenReturn(sampleEntity)

        svc.update(updateRequest)

        verify(repo).save(captor.capture())
        Assertions.assertEquals(listOf("10.0.0.1"), captor.firstValue.targets)
    }

    // ─── findByModuleNameAndLimitDimensionAndResource ─────────────────────────────

    @Test
    fun `findByModuleNameAndLimitDimensionAndResource — delegates to repository`() {
        val modules = listOf("generic", "npm")
        whenever(
            repo.findByModuleNameAndLimitDimensionAndResource("/proj/repo", modules, "URL")
        ).thenReturn(sampleEntity)

        val result = svc.findByModuleNameAndLimitDimensionAndResource("/proj/repo", modules, "URL")

        verify(repo).findByModuleNameAndLimitDimensionAndResource("/proj/repo", modules, "URL")
        Assertions.assertNotNull(result)
    }
}
