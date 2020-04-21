package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
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
    private val projectId = "unit-test"
    private val operator = "system"
    private var repoName = "unit-test"

    private val defaultMetadata = mapOf("key1" to "value1", "key2" to "value2")

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
        val node = nodeService.create(createRequest())
        Assertions.assertEquals(0, metadataService.query(projectId, repoName, node.fullPath).size)
        metadataService.save(MetadataSaveRequest(projectId, repoName, node.fullPath, defaultMetadata))
        val dbMetadata = metadataService.query(projectId, repoName, node.fullPath)
        Assertions.assertEquals(2, dbMetadata.size)
        Assertions.assertEquals("value1", dbMetadata["key1"])
        Assertions.assertEquals("value2", dbMetadata["key2"])
    }

    @Test
    fun saveEmptyTest() {
        val node = nodeService.create(createRequest(defaultMetadata))
        // update with empty key list
        metadataService.save(MetadataSaveRequest(projectId, repoName, node.fullPath, mutableMapOf()))

        val dbMetadata = metadataService.query(projectId, repoName, node.fullPath)
        Assertions.assertEquals("value1", dbMetadata["key1"])
        Assertions.assertEquals("value2", dbMetadata["key2"])
    }
    
    @Test
    fun updateTest() {
        val node = nodeService.create(createRequest(defaultMetadata))
        // update
        val newMetadata = mapOf("key1" to "value1", "key2" to "value22", "key3" to "value3")
        metadataService.save(MetadataSaveRequest(projectId, repoName, node.fullPath, newMetadata))

        val dbMetadata = metadataService.query(projectId, repoName, node.fullPath)
        Assertions.assertEquals(3, dbMetadata.size)
        Assertions.assertEquals("value1", dbMetadata["key1"])
        Assertions.assertEquals("value22", dbMetadata["key2"])
        Assertions.assertEquals("value3", dbMetadata["key3"])
    }

    @Test
    fun deleteTest() {
        val metadata = mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3")
        val node = nodeService.create(createRequest(metadata))
        // delete
        metadataService.delete(MetadataDeleteRequest(projectId, repoName,node.fullPath, setOf("key1", "key2", "key0")))

        val dbMetadata = metadataService.query(projectId, repoName, node.fullPath)
        Assertions.assertEquals(1, dbMetadata.size)
        Assertions.assertEquals("value3", dbMetadata["key3"])
    }

    private fun createRequest(metadata: Map<String, String> = emptyMap()): NodeCreateRequest {
        return NodeCreateRequest(
            projectId = projectId,
            repoName = repoName,
            folder = false,
            fullPath = "/1.txt",
            expires = 0,
            overwrite = false,
            size = 1,
            sha256 = "sha256",
            md5 = "md5",
            metadata = metadata,
            operator = operator
        )
    }

}
