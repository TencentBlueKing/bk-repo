package com.tencent.bkrepo.common.metadata.service.separation.impl

import com.tencent.bkrepo.common.metadata.dao.separation.SeparationNodeDao
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationPackageDao
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationPackageVersionDao
import com.tencent.bkrepo.common.metadata.model.TSeparationNode
import com.tencent.bkrepo.common.metadata.service.separation.SeparationTaskService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.time.LocalDateTime
import java.util.stream.Stream

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SeparationDataServiceImpl 冷数据查询")
class SeparationDataServiceImplTest {

    @Mock
    private lateinit var separationPackageVersionDao: SeparationPackageVersionDao

    @Mock
    private lateinit var separationPackageDao: SeparationPackageDao

    @Mock
    private lateinit var separationNodeDao: SeparationNodeDao

    @Mock
    private lateinit var separationTaskService: SeparationTaskService

    private lateinit var service: SeparationDataServiceImpl

    private val dOld = LocalDateTime.of(2024, 1, 1, 0, 0)
    private val dNew = LocalDateTime.of(2024, 6, 1, 0, 0)
    private val now = LocalDateTime.of(2024, 1, 15, 12, 0)

    @BeforeEach
    fun setup() {
        service = SeparationDataServiceImpl(
            separationPackageVersionDao,
            separationPackageDao,
            separationNodeDao,
            separationTaskService,
        )
    }

    @Test
    @DisplayName("findNodeInfo 按降冷日期倒序命中最近分表")
    fun findNodeInfo_prefersLatestSeparationDateShard() {
        whenever(separationTaskService.findDistinctSeparationDate("p", "r")).thenReturn(setOf(dOld, dNew))
        val nodeNew = coldNode(dNew, "/a.txt")
        val nodeOld = coldNode(dOld, "/a.txt")
        whenever(separationNodeDao.findOne(any())).thenReturn(null, nodeOld)

        val info = service.findNodeInfo("p", "r", "/a.txt")
        assertNotNull(info)
        assertEquals("/a.txt", info!!.fullPath)
        verify(separationNodeDao, times(2)).findOne(any())
    }

    @Test
    @DisplayName("findNodeInfo 无降冷日期时返回 null")
    fun findNodeInfo_noDates_returnsNull() {
        whenever(separationTaskService.findDistinctSeparationDate("p", "r")).thenReturn(emptySet())
        assertNull(service.findNodeInfo("p", "r", "/x"))
    }

    @Test
    @DisplayName("countColdNodes 对多分表计数求和")
    fun countColdNodes_sumsAcrossShards() {
        baseQuery()
        whenever(separationTaskService.findDistinctSeparationDate("p", "r")).thenReturn(setOf(dOld, dNew))
        whenever(separationNodeDao.countByQuery(any(), eq(dOld))).thenReturn(3L)
        whenever(separationNodeDao.countByQuery(any(), eq(dNew))).thenReturn(7L)
        assertEquals(10L, service.countColdNodes(baseQuery()))
    }

    @Test
    @DisplayName("countColdNodes 无 project/repo 等值时仍查任务表（可能返回空集合）")
    fun countColdNodes_fallbackDatesFromTaskService() {
        val q = Query()
        whenever(separationTaskService.findDistinctSeparationDate(null, null)).thenReturn(emptySet())
        assertEquals(0L, service.countColdNodes(q))
    }

    @Test
    @DisplayName("searchColdNodes limit<=0 直接空列表")
    fun searchColdNodes_nonPositiveLimit_empty() {
        assertTrue(service.searchColdNodes(baseQuery(), 0, 0).isEmpty())
        verifyNoMoreInteractions(separationNodeDao)
    }

    @Test
    @DisplayName("hasColdNodeUnderPath 任一分表存在即 true")
    fun hasColdNodeUnderPath_trueWhenAnyShardExists() {
        baseQuery()
        whenever(separationTaskService.findDistinctSeparationDate("p", "r")).thenReturn(setOf(dOld))
        whenever(separationNodeDao.exists(any())).thenReturn(true)
        assertTrue(service.hasColdNodeUnderPath("p", "r", "/prefix/"))
    }

    @Test
    @DisplayName("hasColdNodeUnderPath 全部分表无节点时 false")
    fun hasColdNodeUnderPath_falseWhenNoShard() {
        baseQuery()
        whenever(separationTaskService.findDistinctSeparationDate("p", "r")).thenReturn(setOf(dOld))
        whenever(separationNodeDao.exists(any())).thenReturn(false)
        assertFalse(service.hasColdNodeUnderPath("p", "r", "/x/"))
    }

    @Test
    @DisplayName("searchColdNodes 跨分表 skip 耗尽后读下一分表")
    fun searchColdNodes_skipSpansShards() {
        baseQuery()
        whenever(separationTaskService.findDistinctSeparationDate("p", "r")).thenReturn(setOf(dOld, dNew))
        whenever(separationNodeDao.streamByQuery(any(), eq(dNew))).thenReturn(
            Stream.of(mutableMapOf("i" to 0), mutableMapOf("i" to 1)),
        )
        val row = mutableMapOf<String, Any?>("fullPath" to "/z")
        whenever(separationNodeDao.streamByQuery(any(), eq(dOld))).thenReturn(
            Stream.of(mutableMapOf("i" to 2), row),
        )

        val out = service.searchColdNodes(baseQuery(), skip = 3, limit = 10)
        assertEquals(1, out.size)
        assertEquals("/z", out[0]["fullPath"])
    }

    private fun baseQuery(): Query = Query.query(
        Criteria.where(TSeparationNode::projectId.name).`is`("p")
            .and(TSeparationNode::repoName.name).`is`("r"),
    )

    private fun coldNode(separationDate: LocalDateTime, fullPath: String) = TSeparationNode(
        id = "id1",
        createdBy = "u",
        createdDate = now,
        lastModifiedBy = "u",
        lastModifiedDate = now,
        folder = false,
        path = "/",
        name = fullPath.trimStart('/'),
        fullPath = fullPath,
        size = 1L,
        sha256 = "s",
        projectId = "p",
        repoName = "r",
    ).apply {
        this.separationDate = separationDate
    }
}
