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
import com.tencent.bkrepo.common.artifact.api.FileSystemArtifactFile
import com.tencent.bkrepo.common.artifact.cache.UT_PROJECT_ID
import com.tencent.bkrepo.common.artifact.cache.UT_REPO_NAME
import com.tencent.bkrepo.common.artifact.cache.UT_SHA256
import com.tencent.bkrepo.common.artifact.cache.config.ArtifactPreloadConfiguration
import com.tencent.bkrepo.common.artifact.cache.config.ArtifactPreloadProperties
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlan
import com.tencent.bkrepo.common.storage.StorageAutoConfiguration
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.core.cache.CacheStorageService
import com.tencent.bkrepo.common.storage.core.locator.FileLocator
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitorHelper
import com.tencent.bkrepo.common.storage.util.existReal
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.TestPropertySource
import java.io.File
import java.nio.file.Paths
import java.time.LocalDateTime

@DisplayName("预加载策略服务测试")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DataMongoTest
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties", "classpath:application-test.properties"])
@ImportAutoConfiguration(
    ArtifactPreloadConfiguration::class, StorageAutoConfiguration::class, TaskExecutionAutoConfiguration::class
)
class DefaultPreloadPlanExecutorTest @Autowired constructor(
    private val preloadPlanExecutor: DefaultPreloadPlanExecutor,
    private val storageService: StorageService,
    private val storageProperties: StorageProperties,
    private val preloadProperties: ArtifactPreloadProperties,
    private val monitorHelper: StorageHealthMonitorHelper,
    private val fileLocator: FileLocator,
) {

    @MockBean
    lateinit var storageCredentialsClient: StorageCredentialsClient

    @BeforeEach
    fun beforeEach() {
        resetMock()
    }

    @AfterEach
    fun afterEach() {
        storageService.delete(UT_SHA256, storageProperties.defaultStorageCredentials())
    }

    @Test
    fun testCacheUnhealthy() {
        require(storageService is CacheStorageService)
        monitorHelper.getMonitor(storageProperties, storageProperties.defaultStorageCredentials()).healthy.set(false)
        Assertions.assertFalse(preloadPlanExecutor.execute(buildPlan()))
        monitorHelper.getMonitor(storageProperties, storageProperties.defaultStorageCredentials()).healthy.set(true)
    }

    @Test
    fun testExecutorFull() {
        preloadProperties.preloadConcurrency = 1
        val plan = buildPlan()
        Assertions.assertTrue(preloadPlanExecutor.execute(plan))
        Assertions.assertFalse(preloadPlanExecutor.execute(plan))
        preloadProperties.preloadConcurrency = 8
    }

    @Test
    fun testLoadCacheSuccess() {
        require(storageService is CacheStorageService)
        // create file
        val artifactFile = createTempArtifactFile(1024 * 1024)
        storageService.store(UT_SHA256, artifactFile, null)

        // 缓存已经存在
        Assertions.assertTrue(preloadPlanExecutor.execute(buildPlan()))

        // 确认缓存被删除
        val path = fileLocator.locate(UT_SHA256)
        val credentials = storageProperties.defaultStorageCredentials()
        storageService.deleteCacheFile(path, UT_SHA256, credentials)
        val cachePath = Paths.get(credentials.cache.path, path, UT_SHA256)
        Assertions.assertFalse(cachePath.existReal())
        Assertions.assertTrue(storageService.exist(UT_SHA256, credentials))

        // 加载缓存
        preloadPlanExecutor.execute(buildPlan())
        Thread.sleep(1000L)
        Assertions.assertTrue(cachePath.existReal())
    }

    private fun createTempArtifactFile(size: Long): ArtifactFile {
        val tempFile = File.createTempFile("preload-", ".tmp")
        val content = StringPool.randomString(size.toInt())
        content.byteInputStream().use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return FileSystemArtifactFile(tempFile)
    }

    private fun buildPlan() = ArtifactPreloadPlan(
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
        executeTime = System.currentTimeMillis() - 1000,
    )

    private fun resetMock() {
        val defaultCredentials = storageProperties.defaultStorageCredentials() as FileSystemCredentials
        whenever(storageCredentialsClient.findByKey(anyString())).thenReturn(
            Response(0, null, defaultCredentials.copy(key = "test"))
        )
    }
}
