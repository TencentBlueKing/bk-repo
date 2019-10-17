package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.constant.enum.RepositoryCategoryEnum
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataUpsertRequest
import com.tencent.bkrepo.repository.pojo.node.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import org.apache.commons.lang.RandomStringUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * 元数据服务 测试
 *
 * @author: carrypan
 * @date: 2019-10-15
 */
@DisplayName("元数据服务 测试")
@SpringBootTest
class MetadataServiceTest @Autowired constructor(
        private val metadataService: MetadataService,
        private val repositoryService: RepositoryService,
        private val nodeService: NodeService
){

    private val projectId = "1"
    private val operator = "system"

    private var repoName = ""

    @BeforeEach
    fun setUp() {
        repoName = RandomStringUtils.randomAlphabetic(10)
        repositoryService.list(projectId).forEach { repositoryService.delete(projectId, it.name) }
        repositoryService.create(
                RepoCreateRequest(
                        projectId = projectId,
                        name = repoName,
                        type = "GENERIC",
                        category = RepositoryCategoryEnum.LOCAL,
                        public = true,
                        description = "简单描述",
                        operator = operator
                )
        )
    }

    @AfterEach
    fun tearDown() {
        repositoryService.delete(projectId, repoName)
    }

    @Test
    fun createTest() {
        val metadata = mutableMapOf<String, String>()
        metadata["name"] = "c.txt"
        metadata["createdBy"] = "system"
        
        val createRequest =  NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                folder = false,
                fullPath = "/a/b/c.txt",
                expires = 0,
                overwrite = false,
                size = 1,
                sha256 = "sha256",
                metadata = metadata,
                operator = operator
        )
        
        nodeService.create(createRequest)
        Assertions.assertEquals(2, metadataService.query(projectId, repoName, "/a/b/c.txt").size)

        var dbMetadata = metadataService.query(projectId, repoName, "/a/b/c.txt")
        Assertions.assertEquals("c.txt", dbMetadata["name"])
        Assertions.assertEquals("system", dbMetadata["createdBy"])
    }
    
    @Test
    fun upsertTest() {
        nodeService.create(createRequest())
        Assertions.assertEquals(0, metadataService.query(projectId, repoName, "/a/b/c.txt").size)

        val metadata = mutableMapOf<String, String>()
        metadata["name"] = "c.txt"
        metadata["createdBy"] = "system"
        metadataService.upsert(MetadataUpsertRequest(projectId, repoName,"a/b/c.txt", metadata, operator))

        var dbMetadata = metadataService.query(projectId, repoName, "/a/b/c.txt")
        Assertions.assertEquals("c.txt", dbMetadata["name"])
        Assertions.assertEquals("system", dbMetadata["createdBy"])

        metadata["size"] = "0"
        metadata["createdBy"] = "admin"
        metadataService.upsert(MetadataUpsertRequest(projectId, repoName, "a/b/c.txt", metadata, operator))

        dbMetadata = metadataService.query(projectId, repoName, "/a/b/c.txt")
        Assertions.assertEquals("c.txt", dbMetadata["name"])
        Assertions.assertEquals("admin", dbMetadata["createdBy"])
        Assertions.assertEquals("0", dbMetadata["size"])
    }

    @Test
    fun deleteTest() {
        nodeService.create(createRequest())
        Assertions.assertEquals(0, metadataService.query(projectId, repoName, "/a/b/c.txt").size)

        val metadata = mutableMapOf<String, String>()
        metadata["name"] = "c.txt"
        metadata["createdBy"] = "system"
        metadata["size"] = "0"
        metadataService.upsert(MetadataUpsertRequest(projectId, repoName, "a/b/c.txt", metadata, operator))

        metadataService.delete(MetadataDeleteRequest(projectId, repoName,"a/b/c.txt", setOf("name", "createdBy"), operator))

        val dbMetadata = metadataService.query(projectId, repoName, "/a/b/c.txt")
        Assertions.assertEquals(1, dbMetadata.size)
        Assertions.assertEquals("0", dbMetadata["size"])
    }

    private fun createRequest(): NodeCreateRequest{
        return NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                folder = false,
                fullPath = "/a/b/c.txt",
                expires = 0,
                overwrite = false,
                size = 1,
                sha256 = "sha256",
                operator = operator
        )
    }

}