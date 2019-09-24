package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.repository.constant.enum.RepositoryCategoryEnum
import com.tencent.bkrepo.repository.pojo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.RepoUpdateRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    @BeforeEach
    fun setUp() {
        repositoryService.list(projectId).forEach { repositoryService.deleteById(it.id) }
    }

    @BeforeEach
    fun tearDown() {
        repositoryService.list(projectId).forEach { repositoryService.deleteById(it.id) }
    }

    @Test
    fun getDetailById() {
        assertThrows<ErrorCodeException> { repositoryService.getDetailById("") }
    }

    @Test
    fun list() {
        assertEquals(0, repositoryService.list(projectId).size)
        val size = 20
        repeat(size) {
            repositoryService.create(RepoCreateRequest(operator, "测试仓库", "BINARY", RepositoryCategoryEnum.LOCAL, true, projectId, "简单描述"))
        }
        assertEquals(size, repositoryService.list(projectId).size)
    }

    @Test
    fun page() {
        assertEquals(0, repositoryService.list(projectId).size)
        val size = 51L
        repeat(size.toInt()) {
            repositoryService.create(RepoCreateRequest(operator, "测试仓库", "BINARY", RepositoryCategoryEnum.LOCAL, true, projectId, "简单描述"))
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
        val idValue = repositoryService.create(RepoCreateRequest(operator, "测试仓库", "BINARY", RepositoryCategoryEnum.LOCAL, true, projectId, "简单描述"))
        assertTrue(repositoryService.exist(projectId, "BINARY", "测试仓库"))
        assertFalse(repositoryService.exist("", "", ""))
        assertFalse(repositoryService.exist(projectId, "", ""))
        assertFalse(repositoryService.exist(projectId, "BINARY", ""))
        assertFalse(repositoryService.exist("", "BINARY", "测试仓库"))

        repositoryService.deleteById(idValue.id)
        assertFalse(repositoryService.exist(projectId, "BINARY", "测试仓库"))
    }

    @Test
    fun create() {
        val idValue = repositoryService.create(RepoCreateRequest(operator, "测试仓库", "BINARY", RepositoryCategoryEnum.LOCAL, true, projectId, "简单描述"))
        val repository = repositoryService.getDetailById(idValue.id)
        assertEquals("测试仓库", repository.name)
        assertEquals("BINARY", repository.type)
        assertEquals(RepositoryCategoryEnum.LOCAL, repository.category)
        assertEquals(true, repository.public)
        assertEquals(projectId, repository.projectId)
        assertEquals("简单描述", repository.description)
        assertEquals(null, repository.extension)
    }

    @Test
    fun updateById() {
        val idValue = repositoryService.create(RepoCreateRequest(operator, "测试仓库", "BINARY", RepositoryCategoryEnum.LOCAL, true, projectId, "简单描述"))
        repositoryService.updateById(idValue.id, RepoUpdateRequest(operator, name = "新的名称", category = RepositoryCategoryEnum.REMOTE, public = false, description = "新的描述"))
        val repository = repositoryService.getDetailById(idValue.id)
        assertEquals("新的名称", repository.name)
        assertEquals(RepositoryCategoryEnum.REMOTE, repository.category)
        assertEquals(false, repository.public)
        assertEquals("新的描述", repository.description)
        assertEquals(projectId, repository.projectId)
    }

    @Test
    fun deleteById() {
        val idValue = repositoryService.create(RepoCreateRequest(operator, "测试仓库", "BINARY", RepositoryCategoryEnum.LOCAL, true, projectId, "简单描述"))
        val idValue2 = repositoryService.create(RepoCreateRequest(operator, "测试仓库", "BINARY", RepositoryCategoryEnum.LOCAL, true, projectId, "简单描述"))
        repositoryService.deleteById(idValue.id)
        assertThrows<ErrorCodeException> { repositoryService.getDetailById(idValue.id) }

        repositoryService.deleteById("")
        repositoryService.getDetailById(idValue2.id)
    }
}
