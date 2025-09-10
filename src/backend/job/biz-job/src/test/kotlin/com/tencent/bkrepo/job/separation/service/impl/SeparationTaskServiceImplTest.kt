package com.tencent.bkrepo.job.separation.service.impl

import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.job.separation.config.DataSeparationConfig
import com.tencent.bkrepo.job.separation.dao.SeparationFailedRecordDao
import com.tencent.bkrepo.job.separation.dao.SeparationTaskDao
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
    }

    /**
     * 调用私有方法matchesConfigRepos的辅助方法
     */
    private fun callMatchesConfigRepos(projectRepoKey: String, configRepos: List<String>): Boolean {
        return matchesConfigReposMethod.invoke(separationTaskService, projectRepoKey, configRepos) as Boolean
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
}