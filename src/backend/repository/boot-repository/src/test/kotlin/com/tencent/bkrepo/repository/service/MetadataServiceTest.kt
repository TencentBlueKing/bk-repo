package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.artifact.pojo.configuration.LocalConfiguration
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
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
    private val projectId = "unit-test"
    private val operator = "system"
    private var repoName = "unit-test"

    @BeforeEach
    fun setUp() {
        if(!repositoryService.exist(projectId, repoName)) {
            repositoryService.create(
                RepoCreateRequest(
                    projectId = projectId,
                    name = repoName,
                    type = RepositoryType.GENERIC,
                    category = RepositoryCategory.LOCAL,
                    public = false,
                    description = "单元测试仓库",
                    configuration = LocalConfiguration(),
                    operator = operator
                )
            )
        }
    }

    @AfterEach
    fun tearDown() {
        nodeService.deleteByPath(projectId, repoName, "", operator, false)
    }

    @Test
    fun createTest() {
        val metadata = mutableMapOf<String, String>()
        metadata["name"] = "c.txt"
        metadata["createdBy"] = "system"

        val createRequest = NodeCreateRequest(
            projectId = projectId,
            repoName = repoName,
            folder = false,
            fullPath = "/a/b/c.txt",
            expires = 0,
            overwrite = false,
            size = 1,
            sha256 = "sha256",
            md5 = "md5",
            metadata = metadata,
            operator = operator
        )
        
        nodeService.create(createRequest)

        val dbMetadata = metadataService.query(projectId, repoName, "/a/b/c.txt")
        Assertions.assertEquals(2, dbMetadata.size)
        Assertions.assertEquals("c.txt", dbMetadata["name"])
        Assertions.assertEquals("system", dbMetadata["createdBy"])
    }
    
    @Test
    fun saveTest() {
        nodeService.create(createRequest())
        Assertions.assertEquals(0, metadataService.query(projectId, repoName, "/a/b/c.txt").size)

        val metadata = mutableMapOf<String, String>()
        metadata["name"] = "c.txt"
        metadata["createdBy"] = "system"
        metadataService.save(MetadataSaveRequest(projectId, repoName,"a/b/c.txt", metadata))

        var dbMetadata = metadataService.query(projectId, repoName, "/a/b/c.txt")
        Assertions.assertEquals(2, dbMetadata.size)
        Assertions.assertEquals("c.txt", dbMetadata["name"])
        Assertions.assertEquals("system", dbMetadata["createdBy"])

        metadata["size"] = "0"
        metadata["createdBy"] = "admin"
        metadataService.save(MetadataSaveRequest(projectId, repoName, "a/b/c.txt", metadata))

        dbMetadata = metadataService.query(projectId, repoName, "/a/b/c.txt")
        Assertions.assertEquals(3, dbMetadata.size)
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
        metadataService.save(MetadataSaveRequest(projectId, repoName, "a/b/c.txt", metadata))

        metadataService.delete(MetadataDeleteRequest(projectId, repoName,"a/b/c.txt", setOf("name", "createdBy")))

        val dbMetadata = metadataService.query(projectId, repoName, "/a/b/c.txt")
        Assertions.assertEquals(1, dbMetadata.size)
        Assertions.assertEquals("0", dbMetadata["size"])
    }

    private fun createRequest(): NodeCreateRequest {
        return NodeCreateRequest(
            projectId = projectId,
            repoName = repoName,
            folder = false,
            fullPath = "/a/b/c.txt",
            expires = 0,
            overwrite = false,
            size = 1,
            sha256 = "sha256",
            md5 = "md5",
            operator = operator
        )
    }

}
