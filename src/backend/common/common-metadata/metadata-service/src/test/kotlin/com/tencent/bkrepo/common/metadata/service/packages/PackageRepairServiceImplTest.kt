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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
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
    private lateinit var packageService: PackageService
    private lateinit var service: PackageRepairServiceImpl

    @BeforeEach
    fun setUp() {
        packageDao = mock()
        packageVersionDao = mock()
        packageService = mock()
        service = PackageRepairServiceImpl(packageService, packageDao, packageVersionDao)
        // 默认 updateFirst 返回“更新成功”的结果，个别用例可自行覆盖
        whenever(packageDao.updateFirst(any(), any())).thenReturn(updateResult(modified = 1))
    }

    @Test
    @DisplayName("指定包，且 latest/historyVersion 完全一致时不触发更新，计入 skipped")
    fun `should skip when metadata already consistent`() {
        val pkg = newPackage(id = "pkg-1", latest = "1.0.0", historyVersion = setOf("1.0.0", "0.9.0"))
        whenever(packageDao.findByKey(PROJECT, REPO, PKG_KEY)).thenReturn(pkg)
        whenever(packageVersionDao.listByPackageId("pkg-1")).thenReturn(
            listOf(newVersion("0.9.0", 1), newVersion("1.0.0", 2))
        )
        whenever(packageVersionDao.findLatest("pkg-1")).thenReturn(newVersion("1.0.0", 2))

        val result = service.repairPackageMetadata(PROJECT, REPO, PKG_KEY)

        assertEquals(1, result.total)
        assertEquals(0, result.updated)
        assertEquals(1, result.skipped)
        assertEquals(0, result.failed)
        assertTrue(result.failedItems.isEmpty())
        verify(packageDao, never()).updateFirst(any(), any())
    }

    @Test
    @DisplayName("指定包，historyVersion 存在脏数据时应全量覆盖并同步 latest")
    fun `should update when history version contains dirty data`() {
        // 元数据里 historyVersion 多了一个 999.0.0 脏数据，latest 也过期
        val pkg = newPackage(
            id = "pkg-2",
            latest = "0.9.0",
            historyVersion = setOf("0.9.0", "1.0.0", "999.0.0")
        )
        whenever(packageDao.findByKey(PROJECT, REPO, PKG_KEY)).thenReturn(pkg)
        whenever(packageVersionDao.listByPackageId("pkg-2")).thenReturn(
            listOf(newVersion("0.9.0", 1), newVersion("1.0.0", 2))
        )
        whenever(packageVersionDao.findLatest("pkg-2")).thenReturn(newVersion("1.0.0", 2))

        val result = service.repairPackageMetadata(PROJECT, REPO, PKG_KEY)

        assertEquals(1, result.total)
        assertEquals(1, result.updated)
        assertEquals(0, result.skipped)
        assertEquals(0, result.failed)

        // 校验实际下发的 Update 语义：historyVersion 覆盖为实际集合，latest 修正为 1.0.0
        val updateCaptor = argumentCaptor<Update>()
        verify(packageDao).updateFirst(any(), updateCaptor.capture())
        val setDoc = updateCaptor.firstValue.updateObject["\$set"] as org.bson.Document
        @Suppress("UNCHECKED_CAST")
        assertEquals(setOf("0.9.0", "1.0.0"), (setDoc["historyVersion"] as Collection<String>).toSet())
        assertEquals("1.0.0", setDoc["latest"])
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
    @DisplayName("包无任何版本时，latest 应置为 null 且 historyVersion 置为空集")
    fun `should set latest null and history empty when no versions`() {
        val pkg = newPackage(id = "pkg-3", latest = "1.0.0", historyVersion = setOf("1.0.0"))
        whenever(packageDao.findByKey(PROJECT, REPO, PKG_KEY)).thenReturn(pkg)
        whenever(packageVersionDao.listByPackageId("pkg-3")).thenReturn(emptyList())
        whenever(packageVersionDao.findLatest("pkg-3")).thenReturn(null)

        val result = service.repairPackageMetadata(PROJECT, REPO, PKG_KEY)

        assertEquals(1, result.total)
        assertEquals(1, result.updated)

        val updateCaptor = argumentCaptor<Update>()
        verify(packageDao).updateFirst(any(), updateCaptor.capture())
        val setDoc = updateCaptor.firstValue.updateObject["\$set"] as org.bson.Document
        assertTrue((setDoc["historyVersion"] as Collection<*>).isEmpty())
        assertNull(setDoc["latest"])
    }

    @Test
    @DisplayName("未指定 packageKey 时全仓库遍历，异常包记入失败明细，其余包按预期分类")
    fun `should collect failed items when iterating repo and one package fails`() {
        // 三个 package：pkg-a 需要更新，pkg-b 已一致（skipped），pkg-c 处理时抛异常
        val pkgA = newPackage(id = "id-a", key = "a", latest = "old", historyVersion = setOf("old"))
        val pkgB = newPackage(id = "id-b", key = "b", latest = "1.0.0", historyVersion = setOf("1.0.0"))
        val pkgC = newPackage(id = "id-c", key = "c", latest = "1.0.0", historyVersion = setOf("1.0.0"))

        // 分页遍历：第 1 页返回 3 条（<REPAIR_PAGE_SIZE 会自动终止循环）
        val pages = listOf(listOf(pkgA, pkgB, pkgC), emptyList())
        val callIndex = intArrayOf(0)
        whenever(packageDao.count(any<Query>())).thenReturn(3L)
        doAnswer { pages[minOf(callIndex[0]++, pages.lastIndex)] }
            .whenever(packageDao).find(any<Query>())

        // pkg-a 需要更新
        whenever(packageVersionDao.listByPackageId("id-a")).thenReturn(listOf(newVersion("1.0.0", 1)))
        whenever(packageVersionDao.findLatest("id-a")).thenReturn(newVersion("1.0.0", 1))

        // pkg-b 已一致
        whenever(packageVersionDao.listByPackageId("id-b")).thenReturn(listOf(newVersion("1.0.0", 1)))
        whenever(packageVersionDao.findLatest("id-b")).thenReturn(newVersion("1.0.0", 1))

        // pkg-c 抛异常
        whenever(packageVersionDao.listByPackageId("id-c"))
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
