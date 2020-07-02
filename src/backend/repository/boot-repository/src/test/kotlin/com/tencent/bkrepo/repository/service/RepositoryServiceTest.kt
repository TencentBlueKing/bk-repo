package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.credendial.StorageCredentialsCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest
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
@SpringBootTest(properties = ["auth.enabled=false"])
internal class RepositoryServiceTest @Autowired constructor(
    private val repositoryService: RepositoryService,
    private val storageCredentialService: StorageCredentialService
) {
    private val projectId = "unit-test"
    private val operator = "system"
    private val repoName = "test"
    private val storageCredentialsKey = "unit-test-credentials-key"
    private val storageCredentials = FileSystemCredentials().apply {
        path = "test"
        cache.enabled = true
        cache.path = "cache-test"
        cache.expireDays = 10
    }

    @BeforeEach
    fun setUp() {
        repositoryService.list(projectId).forEach {
            repositoryService.delete(RepoDeleteRequest(projectId, it.name, SYSTEM_USER))
        }

        val createRequest = StorageCredentialsCreateRequest(storageCredentialsKey, storageCredentials)
        storageCredentialService.create(operator, createRequest)
    }

    @AfterEach
    fun tearDown() {
        repositoryService.list(projectId).forEach {
            repositoryService.delete(RepoDeleteRequest(projectId, it.name, SYSTEM_USER))
        }
        storageCredentialService.delete(storageCredentialsKey)
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

        repositoryService.delete(RepoDeleteRequest(projectId, repoName, SYSTEM_USER))
        assertFalse(repositoryService.exist(projectId, repoName))
    }

    @Test
    fun create() {
        val request = createRequest(storageCredentialKey = storageCredentialsKey)
        repositoryService.create(request)
        val repository = repositoryService.detail(projectId, repoName, "GENERIC")!!
        assertEquals(repoName, repository.name)
        assertEquals(RepositoryType.GENERIC, repository.type)
        assertEquals(RepositoryCategory.LOCAL, repository.category)
        assertEquals(true, repository.public)
        assertEquals(projectId, repository.projectId)
        assertEquals("简单描述", repository.description)
        assertTrue(repository.storageCredentials is FileSystemCredentials)
        val dbCredential = repository.storageCredentials as FileSystemCredentials
        assertEquals(storageCredentials.path, dbCredential.path)
        assertEquals(storageCredentials.cache.enabled, dbCredential.cache.enabled )
        assertEquals(storageCredentials.cache.path, dbCredential.cache.path )
        assertEquals(storageCredentials.cache.expireDays, dbCredential.cache.expireDays )

        assertThrows<ErrorCodeException> { repositoryService.create(createRequest()) }
    }

    @Test
    fun createWithNullCredentials() {
        val request = createRequest()
        repositoryService.create(request)
        val repository = repositoryService.detail(projectId, repoName, "GENERIC")!!
        assertEquals(repoName, repository.name)
        assertEquals(RepositoryType.GENERIC, repository.type)
        assertEquals(RepositoryCategory.LOCAL, repository.category)
        assertEquals(true, repository.public)
        assertEquals(projectId, repository.projectId)
        assertEquals("简单描述", repository.description)
        assertNull(repository.storageCredentials)
        assertThrows<ErrorCodeException> { repositoryService.create(createRequest()) }
    }


    @Test
    fun createWithNonExistCredentials() {
        val request = createRequest(storageCredentialKey = "non-exist-credentials-key")
        assertThrows<ErrorCodeException> { repositoryService.create(request) }
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
        repositoryService.delete(RepoDeleteRequest(projectId, "test1", SYSTEM_USER))
        assertNull(repositoryService.detail(projectId, "test1"))

        assertThrows<ErrorCodeException> { repositoryService.delete(RepoDeleteRequest(projectId, "", SYSTEM_USER)) }
        assertThrows<ErrorCodeException> { repositoryService.delete(RepoDeleteRequest(projectId, "test1", SYSTEM_USER)) }

        assertNotNull(repositoryService.detail(projectId, "test2"))
    }

    private fun createRequest(name: String = repoName, storageCredentialKey: String? = null): RepoCreateRequest {
        return RepoCreateRequest(
                projectId = projectId,
                name = name,
                type = RepositoryType.GENERIC,
                category = RepositoryCategory.LOCAL,
                public = true,
                description = "简单描述",
                configuration = LocalConfiguration(),
                storageCredentialsKey = storageCredentialKey,
                operator = operator
        )
    }
}
