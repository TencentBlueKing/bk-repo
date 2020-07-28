package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.service.query.NodeQueryService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * NodeQueryTest
 *
 * @author: carrypan
 * @date: 2019-11-15
 */
@DisplayName("节点自定义查询测试")
@SpringBootTest(properties = ["auth.enabled=false"])
internal class NodeQueryTest @Autowired constructor(
    private val nodeService: NodeService,
    private val nodeQueryService: NodeQueryService,
    private val repositoryService: RepositoryService
) {
    private val projectId = "unit-test"
    private var repoName = "unit-test"
    private val operator = "system"

    @BeforeEach
    fun setUp() {
        if (!repositoryService.exist(projectId, repoName)) {
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
    @DisplayName("完整路径前缀匹配查询")
    fun fullPathQueryTest() {
        nodeService.create(createRequest("/a/b"))
        nodeService.create(createRequest("/a/b/1.txt", false))
        val size = 21
        repeat(size) { i -> nodeService.create(createRequest("/a/b/c/$i.txt", false)) }
        repeat(size) { i -> nodeService.create(createRequest("/a/b/d/$i.txt", false)) }

        val projectId = Rule.QueryRule("projectId", projectId)
        val repoName = Rule.QueryRule("repoName", repoName)
        val path = Rule.QueryRule("fullPath", "/a/b/d", OperationType.PREFIX)
        val folder = Rule.QueryRule("folder", false)
        val rule = Rule.NestedRule(mutableListOf(projectId, repoName, path, folder))

        val queryModel = QueryModel(
            page = PageLimit(0, 11),
            sort = Sort(listOf("fullPath"), Sort.Direction.ASC),
            select = mutableListOf("projectId", "repoName", "fullPath", "metadata"),
            rule = rule
        )

        val result = nodeQueryService.query(queryModel)
        Assertions.assertEquals(21, result.count)
        Assertions.assertEquals(11, result.records.size)
    }

    @Test
    @DisplayName("元数据精确查询")
    fun metadataUserQueryTest() {
        nodeService.create(createRequest("/a/b/1.txt", false, metadata = mapOf("key1" to "1", "key2" to "2")))
        nodeService.create(createRequest("/a/b/2.txt", false, metadata = mapOf("key1" to "11", "key2" to "2")))
        nodeService.create(createRequest("/a/b/3.txt", false, metadata = mapOf("key1" to "22")))
        nodeService.create(createRequest("/a/b/4.txt", false, metadata = mapOf("key1" to "2", "key2" to "1")))

        val projectId = Rule.QueryRule("projectId", projectId)
        val repoName = Rule.QueryRule("repoName", repoName)
        val metadata = Rule.QueryRule("metadata.key1", "1")
        val rule = Rule.NestedRule(mutableListOf(projectId, repoName, metadata))

        val queryModel = QueryModel(
            page = PageLimit(0, 10),
            sort = Sort(listOf("fullPath"), Sort.Direction.ASC),
            select = mutableListOf("projectId", "repoName", "fullPath", "metadata"),
            rule = rule
        )

        val result = nodeQueryService.userQuery(operator, queryModel)
        println(result)
        Assertions.assertEquals(1, result.count)
        Assertions.assertEquals(1, result.records.size)
        val node = result.records[0]
        val metadataMap = node["metadata"] as Map<*, *>
        Assertions.assertEquals("1", metadataMap["key1"])
        Assertions.assertEquals("/a/b/1.txt", node["fullPath"])
    }

    @Test
    @DisplayName("元数据前缀匹配查询")
    fun metadataPrefixQueryTest() {
        nodeService.create(createRequest("/a/b/1.txt", false, metadata = mapOf("key" to "1")))
        nodeService.create(createRequest("/a/b/2.txt", false, metadata = mapOf("key" to "11")))
        nodeService.create(createRequest("/a/b/3.txt", false, metadata = mapOf("key" to "22")))
        nodeService.create(createRequest("/a/b/4.txt", false, metadata = mapOf("key" to "22", "key1" to "1")))

        val projectId = Rule.QueryRule("projectId", projectId)
        val repoName = Rule.QueryRule("repoName", repoName)
        val metadata = Rule.QueryRule("metadata.key", "1", OperationType.PREFIX) // 前缀
        val rule = Rule.NestedRule(mutableListOf(projectId, repoName, metadata))

        val queryModel = QueryModel(
            page = PageLimit(0, 10),
            sort = Sort(listOf("fullPath"), Sort.Direction.ASC),
            select = mutableListOf("projectId", "repoName", "fullPath", "metadata"),
            rule = rule
        )

        val result = nodeQueryService.userQuery(operator, queryModel)
        Assertions.assertEquals(2, result.count)
        Assertions.assertEquals(2, result.records.size)
    }

    @Test
    @DisplayName("元数据模糊匹配查询")
    fun metadataFuzzyQueryTest() {
        nodeService.create(createRequest("/a/b/1.txt", false, metadata = mapOf("key" to "121")))
        nodeService.create(createRequest("/a/b/2.txt", false, metadata = mapOf("key" to "131")))
        nodeService.create(createRequest("/a/b/3.txt", false, metadata = mapOf("key" to "144")))

        val projectId = Rule.QueryRule("projectId", projectId)
        val repoName = Rule.QueryRule("repoName", repoName)
        val metadata = Rule.QueryRule("metadata.key", "1*1", OperationType.MATCH) // 前缀
        val rule = Rule.NestedRule(mutableListOf(projectId, repoName, metadata))

        val queryModel = QueryModel(
            page = PageLimit(0, 10),
            sort = Sort(listOf("fullPath"), Sort.Direction.ASC),
            select = mutableListOf("projectId", "repoName", "fullPath", "metadata"),
            rule = rule
        )

        val result = nodeQueryService.userQuery(operator, queryModel)
        Assertions.assertEquals(2, result.count)
        Assertions.assertEquals(2, result.records.size)
    }

    private fun createRequest(fullPath: String = "/a/b/c", folder: Boolean = true, size: Long = 1, metadata: Map<String, String>? = null): NodeCreateRequest {
        return NodeCreateRequest(
            projectId = projectId,
            repoName = repoName,
            folder = folder,
            fullPath = fullPath,
            expires = 0,
            overwrite = false,
            size = size,
            sha256 = "sha256",
            md5 = "md5",
            operator = operator,
            metadata = metadata
        )
    }
}
