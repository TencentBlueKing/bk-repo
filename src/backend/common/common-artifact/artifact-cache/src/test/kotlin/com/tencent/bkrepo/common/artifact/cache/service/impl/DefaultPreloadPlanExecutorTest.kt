/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.artifact.cache.service.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.cache.UT_PROJECT_ID
import com.tencent.bkrepo.common.artifact.cache.UT_REPO_NAME
import com.tencent.bkrepo.common.artifact.cache.UT_SHA256
import com.tencent.bkrepo.common.artifact.cache.config.ArtifactPreloadProperties
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlan
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.core.cache.CacheStorageService
import com.tencent.bkrepo.common.storage.core.locator.FileLocator
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitorHelper
import com.tencent.bkrepo.common.storage.util.createFile
import com.tencent.bkrepo.common.storage.util.delete
import com.tencent.bkrepo.common.storage.util.existReal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDateTime
import kotlin.contracts.ExperimentalContracts

@DisplayName("预加载计划执行器测试")
@ExperimentalContracts
class DefaultPreloadPlanExecutorTest @Autowired constructor(
    properties: ArtifactPreloadProperties,
    storageService: StorageService,
    fileLocator: FileLocator,
    storageProperties: StorageProperties,
    private val preloadPlanExecutor: DefaultPreloadPlanExecutor,
    private val monitorHelper: StorageHealthMonitorHelper,
) : ArtifactPreloadBaseServiceTest(properties, storageService, fileLocator, storageProperties) {

    private lateinit var artifactFile: ArtifactFile

    @BeforeEach
    fun beforeEach() {
        resetMock()
        artifactFile = createTempArtifactFile(1024 * 1024)
        storageService.store(UT_SHA256, artifactFile, null)
        // 等待文件异步保存完成
        Thread.sleep(1000L)
    }

    @AfterEach
    fun afterEach() {
        artifactFile.delete()
        storageService.delete(UT_SHA256, null)
    }

    @Test
    fun testCacheUnhealthy() {
        require(storageService is CacheStorageService)
        monitorHelper.getMonitor(storageProperties, storageProperties.defaultStorageCredentials()).healthy.set(false)
        Assertions.assertFalse(preloadPlanExecutor.execute(buildPlan()))
        monitorHelper.getMonitor(storageProperties, storageProperties.defaultStorageCredentials()).healthy.set(true)
    }

    @Test
    fun testPlanTimeout() {
        val cacheFile = deleteCache(storageProperties.defaultStorageCredentials(), UT_SHA256).toFile()

        // 加载缓存成功
        assertTrue(preloadPlanExecutor.execute(buildPlan()))
        Thread.sleep(1000L)
        assertTrue(cacheFile.exists())
        deleteCache(storageProperties.defaultStorageCredentials(), UT_SHA256)
        Assertions.assertFalse(cacheFile.exists())

        // 计划超时，加载失败
        properties.planTimeout = Duration.ofSeconds(1L)
        val plan = buildPlan(executeTime = System.currentTimeMillis() - 2000)
        assertTrue(preloadPlanExecutor.execute(plan))
        Thread.sleep(1000L)
        Assertions.assertFalse(cacheFile.exists())
        properties.planTimeout = Duration.ofHours(1L)
    }

    @Test
    fun testExecutorFull() {
        properties.preloadConcurrency = 1
        val plan = buildPlan()
        assertTrue(preloadPlanExecutor.execute(plan))
        preloadPlanExecutor.execute(plan)
        preloadPlanExecutor.execute(plan)
        Assertions.assertFalse(preloadPlanExecutor.execute(plan))
        properties.preloadConcurrency = 8
    }

    @Test
    fun testLoadCacheSuccess() {
        require(storageService is CacheStorageService)
        val cacheRootPath = storageProperties.defaultStorageCredentials().cache.path
        val path = fileLocator.locate(UT_SHA256)
        val cachePath = Paths.get(cacheRootPath, path, UT_SHA256)
        var mtime = Files.getLastModifiedTime(cachePath)

        // 缓存已经存在,仅更新mtime
        assertTrue(preloadPlanExecutor.execute(buildPlan()))
        Thread.sleep(1000L)
        assertTrue(Files.getLastModifiedTime(cachePath) > mtime)
        mtime = Files.getLastModifiedTime(cachePath)

        // 确认缓存被删除
        deleteCache(storageProperties.defaultStorageCredentials(), UT_SHA256)

        // 缓存锁文件已存在，跳过预加载
        val cacheFileLock = Paths.get(cacheRootPath, StringPool.TEMP, "$UT_SHA256.locked")
        cacheFileLock.createFile()
        assertTrue(preloadPlanExecutor.execute(buildPlan()))
        Thread.sleep(1000L)
        cacheFileLock.delete()

        // 加载缓存
        preloadPlanExecutor.execute(buildPlan())
        Thread.sleep(1000L)
        assertTrue(cachePath.existReal())
    }

    private fun buildPlan(executeTime: Long = System.currentTimeMillis() - 1000) = ArtifactPreloadPlan(
        id = "xxx",
        createdDate = LocalDateTime.now(),
        lastModifiedDate = LocalDateTime.now(),
        strategyId = "xxx",
        projectId = UT_PROJECT_ID,
        repoName = UT_REPO_NAME,
        fullPath = "/a/b/c.txt",
        sha256 = UT_SHA256,
        size = 1000L,
        credentialsKey = null,
        executeTime = executeTime,
    )

    private fun resetMock() {
        val defaultCredentials = storageProperties.defaultStorageCredentials() as FileSystemCredentials
        whenever(storageCredentialsClient.findByKey(anyString())).thenReturn(
            Response(0, null, defaultCredentials.copy(key = "test"))
        )
    }
}
