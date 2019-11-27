package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.repository.configuration.LocalConfiguration
import com.tencent.bkrepo.repository.constant.enums.RepositoryCategory
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * RepositoryServiceTest
 *
 * @author: carrypan
 * @date: 2019-09-23
 */
@DisplayName("仓库服务测试")
@SpringBootTest
internal class RepositoryServiceTest @Autowired constructor(
    private val repositoryService: RepositoryService
) {

    private val projectId = "1"
    private val operator = "system"
    private val repoName = "test"

    @BeforeEach
    fun setUp() {
        repositoryService.list(projectId).forEach { repositoryService.delete(projectId, it.name) }
    }

    @AfterEach
    fun tearDown() {
        repositoryService.list(projectId).forEach { repositoryService.delete(projectId, it.name) }
    }


    @Test
    fun list() {
        assertEquals(0, repositoryService.list(projectId).size)
        val size = 20
        repeat(size) { i -> repositoryService.create(createRequest("repo$i")) }
        assertEquals(size, repositoryService.list(projectId).size)
    }

    @Test
    fun page() {
        assertEquals(0, repositoryService.list(projectId).size)
        val size = 51L
        repeat(size.toInt()) {
            i -> repositoryService.create(createRequest("repo$i"))
        }
        var page = repositoryService.page(projectId, 0, 10)
        assertEquals(10, page.records.size)
        assertEquals(size, page.count)
        assertEquals(0, page.page)
        assertEquals(10, page.pageSize)

        page = repositoryService.page(projectId, 5, 10)
        assertEquals(1, page.records.size)
        page = repositoryService.page(projectId, 6, 10)
        assertEquals(0, page.records.size)

        page = repositoryService.page(projectId, 0, 20)
        assertEquals(20, page.records.size)
        assertEquals(size, page.count)
        assertEquals(0, page.page)
        assertEquals(20, page.pageSize)
    }

    @Test
    fun exist() {
        repositoryService.create(createRequest())
        assertTrue(repositoryService.exist(projectId, repoName))
        assertTrue(repositoryService.exist(projectId, repoName, "GENERIC"))
        assertFalse(repositoryService.exist("", ""))
        assertFalse(repositoryService.exist(projectId, ""))
        assertFalse(repositoryService.exist("", repoName))

        repositoryService.delete(projectId, repoName)
        assertFalse(repositoryService.exist(projectId, repoName))
    }

    @Test
    fun create() {
        repositoryService.create(createRequest())
        val repository = repositoryService.detail(projectId, repoName, "GENERIC")!!
        assertEquals(repoName, repository.name)
        assertEquals("GENERIC", repository.type)
        assertEquals(RepositoryCategory.LOCAL, repository.category)
        assertEquals(true, repository.public)
        assertEquals(projectId, repository.projectId)
        assertEquals("简单描述", repository.description)
        assertThrows<ErrorCodeException> { repositoryService.create(createRequest()) }
    }

    @Test
    fun update() {
        repositoryService.create(createRequest())
        repositoryService.update(RepoUpdateRequest(
                projectId = projectId,
                name = repoName,
                public = false,
                description = "新的描述",
                operator = operator))
        val repository = repositoryService.detail(projectId, repoName)!!
        assertEquals(false, repository.public)
        assertEquals("新的描述", repository.description)
    }

    @Test
    fun deleteById() {
        repositoryService.create(createRequest("test1"))
        repositoryService.create(createRequest("test2"))
        repositoryService.delete(projectId, "test1")
        assertNull(repositoryService.detail(projectId, "test1"))

        assertThrows<ErrorCodeException> { repositoryService.delete(projectId, "") }
        assertThrows<ErrorCodeException> { repositoryService.delete(projectId, "test1") }

        assertNotNull(repositoryService.detail(projectId, "test2"))
    }

    private fun createRequest(name: String = repoName): RepoCreateRequest {
        return RepoCreateRequest(
                projectId = projectId,
                name = name,
                type = "GENERIC",
                category = RepositoryCategory.LOCAL,
                public = true,
                description = "简单描述",
                configuration = LocalConfiguration(),
                operator = operator
        )
    }
}
