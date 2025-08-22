/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

import com.tencent.bkrepo.common.artifact.cache.UT_PROJECT_ID
import com.tencent.bkrepo.common.artifact.cache.UT_REPO_NAME
import com.tencent.bkrepo.common.artifact.cache.UT_SHA256
import com.tencent.bkrepo.common.artifact.cache.UT_USER
import com.tencent.bkrepo.common.artifact.cache.config.ArtifactPreloadProperties
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlan
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlanCreateRequest
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadStrategyCreateRequest
import com.tencent.bkrepo.common.artifact.cache.pojo.PreloadStrategyType
import com.tencent.bkrepo.common.artifact.metrics.ArtifactCacheMetrics
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.core.locator.FileLocator
import com.tencent.bkrepo.common.storage.util.existReal
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.util.unit.DataSize
import java.time.LocalDateTime
import kotlin.contracts.ExperimentalContracts

@DisplayName("预加载执行计划服务类测试")
@ExperimentalContracts
class ArtifactPreloadPlanServiceImplTest @Autowired constructor(
    properties: ArtifactPreloadProperties,
    storageService: StorageService,
    fileLocator: FileLocator,
    storageProperties: StorageProperties,
    private val preloadStrategyService: ArtifactPreloadStrategyServiceImpl,
    private val preloadPlanService: ArtifactPreloadPlanServiceImpl,
) : ArtifactPreloadBaseServiceTest(properties, storageService, fileLocator, storageProperties) {

    @MockitoBean
    private lateinit var artifactCacheMetrics: ArtifactCacheMetrics

    @BeforeAll
    fun before() {
        properties.enabled = true
    }

    @BeforeEach
    fun beforeEach() {
        resetMock()
        createStrategy()
    }

    @AfterEach
    fun afterEach() {
        deleteStrategy()
        deletePlans()
    }

    @Test
    fun testCreatePlan() {
        val node = NodeDetail(buildNodeInfo(UT_PROJECT_ID, UT_REPO_NAME))
        whenever(nodeService.getNodeDetail(any(), anyOrNull())).thenReturn(node)
        val executeTime = System.currentTimeMillis()
        val request = ArtifactPreloadPlanCreateRequest(
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            fullPath = "/a/b/c.txt",
            executeTime = executeTime
        )
        val createdPlan = preloadPlanService.createPlan(request)
        assertEquals(UT_SHA256, createdPlan.sha256)
        assertEquals(null, createdPlan.credentialsKey)
        assertEquals(executeTime, createdPlan.executeTime)
    }

    @Test
    fun testGeneratePlan() {
        // test repo credentials not match
        resetMock(repoName = UT_REPO_NAME2)
        preloadPlanService.generatePlan(OTHER_CREDENTIALS_KEY, UT_SHA256)
        var plans = preloadPlanService.plans(UT_PROJECT_ID, UT_REPO_NAME2, Pages.ofRequest(0, 10)).records
        assertEquals(0, plans.size)

        // test success create
        resetMock()
        val node = buildNodeInfo()
        val nodes = listOf(
            // match path
            node.copy(fullPath = "test.exe"),
            // match size
            node.copy(size = DataSize.ofKilobytes(1L).toBytes()),
            // match create date
            node.copy(createdDate = LocalDateTime.now().minusDays(1L).toString()),
            // success create
            node.copy(fullPath = "test.txt"),
            node.copy(fullPath = "test2.txt"),
        )
        whenever(
            nodeService.listNodeBySha256(anyString(), any(), anyBoolean(), anyBoolean(), anyBoolean())
        ).thenReturn(nodes)

        preloadPlanService.generatePlan(null, UT_SHA256)
        plans = preloadPlanService.plans(UT_PROJECT_ID, UT_REPO_NAME, Pages.ofRequest(0, 10)).records
        assertEquals(2, plans.size)
        val plan = plans[0]
        assertEquals(UT_PROJECT_ID, plan.projectId)
        assertEquals(UT_REPO_NAME, plan.repoName)
        assertEquals(UT_SHA256, plan.sha256)
        assertEquals(DataSize.ofGigabytes(2L).toBytes(), plan.size)
        assertEquals(ArtifactPreloadPlan.STATUS_PENDING, plan.status)
    }

    @Test
    fun testDeletePlan() {
        // delete by id
        preloadPlanService.deletePlan(UT_PROJECT_ID, UT_REPO_NAME)
        preloadPlanService.generatePlan(null, UT_SHA256)
        var plans = preloadPlanService.plans(UT_PROJECT_ID, UT_REPO_NAME, Pages.ofRequest(0, 10)).records
        assertEquals(1, plans.size)
        preloadPlanService.deletePlan(UT_PROJECT_ID, UT_REPO_NAME, plans[0].id!!)
        plans = preloadPlanService.plans(UT_PROJECT_ID, UT_REPO_NAME, Pages.ofRequest(0, 10)).records
        assertEquals(0, plans.size)

        // delete all
        preloadPlanService.generatePlan(null, UT_SHA256)
        preloadPlanService.generatePlan(null, UT_SHA256)
        plans = preloadPlanService.plans(UT_PROJECT_ID, UT_REPO_NAME, Pages.ofRequest(0, 10)).records
        assertEquals(2, plans.size)
        preloadPlanService.deletePlan(UT_PROJECT_ID, UT_REPO_NAME)
        plans = preloadPlanService.plans(UT_PROJECT_ID, UT_REPO_NAME, Pages.ofRequest(0, 10)).records
        assertEquals(0, plans.size)
    }

    @Test
    fun testExceedMaxNodeCount() {
        val nodes = ArrayList<NodeInfo>()
        for (i in 0..1000) {
            nodes.add(buildNodeInfo())
        }
        whenever(
            nodeService.listNodeBySha256(anyString(), any(), anyBoolean(), anyBoolean(), anyBoolean())
        ).thenReturn(nodes)
        preloadPlanService.generatePlan(null, UT_SHA256)
        val plans = preloadPlanService.plans(UT_PROJECT_ID, UT_REPO_NAME, Pages.ofRequest(0, 10)).records
        assertEquals(0, plans.size)
    }

    @Test
    fun testExecutePlans() {
        // create file
        val artifactFile = createTempArtifactFile(1024 * 1024)
        storageService.store(UT_SHA256, artifactFile, null)
        // 等待异步存储完成
        Thread.sleep(1000L)

        // 删除缓存
        val cacheFilePath = deleteCache(storageProperties.defaultStorageCredentials(), UT_SHA256)

        // 创建计划，每秒执行一次预加载
        createStrategy("0/1 * * * * ?")
        preloadPlanService.generatePlan(null, UT_SHA256)
        // 等待到达预加载时间
        Thread.sleep(2000L)

        // 执行计划
        preloadPlanService.executePlans()
        // 等待异步加载完成
        Thread.sleep(1000L)

        // 确认缓存加载成功
        assertTrue(cacheFilePath.existReal())
        storageService.delete(UT_SHA256, storageProperties.defaultStorageCredentials())
        artifactFile.delete()
    }

    private fun resetMock(projectId: String = UT_PROJECT_ID, repoName: String = UT_REPO_NAME) {
        whenever(repositoryService.getRepoInfo(anyString(), anyString(), anyOrNull())).thenReturn(
            buildRepo(projectId = projectId, repoName = repoName)
        )
        val nodes = listOf(buildNodeInfo(projectId, repoName))
        whenever(
            nodeService.listNodeBySha256(anyString(), any(), anyBoolean(), anyBoolean(), anyBoolean())
        ).thenReturn(nodes)
    }

    // 构造测试数据

    private fun createStrategy(cron: String = "0 0 0 * * ?") {
        val request = ArtifactPreloadStrategyCreateRequest(
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            fullPathRegex = ".*\\.txt",
            recentSeconds = 3600L,
            minSize = DataSize.ofMegabytes(1L).toBytes(),
            preloadCron = cron,
            type = PreloadStrategyType.CUSTOM.name
        )
        preloadStrategyService.create(request)
        preloadStrategyService.create(request.copy(projectId = UT_PROJECT_ID2))
    }

    private fun deleteStrategy() {
        preloadStrategyService.delete(UT_PROJECT_ID, UT_REPO_NAME)
        preloadStrategyService.delete(UT_PROJECT_ID, UT_REPO_NAME2)
        preloadStrategyService.delete(UT_PROJECT_ID2, UT_REPO_NAME)
        preloadStrategyService.delete(UT_PROJECT_ID2, UT_REPO_NAME2)
    }

    private fun deletePlans() {
        preloadPlanService.deletePlan(UT_PROJECT_ID, UT_REPO_NAME)
        preloadPlanService.deletePlan(UT_PROJECT_ID, UT_REPO_NAME2)
        preloadPlanService.deletePlan(UT_PROJECT_ID2, UT_REPO_NAME)
        preloadPlanService.deletePlan(UT_PROJECT_ID2, UT_REPO_NAME2)
    }

    private fun buildRepo(projectId: String = UT_PROJECT_ID, repoName: String = UT_REPO_NAME) = RepositoryInfo(
        id = "",
        projectId = projectId,
        name = repoName,
        type = RepositoryType.GENERIC,
        category = RepositoryCategory.LOCAL,
        public = false,
        description = "",
        configuration = LocalConfiguration(),
        storageCredentialsKey = null,
        createdBy = "",
        createdDate = "",
        lastModifiedBy = "",
        lastModifiedDate = "",
        quota = null,
        display = true,
        used = null
    )

    private fun buildNodeInfo(projectId: String = UT_PROJECT_ID, repoName: String = UT_REPO_NAME): NodeInfo {
        val path = "/a/b/c"
        val name = "/d.txt"
        return NodeInfo(
            createdBy = UT_USER,
            createdDate = LocalDateTime.now().toString(),
            lastModifiedBy = UT_USER,
            lastModifiedDate = LocalDateTime.now().toString(),
            lastAccessDate = LocalDateTime.now().toString(),
            folder = false,
            path = path,
            name = name,
            fullPath = "$path/$name",
            size = DataSize.ofGigabytes(2L).toBytes(),
            projectId = projectId,
            repoName = repoName,
            sha256 = UT_SHA256,
            metadata = emptyMap()
        )
    }

    companion object {
        private const val OTHER_CREDENTIALS_KEY = "other"
        private const val UT_REPO_NAME2 = "${UT_REPO_NAME}2"
        private const val UT_PROJECT_ID2 = "${UT_PROJECT_ID}2"
    }
}
