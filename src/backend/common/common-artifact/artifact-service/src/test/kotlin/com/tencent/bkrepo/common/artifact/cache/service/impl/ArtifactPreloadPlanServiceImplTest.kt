package com.tencent.bkrepo.common.artifact.cache.service.impl

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.cache.UT_PROJECT_ID
import com.tencent.bkrepo.common.artifact.cache.UT_REPO_NAME
import com.tencent.bkrepo.common.artifact.cache.UT_SHA256
import com.tencent.bkrepo.common.artifact.cache.UT_USER
import com.tencent.bkrepo.common.artifact.cache.config.ArtifactPreloadConfiguration
import com.tencent.bkrepo.common.artifact.cache.config.ArtifactPreloadProperties
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlan
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadStrategyCreateRequest
import com.tencent.bkrepo.common.artifact.cache.pojo.PreloadStrategyType
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.TestPropertySource
import org.springframework.util.unit.DataSize
import java.time.LocalDateTime

@DisplayName("预加载执行计划服务类测试")
@DataMongoTest
@ImportAutoConfiguration(ArtifactPreloadConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties", "classpath:application-test.yaml"])
class ArtifactPreloadPlanServiceImplTest @Autowired constructor(
    private val properties: ArtifactPreloadProperties,
    private val preloadStrategyService: ArtifactPreloadStrategyServiceImpl,
    private val preloadPlanService: ArtifactPreloadPlanServiceImpl,
) {

    @MockBean
    lateinit var repositoryClient: RepositoryClient

    @MockBean
    lateinit var nodeClient: NodeClient

    @BeforeAll
    fun before() {
        properties.enabled = true
        resetMock()
        createStrategy()
    }

    @BeforeEach
    fun beforeEach() {
        resetMock()
    }

    @Test
    fun testCreatePlan() {
        // test repo credentials not match
        val repoName2 = "${UT_REPO_NAME}2"
        resetMock(repoName = repoName2)
        preloadPlanService.createPlan("other", UT_SHA256)
        var plans = preloadPlanService.plans(UT_PROJECT_ID, repoName2, Pages.ofRequest(0, 10)).records
        assertEquals(0, plans.size)

        // test success create
        resetMock()
        val node = buildNodeInfo()
        val nodes = listOf(
            // match path
            node.copy(fullPath = "test.exe"),
            // match create date
            node.copy(createdDate = LocalDateTime.now().minusDays(1L).toString()),
            // success create
            node.copy(fullPath = "test.txt"),
            node.copy(fullPath = "test2.txt"),
        )
        whenever(nodeClient.listPageNodeBySha256(anyString(), any())).thenReturn(
            Response(
                0,
                "",
                Pages.ofResponse(Pages.ofRequest(0, 2000), nodes.size.toLong(), nodes)
            )
        )

        preloadPlanService.createPlan(null, UT_SHA256)
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
        preloadPlanService.createPlan(null, UT_SHA256)
        var plans = preloadPlanService.plans(UT_PROJECT_ID, UT_REPO_NAME, Pages.ofRequest(0, 10)).records
        assertEquals(1, plans.size)
        preloadPlanService.deletePlan(UT_PROJECT_ID, UT_REPO_NAME, plans[0].id!!)
        plans = preloadPlanService.plans(UT_PROJECT_ID, UT_REPO_NAME, Pages.ofRequest(0, 10)).records
        assertEquals(0, plans.size)

        // delete all
        preloadPlanService.createPlan(null, UT_SHA256)
        preloadPlanService.createPlan(null, UT_SHA256)
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
        whenever(nodeClient.listPageNodeBySha256(anyString(), any())).thenReturn(
            Response(
                0,
                "",
                Pages.ofResponse(Pages.ofRequest(0, 2000), nodes.size.toLong(), nodes)
            )
        )
        assertThrows<RuntimeException> { preloadPlanService.createPlan(null, UT_SHA256) }
    }

    private fun resetMock(projectId: String = UT_PROJECT_ID, repoName: String = UT_REPO_NAME) {
        whenever(repositoryClient.getRepoInfo(anyString(), anyString())).thenReturn(
            Response(0, "", buildRepo(projectId = projectId, repoName = repoName))
        )
        val nodes = listOf(buildNodeInfo(projectId, repoName))
        whenever(nodeClient.listPageNodeBySha256(anyString(), any())).thenReturn(
            Response(0, "", Pages.ofResponse(Pages.ofRequest(0, 20), 1L, nodes))
        )
    }

    private fun createStrategy() {
        val request = ArtifactPreloadStrategyCreateRequest(
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            fullPathRegex = ".*\\.txt",
            recentSeconds = 3600L,
            preloadCron = "0 0 0 * * ?",
            type = PreloadStrategyType.CUSTOM.name
        )
        preloadStrategyService.create(request)
        preloadStrategyService.create(request.copy(projectId = "${UT_PROJECT_ID}2"))
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
        used = null,
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
}
