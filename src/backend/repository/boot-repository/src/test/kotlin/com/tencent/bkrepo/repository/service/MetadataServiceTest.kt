package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.constant.enum.RepositoryCategoryEnum
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataUpsertRequest
import com.tencent.bkrepo.repository.pojo.node.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
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

    private var repoId = ""

    @BeforeEach
    fun setUp() {
        repositoryService.list(projectId).forEach { repositoryService.deleteById(it.id) }
        repoId = repositoryService.create(RepoCreateRequest(operator, "test", "BINARY", RepositoryCategoryEnum.LOCAL, true, projectId, "简单描述")).id
    }

    @AfterEach
    fun tearDown() {
        repositoryService.deleteById(repoId)
    }

    @Test
    fun upsertTest() {
        nodeService.create(NodeCreateRequest(false, "/a/b", "c.txt", repoId, operator, 0, false, 1024, "sha256")).id
        Assertions.assertEquals(0, metadataService.query(repoId, "/a/b/c.txt").size)

        val metadata = mutableMapOf<String, String>()
        metadata["name"] = "c.txt"
        metadata["createdBy"] = "system"
        metadataService.upsert(MetadataUpsertRequest(repoId, "a/b/c.txt", metadata))

        var dbMetadata = metadataService.query(repoId, "/a/b/c.txt")
        Assertions.assertEquals("c.txt", dbMetadata["name"])
        Assertions.assertEquals("system", dbMetadata["createdBy"])

        metadata["size"] = "0"
        metadata["createdBy"] = "admin"
        metadataService.upsert(MetadataUpsertRequest(repoId, "a/b/c.txt", metadata))

        dbMetadata = metadataService.query(repoId, "/a/b/c.txt")
        Assertions.assertEquals("c.txt", dbMetadata["name"])
        Assertions.assertEquals("admin", dbMetadata["createdBy"])
        Assertions.assertEquals("0", dbMetadata["size"])
    }

    @Test
    fun deleteTest() {
        nodeService.create(NodeCreateRequest(false, "/a/b", "c.txt", repoId, operator, 0, false, 1024, "sha256")).id
        Assertions.assertEquals(0, metadataService.query(repoId, "/a/b/c.txt").size)

        val metadata = mutableMapOf<String, String>()
        metadata["name"] = "c.txt"
        metadata["createdBy"] = "system"
        metadata["size"] = "0"
        metadataService.upsert(MetadataUpsertRequest(repoId, "a/b/c.txt", metadata))

        metadataService.delete(MetadataDeleteRequest(repoId, "a/b/c.txt", setOf("name", "createdBy")))

        val dbMetadata = metadataService.query(repoId, "/a/b/c.txt")
        Assertions.assertEquals(1, dbMetadata.size)
        Assertions.assertEquals("0", dbMetadata["size"])
    }
}