/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 */

package com.tencent.bkrepo.common.metadata.service.packages

import com.mongodb.client.result.UpdateResult
import com.tencent.bkrepo.common.artifact.exception.PackageNotFoundException
import com.tencent.bkrepo.common.metadata.dao.packages.PackageDao
import com.tencent.bkrepo.common.metadata.dao.packages.PackageVersionDao
import com.tencent.bkrepo.common.metadata.model.TPackage
import com.tencent.bkrepo.common.metadata.model.TPackageVersion
import com.tencent.bkrepo.common.metadata.service.packages.impl.PackageRepairServiceImpl
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import org.bson.BsonValue
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.time.LocalDateTime

@DisplayName("Package 元数据修复服务测试")
class PackageRepairServiceImplTest {

    private lateinit var packageDao: PackageDao
    private lateinit var packageVersionDao: PackageVersionDao
    private lateinit var service: PackageRepairServiceImpl

    @BeforeEach
    fun setUp() {
        packageDao = mock()
        packageVersionDao = mock()
        service = PackageRepairServiceImpl(packageDao, packageVersionDao)
        // 默认 latest 修复走的 updateFirst 返回成功；
        // historyVersion 的追加/移除走 DAO 语义方法，默认返回 true 表示有写入。
        whenever(packageDao.updateFirst(any(), any())).thenReturn(updateResult(modified = 1))
        whenever(packageDao.appendHistoryVersions(any(), any())).thenReturn(true)
        whenever(packageDao.removeHistoryVersions(any(), any())).thenReturn(true)
    }

    @Test
    @DisplayName("指定包，且 latest/historyVersion 完全一致时不触发更新，计入 skipped")
    fun `should skip when metadata already consistent`() {
        val pkg = newPackage(id = "pkg-1", latest = "1.0.0", historyVersion = setOf("1.0.0", "0.9.0"))
        whenever(packageDao.findByKey(PROJECT, REPO, PKG_KEY)).thenReturn(pkg)
        mockVersionNames("pkg-1", listOf("0.9.0", "1.0.0"))
        whenever(packageVersionDao.findLatest("pkg-1")).thenReturn(newVersion("1.0.0", 2))

        val result = service.repairPackageMetadata(PROJECT, REPO, PKG_KEY)

        assertEquals(1, result.total)
        assertEquals(0, result.updated)
        assertEquals(1, result.skipped)
        assertEquals(0, result.failed)
        assertTrue(result.failedItems.isEmpty())
        // 已一致时：不会触发 latest updateFirst，也不会调用 append/remove
        verify(packageDao, never()).updateFirst(any(), any())
        verify(packageDao, never()).appendHistoryVersions(any(), any())
        verify(packageDao, never()).removeHistoryVersions(any(), any())
    }

    @Test
    @DisplayName("指定包，historyVersion 存在脏数据时应分批修正")
    fun `should update when history version contains dirty data`() {
        // 元数据里 historyVersion 多了一个 999.0.0 脏数据，缺少 1.0.0，latest 也过期
        val pkg = newPackage(
            id = "pkg-2",
            latest = "0.9.0",
            historyVersion = setOf("0.9.0", "999.0.0")
        )
        whenever(packageDao.findByKey(PROJECT, REPO, PKG_KEY)).thenReturn(pkg)
        // 实际 package_version 中的版本：0.9.0 + 1.0.0
        mockVersionNames("pkg-2", listOf("0.9.0", "1.0.0"))
        whenever(packageVersionDao.findLatest("pkg-2")).thenReturn(newVersion("1.0.0", 2))

        val result = service.repairPackageMetadata(PROJECT, REPO, PKG_KEY)

        assertEquals(1, result.total)
        assertEquals(1, result.updated)
        assertEquals(0, result.skipped)
        assertEquals(0, result.failed)

        // latest 修复途径仍然走 updateFirst，验证写入值为真正的 latest "1.0.0"
        val updateCaptor = argumentCaptor<Update>()
        verify(packageDao, org.mockito.kotlin.atLeast(1))
            .updateFirst(any(), updateCaptor.capture())
        val latestUpdate = updateCaptor.allValues.firstOrNull {
            (it.updateObject["\$set"] as? Document)?.containsKey("latest") == true
        }
        assertEquals("1.0.0", (latestUpdate?.updateObject?.get("\$set") as? Document)?.get("latest"))

        // historyVersion 追加：验证 append 调用参数集合中包含 1.0.0
        val appendCaptor = argumentCaptor<Collection<String>>()
        verify(packageDao, org.mockito.kotlin.atLeast(1))
            .appendHistoryVersions(eq("pkg-2"), appendCaptor.capture())
        assertTrue(appendCaptor.allValues.any { "1.0.0" in it })

        // historyVersion 移除：验证 remove 调用参数集合中包含 999.0.0
        val removeCaptor = argumentCaptor<Collection<String>>()
        verify(packageDao, org.mockito.kotlin.atLeast(1))
            .removeHistoryVersions(eq("pkg-2"), removeCaptor.capture())
        assertTrue(removeCaptor.allValues.any { "999.0.0" in it })
    }

    @Test
    @DisplayName("指定包但不存在时抛 PackageNotFoundException，不下发更新")
    fun `should throw when specified package not found`() {
        whenever(packageDao.findByKey(PROJECT, REPO, "not-exist")).thenReturn(null)

        assertThrows<PackageNotFoundException> {
            service.repairPackageMetadata(PROJECT, REPO, "not-exist")
        }
        verify(packageDao, never()).updateFirst(any(), any())
    }

    @Test
    @DisplayName("包无任何版本时，latest 应置为 null 且 historyVersion 内脏数据被清空")
    fun `should set latest null and pull all when no versions`() {
        val pkg = newPackage(id = "pkg-3", latest = "1.0.0", historyVersion = setOf("1.0.0"))
        whenever(packageDao.findByKey(PROJECT, REPO, PKG_KEY)).thenReturn(pkg)
        mockVersionNames("pkg-3", emptyList())
        whenever(packageVersionDao.findLatest("pkg-3")).thenReturn(null)

        val result = service.repairPackageMetadata(PROJECT, REPO, PKG_KEY)

        assertEquals(1, result.total)
        assertEquals(1, result.updated)

        // latest 应被 $set 为 null
        val updateCaptor = argumentCaptor<Update>()
        verify(packageDao, org.mockito.kotlin.atLeast(1))
            .updateFirst(any(), updateCaptor.capture())
        val latestUpdate = updateCaptor.allValues.firstOrNull {
            (it.updateObject["\$set"] as? Document)?.containsKey("latest") == true
        }
        assertNull((latestUpdate?.updateObject?.get("\$set") as? Document)?.get("latest"))

        // 原有 historyVersion 中的 1.0.0 应被移除
        val removeCaptor = argumentCaptor<Collection<String>>()
        verify(packageDao, org.mockito.kotlin.atLeast(1))
            .removeHistoryVersions(eq("pkg-3"), removeCaptor.capture())
        assertTrue(removeCaptor.allValues.any { "1.0.0" in it })
    }

    @Test
    @DisplayName("大包（10w 版本）且元数据一致时，无前置短路依旧不触发 updateFirst")
    fun `should skip large package without triggering updates`() {
        val largeVersions = (1..100_000).mapTo(LinkedHashSet()) { "v-$it" }
        val pkg = newPackage(id = "large-pkg", latest = "v-100000", historyVersion = largeVersions)
        whenever(packageDao.findByKey(PROJECT, REPO, PKG_KEY)).thenReturn(pkg)
        mockVersionNames("large-pkg", largeVersions.toList())
        whenever(packageVersionDao.findLatest("large-pkg"))
            .thenReturn(newVersion("v-100000", 100_000))

        val result = service.repairPackageMetadata(PROJECT, REPO, PKG_KEY)

        assertEquals(1, result.total)
        assertEquals(1, result.skipped)
        assertEquals(0, result.updated)
        // 已一致时不会触发任何 latest / append / remove 写入
        verify(packageDao, never()).updateFirst(any(), any())
        verify(packageDao, never()).appendHistoryVersions(any(), any())
        verify(packageDao, never()).removeHistoryVersions(any(), any())
    }

    @Test
    @DisplayName("未指定 packageKey 时全仓库遍历，异常包记入失败明细，其余包按预期分类")
    fun `should collect failed items when iterating repo and one package fails`() {
        // 三个 package：pkg-a 需要更新（latest 不一致），pkg-b 已一致（skipped），pkg-c 抛异常
        val pkgA = newPackage(id = "id-a", key = "a", latest = "old", historyVersion = setOf("1.0.0"))
        val pkgB = newPackage(id = "id-b", key = "b", latest = "1.0.0", historyVersion = setOf("1.0.0"))
        val pkgC = newPackage(id = "id-c", key = "c", latest = "1.0.0", historyVersion = setOf("1.0.0"))

        // 分页遍历：第 1 页返回 3 条（<REPAIR_PAGE_SIZE 会自动终止循环）
        val pages = listOf(listOf(pkgA, pkgB, pkgC), emptyList())
        val callIndex = intArrayOf(0)
        whenever(packageDao.count(any<Query>())).thenReturn(3L)
        doAnswer { pages[minOf(callIndex[0]++, pages.lastIndex)] }
            .whenever(packageDao).find(any<Query>())

        // pkg-a 需要更新（latest 不一致就会触发）
        mockVersionNames("id-a", listOf("1.0.0"))
        whenever(packageVersionDao.findLatest("id-a")).thenReturn(newVersion("1.0.0", 1))

        // pkg-b 已一致
        mockVersionNames("id-b", listOf("1.0.0"))
        whenever(packageVersionDao.findLatest("id-b")).thenReturn(newVersion("1.0.0", 1))

        // pkg-c 抛异常：在 findLatest 阶段就抛，无需 mock 后续方法
        whenever(packageVersionDao.findLatest("id-c"))
            .thenThrow(IllegalStateException("boom"))

        val result = service.repairPackageMetadata(PROJECT, REPO, packageKey = null)

        assertEquals(3, result.total)
        assertEquals(1, result.updated)
        assertEquals(1, result.skipped)
        assertEquals(1, result.failed)
        assertEquals(1, result.failedItems.size)
        assertEquals("c", result.failedItems.single().packageKey)
        assertEquals("boom", result.failedItems.single().reason)
    }

    @Test
    @DisplayName("空 packageKey 视为未指定，走仓库遍历路径")
    fun `should treat blank package key as null and iterate repo`() {
        // 仓库内空，应直接返回 total=0
        whenever(packageDao.count(any<Query>())).thenReturn(0L)
        whenever(packageDao.find(any<Query>())).thenReturn(emptyList())

        val result = service.repairPackageMetadata(PROJECT, REPO, packageKey = "   ")

        assertEquals(0, result.total)
        assertEquals(0, result.updated)
        assertEquals(0, result.skipped)
        assertEquals(0, result.failed)
        // 未走单包路径
        verify(packageDao, never()).findByKey(eq(PROJECT), eq(REPO), any())
    }

    // ------------------------------ helpers ------------------------------

    /**
     * 统一 stub 版本 DAO 的三个查询：countVersion / pageVersionNamesAfterId / findExistingNames。
     *
     * 传入的 [actualNames] 代表 Mongo 侧真实存在的版本名列表。
     * - `pageVersionNamesAfterId(pkgId, lastId, size)` 按 (index+1) 作为伪 id 排序，
     *   按 [batchSize] 真实分批返回，贴近生产侧游标分页行为；避免大数据集下把整份数据当作一批返回。
     * - `findExistingNames(pkgId, names)` 返回 names 与 actualNames 的交集；
     *   actualNames 预先转成 [HashSet]，将 contains 从 List 的 O(N) 降到 O(1)，避免 10w 场景下 O(N²) 退化。
     */
    private fun mockVersionNames(packageId: String, actualNames: List<String>) {
        whenever(packageVersionDao.countVersion(packageId)).thenReturn(actualNames.size.toLong())
        // 伪 id 用 4 位左填 0，保证按字符串比较结果与索引顺序一致
        val pairs = actualNames.mapIndexed { idx, name -> "id-%08d".format(idx) to name }
        val actualSet = actualNames.toHashSet()
        whenever(packageVersionDao.pageVersionNamesAfterId(eq(packageId), anyOrNull(), any()))
            .thenAnswer { invocation ->
                val lastId = invocation.arguments[1] as String?
                val batchSize = invocation.arguments[2] as Int
                val startIndex =
                    if (lastId == null) 0 else pairs.indexOfFirst { it.first > lastId }
                if (startIndex < 0) emptyList<Pair<String, String>>()
                else pairs.subList(startIndex, minOf(startIndex + batchSize, pairs.size))
            }
        whenever(packageVersionDao.findExistingNames(eq(packageId), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val names = invocation.arguments[1] as Collection<String>
            names.filterTo(HashSet(names.size)) { it in actualSet }
        }
    }

    private fun newPackage(
        id: String,
        key: String = PKG_KEY,
        latest: String?,
        historyVersion: Set<String>
    ): TPackage {
        val now = LocalDateTime.now()
        return TPackage(
            id = id,
            createdBy = "ut",
            createdDate = now,
            lastModifiedBy = "ut",
            lastModifiedDate = now,
            projectId = PROJECT,
            repoName = REPO,
            name = key,
            key = key,
            type = PackageType.MAVEN,
            latest = latest,
            downloads = 0,
            versions = historyVersion.size.toLong(),
            historyVersion = historyVersion
        )
    }

    private fun newVersion(name: String, ordinal: Long): TPackageVersion {
        val now = LocalDateTime.now()
        return TPackageVersion(
            id = "v-$name",
            createdBy = "ut",
            createdDate = now,
            lastModifiedBy = "ut",
            lastModifiedDate = now,
            packageId = "",
            name = name,
            size = 0,
            ordinal = ordinal,
            downloads = 0,
            stageTag = emptyList(),
            metadata = emptyList()
        )
    }

    private fun updateResult(modified: Long): UpdateResult = object : UpdateResult() {
        override fun wasAcknowledged(): Boolean = true
        override fun getMatchedCount(): Long = modified
        override fun getModifiedCount(): Long = modified
        override fun getUpsertedId(): BsonValue? = null
    }

    companion object {
        private const val PROJECT = "test-project"
        private const val REPO = "test-repo"
        private const val PKG_KEY = "com.tencent.bkrepo:demo"
    }
}
