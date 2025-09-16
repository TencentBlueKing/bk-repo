package com.tencent.bkrepo.job.separation.service.impl

import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.job.separation.config.DataSeparationConfig
import com.tencent.bkrepo.job.separation.dao.SeparationFailedRecordDao
import com.tencent.bkrepo.job.separation.dao.SeparationTaskDao
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.TestPropertySource
import java.lang.reflect.Method

@DisplayName("降冷任务服务测试")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class SeparationTaskServiceImplTest {
    private lateinit var dataSeparationConfig: DataSeparationConfig
    private lateinit var repositoryService: RepositoryService
    private lateinit var separationTaskDao: SeparationTaskDao
    private lateinit var separationFailedRecordDao: SeparationFailedRecordDao
    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var matchesConfigReposMethod: Method
    private lateinit var isProjectAllowedForSeparationMethod: Method
    private lateinit var separationTaskService: SeparationTaskServiceImpl

    @BeforeAll
    fun commonMock() {
        dataSeparationConfig = mockk() // 显式初始化 Mock 对象
        repositoryService = mockk()
        separationTaskDao = mockk()
        separationFailedRecordDao = mockk()
        mongoTemplate = mockk()

        separationTaskService = SeparationTaskServiceImpl(
            dataSeparationConfig, repositoryService, separationTaskDao, separationFailedRecordDao, mongoTemplate
        )
        matchesConfigReposMethod = SeparationTaskServiceImpl::class.java.getDeclaredMethod(
            "matchesConfigRepos",
            String::class.java,
            List::class.java
        )
        matchesConfigReposMethod.isAccessible = true

        isProjectAllowedForSeparationMethod = SeparationTaskServiceImpl::class.java.getDeclaredMethod(
            "isProjectAllowedForSeparation",
            String::class.java
        )
        isProjectAllowedForSeparationMethod.isAccessible = true
    }

    /**
     * 调用私有方法matchesConfigRepos的辅助方法
     */
    private fun callMatchesConfigRepos(projectRepoKey: String, configRepos: List<String>): Boolean {
        return matchesConfigReposMethod.invoke(separationTaskService, projectRepoKey, configRepos) as Boolean
    }

    /**
     * 调用私有方法isProjectAllowedForSeparation的辅助方法
     */
    private fun callIsProjectAllowedForSeparation(projectId: String): Boolean {
        return isProjectAllowedForSeparationMethod.invoke(separationTaskService, projectId) as Boolean
    }

    @Test
    @DisplayName("测试精确匹配")
    fun testExactMatch() {
        val configRepos = listOf("project1/repo1", "project2/repo2")

        // 精确匹配
        assertTrue(callMatchesConfigRepos("project1/repo1", configRepos))
        assertTrue(callMatchesConfigRepos("project2/repo2", configRepos))

        // 不匹配
        assertFalse(callMatchesConfigRepos("project1/repo2", configRepos))
        assertFalse(callMatchesConfigRepos("project3/repo1", configRepos))
    }

    @Test
    @DisplayName("测试通配符匹配")
    fun testWildcardMatch() {
        val configRepos = listOf("project1/*", "*/repo2", "*/*")

        // 项目通配符匹配
        assertTrue(callMatchesConfigRepos("project1/repo1", configRepos))
        assertTrue(callMatchesConfigRepos("project1/repo2", configRepos))
        assertTrue(callMatchesConfigRepos("project1/any-repo", configRepos))

        // 仓库通配符匹配
        assertTrue(callMatchesConfigRepos("any-project/repo2", configRepos))
        assertTrue(callMatchesConfigRepos("project1/repo2", configRepos))

        // 全通配符匹配
        assertTrue(callMatchesConfigRepos("any-project/any-repo", configRepos))

        // 不匹配的情况
        assertFalse(callMatchesConfigRepos("project2/repo1", listOf("project1/*")))
    }

    @Test
    @DisplayName("测试部分通配符匹配")
    fun testPartialWildcardMatch() {
        val configRepos = listOf("project*/*", "*/repo*", "test-*/test-*")

        // 项目前缀通配符
        assertTrue(callMatchesConfigRepos("project1/repo1", configRepos))
        assertTrue(callMatchesConfigRepos("project-test/repo1", configRepos))
        assertTrue(callMatchesConfigRepos("projectabc/repo1", configRepos))

        // 仓库前缀通配符
        assertTrue(callMatchesConfigRepos("any-project/repo1", configRepos))
        assertTrue(callMatchesConfigRepos("any-project/repo-test", configRepos))
        assertTrue(callMatchesConfigRepos("any-project/repoabc", configRepos))

        // 双前缀通配符
        assertTrue(callMatchesConfigRepos("test-project/test-repo", configRepos))
        assertTrue(callMatchesConfigRepos("test-abc/test-xyz", configRepos))

        // 不匹配的情况
        assertFalse(callMatchesConfigRepos("abc-project/repo1", listOf("project*/*")))
        assertFalse(callMatchesConfigRepos("any-project/abc-repo", listOf("*/repo*")))
    }

    @Test
    @DisplayName("测试空列表和空字符串")
    fun testEmptyAndNull() {
        // 空配置列表
        assertFalse(callMatchesConfigRepos("project1/repo1", emptyList()))
        assertFalse(callMatchesConfigRepos("project1/*", emptyList()))

        // 包含空字符串的配置
        val configWithEmpty = listOf("", "project1/repo1")
        assertTrue(callMatchesConfigRepos("project1/repo1", configWithEmpty))
        assertFalse(callMatchesConfigRepos("project2/repo2", configWithEmpty))
    }

    @Test
    @DisplayName("测试特殊字符")
    fun testSpecialCharacters() {
        val configRepos = listOf("project-1/repo_1", "project.test/*", "*/repo-*")

        // 包含特殊字符的精确匹配
        assertTrue(callMatchesConfigRepos("project-1/repo_1", configRepos))

        // 包含点号的通配符匹配
        assertTrue(callMatchesConfigRepos("project.test/any-repo", configRepos))

        // 包含连字符的通配符匹配
        assertTrue(callMatchesConfigRepos("any-project/repo-test", configRepos))
    }

    @Test
    @DisplayName("测试复杂匹配场景")
    fun testComplexScenarios() {
        val configRepos = listOf(
            "prod-*/release-*",
            "test-*/test-*",
            "dev/*",
            "*/common",
            "special/repo"
        )

        // 复杂通配符匹配
        assertTrue(callMatchesConfigRepos("prod-app/release-v1", configRepos))
        assertTrue(callMatchesConfigRepos("test-service/test-repo", configRepos))
        assertTrue(callMatchesConfigRepos("dev/any-repo", configRepos))
        assertTrue(callMatchesConfigRepos("any-project/common", configRepos))
        assertTrue(callMatchesConfigRepos("special/repo", configRepos))

        // 不匹配的情况
        assertFalse(callMatchesConfigRepos("prod-app/test-v1", configRepos))
        assertFalse(callMatchesConfigRepos("staging/private", configRepos))
    }

    @Test
    @DisplayName("测试大小写敏感")
    fun testCaseSensitive() {
        val configRepos = listOf("Project1/Repo1", "project2/*")

        // 大小写敏感匹配
        assertTrue(callMatchesConfigRepos("Project1/Repo1", configRepos))
        assertFalse(callMatchesConfigRepos("project1/repo1", configRepos))

        // 通配符大小写敏感
        assertTrue(callMatchesConfigRepos("project2/AnyRepo", configRepos))
        assertFalse(callMatchesConfigRepos("Project2/AnyRepo", configRepos))
    }

    @Test
    @DisplayName("测试单个配置项匹配")
    fun testSingleConfigMatch() {
        // 单个精确匹配
        assertTrue(callMatchesConfigRepos("project1/repo1", listOf("project1/repo1")))

        // 单个通配符匹配
        assertTrue(callMatchesConfigRepos("project1/repo1", listOf("*/*")))
        assertTrue(callMatchesConfigRepos("project1/repo1", listOf("project1/*")))
        assertTrue(callMatchesConfigRepos("project1/repo1", listOf("*/repo1")))

        // 单个不匹配
        assertFalse(callMatchesConfigRepos("project1/repo1", listOf("project2/repo2")))
    }

    @Test
    @DisplayName("测试项目ID是否允许数据分离 - 精确匹配")
    fun testIsProjectAllowedForSeparation_ExactMatch() {
        // Mock配置：精确匹配项目ID
        every { dataSeparationConfig.specialSeparateRepos } returns mutableListOf(
            "project1/repo1",
            "project2/repo2",
            "test-project/test-repo"
        )

        // 精确匹配的项目ID应该被允许
        assertTrue(callIsProjectAllowedForSeparation("project1"))
        assertTrue(callIsProjectAllowedForSeparation("project2"))
        assertTrue(callIsProjectAllowedForSeparation("test-project"))

        // 不在配置中的项目ID应该被拒绝
        assertFalse(callIsProjectAllowedForSeparation("project3"))
        assertFalse(callIsProjectAllowedForSeparation("unknown-project"))
        assertFalse(callIsProjectAllowedForSeparation(""))
    }

    @Test
    @DisplayName("测试项目ID是否允许数据分离 - 通配符匹配")
    fun testIsProjectAllowedForSeparation_WildcardMatch() {
        // Mock配置：包含通配符
        every { dataSeparationConfig.specialSeparateRepos } returns mutableListOf(
            "*/common",
            "prod-*/release-repo",
            "*/*"
        )

        // 任何项目ID都应该被允许（因为有*/*配置）
        assertTrue(callIsProjectAllowedForSeparation("any-project"))
        assertTrue(callIsProjectAllowedForSeparation("test"))
        assertTrue(callIsProjectAllowedForSeparation("prod-app"))
        assertTrue(callIsProjectAllowedForSeparation("random-name"))
    }

    @Test
    @DisplayName("测试项目ID是否允许数据分离 - 全局通配符")
    fun testIsProjectAllowedForSeparation_GlobalWildcard() {
        // Mock配置：只有全局通配符
        every { dataSeparationConfig.specialSeparateRepos } returns mutableListOf("*/*")

        // 任何项目ID都应该被允许
        assertTrue(callIsProjectAllowedForSeparation("project1"))
        assertTrue(callIsProjectAllowedForSeparation("any-project"))
        assertTrue(callIsProjectAllowedForSeparation("test-123"))
        assertTrue(callIsProjectAllowedForSeparation(""))
    }

    @Test
    @DisplayName("测试项目ID是否允许数据分离 - 项目级通配符")
    fun testIsProjectAllowedForSeparation_ProjectWildcard() {
        // Mock配置：项目级通配符
        every { dataSeparationConfig.specialSeparateRepos } returns mutableListOf(
            "project1/*",
            "test-*/*",
            "prod/*"
        )

        // 匹配的项目ID应该被允许
        assertTrue(callIsProjectAllowedForSeparation("project1"))
        assertTrue(callIsProjectAllowedForSeparation("test-app"))
        assertTrue(callIsProjectAllowedForSeparation("test-service"))
        assertTrue(callIsProjectAllowedForSeparation("prod"))

        // 不匹配的项目ID应该被拒绝
        assertFalse(callIsProjectAllowedForSeparation("project2"))
        assertFalse(callIsProjectAllowedForSeparation("dev"))
        assertFalse(callIsProjectAllowedForSeparation("staging"))
    }

    @Test
    @DisplayName("测试项目ID是否允许数据分离 - 空配置")
    fun testIsProjectAllowedForSeparation_EmptyConfig() {
        // Mock配置：空列表
        every { dataSeparationConfig.specialSeparateRepos } returns mutableListOf()

        // 所有项目ID都应该被拒绝
        assertFalse(callIsProjectAllowedForSeparation("project1"))
        assertFalse(callIsProjectAllowedForSeparation("any-project"))
        assertFalse(callIsProjectAllowedForSeparation("test"))
        assertFalse(callIsProjectAllowedForSeparation(""))
    }

    @Test
    @DisplayName("测试项目ID是否允许数据分离 - 混合配置")
    fun testIsProjectAllowedForSeparation_MixedConfig() {
        // Mock配置：混合精确匹配和通配符
        every { dataSeparationConfig.specialSeparateRepos } returns mutableListOf(
            "exact-project/exact-repo",
            "wildcard-*/*",
            "*/shared-repo",
            "special/*"
        )

        // 精确匹配
        assertTrue(callIsProjectAllowedForSeparation("exact-project"))

        // 通配符匹配
        assertTrue(callIsProjectAllowedForSeparation("wildcard-test"))
        assertTrue(callIsProjectAllowedForSeparation("wildcard-prod"))
        assertTrue(callIsProjectAllowedForSeparation("any-name")) // 匹配 */shared-repo
        assertTrue(callIsProjectAllowedForSeparation("special"))

        // 不匹配
        assertTrue(callIsProjectAllowedForSeparation("other-project"))
        assertTrue(callIsProjectAllowedForSeparation("random"))
    }

    @Test
    @DisplayName("测试项目ID是否允许数据分离 - 特殊字符")
    fun testIsProjectAllowedForSeparation_SpecialCharacters() {
        // Mock配置：包含特殊字符
        every { dataSeparationConfig.specialSeparateRepos } returns mutableListOf(
            "project-1/repo_1",
            "project.test/*",
            "test_project/*"
        )

        // 包含特殊字符的项目ID
        assertTrue(callIsProjectAllowedForSeparation("project-1"))
        assertTrue(callIsProjectAllowedForSeparation("project.test"))
        assertTrue(callIsProjectAllowedForSeparation("test_project"))

        // 不匹配的特殊字符
        assertFalse(callIsProjectAllowedForSeparation("project_1"))
        assertFalse(callIsProjectAllowedForSeparation("project-test"))
    }

    @Test
    @DisplayName("测试项目模式匹配 - 通配符逻辑")
    fun testMatchesProjectPattern() {
        // 使用反射调用私有方法进行测试
        val method = SeparationTaskServiceImpl::class.java.getDeclaredMethod(
            "matchesProjectPattern", 
            String::class.java, 
            String::class.java
        )
        method.isAccessible = true
        
        fun callMatchesProjectPattern(projectId: String, pattern: String): Boolean {
            return method.invoke(separationTaskService, projectId, pattern) as Boolean
        }

        // 全局通配符测试
        assertTrue(callMatchesProjectPattern("any-project", "*"))
        assertTrue(callMatchesProjectPattern("", "*"))
        assertTrue(callMatchesProjectPattern("test123", "*"))

        // 精确匹配测试
        assertTrue(callMatchesProjectPattern("project1", "project1"))
        assertTrue(callMatchesProjectPattern("test-app", "test-app"))
        assertFalse(callMatchesProjectPattern("project1", "project2"))

        // 前缀通配符测试
        assertTrue(callMatchesProjectPattern("test-app", "test-*"))
        assertTrue(callMatchesProjectPattern("test-service", "test-*"))
        assertTrue(callMatchesProjectPattern("test-", "test-*"))
        assertTrue(callMatchesProjectPattern("test", "test*"))
        assertFalse(callMatchesProjectPattern("prod-app", "test-*"))
        assertFalse(callMatchesProjectPattern("mytest-app", "test-*"))

        // 后缀通配符测试
        assertTrue(callMatchesProjectPattern("app-test", "*-test"))
        assertTrue(callMatchesProjectPattern("service-test", "*-test"))
        assertTrue(callMatchesProjectPattern("-test", "*-test"))
        assertTrue(callMatchesProjectPattern("test", "*test"))
        assertFalse(callMatchesProjectPattern("app-prod", "*-test"))
        assertFalse(callMatchesProjectPattern("app-testing", "*-test"))

        // 边界情况测试
        assertFalse(callMatchesProjectPattern("project", ""))
        assertFalse(callMatchesProjectPattern("", "project"))
        assertTrue(callMatchesProjectPattern("", ""))
        
        // 特殊字符测试
        assertTrue(callMatchesProjectPattern("project-1.0", "project-*"))
        assertTrue(callMatchesProjectPattern("test_app", "test_*"))
        assertTrue(callMatchesProjectPattern("app.service", "*.service"))
    }
}