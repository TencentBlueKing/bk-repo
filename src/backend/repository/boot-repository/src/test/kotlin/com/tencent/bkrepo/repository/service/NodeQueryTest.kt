package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.artifact.repository.configuration.LocalConfiguration
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.repository.constant.enums.RepositoryCategory
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.service.query.NodeQueryService
import org.apache.commons.lang.RandomStringUtils
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
@SpringBootTest
internal class NodeQueryTest @Autowired constructor(
    private val nodeService: NodeService,
    private val nodeQueryService: NodeQueryService,
    private val repositoryService: RepositoryService
) {

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
                category = RepositoryCategory.LOCAL,
                public = true,
                description = "简单描述",
                configuration = LocalConfiguration(),
                operator = operator
            )
        )
    }

    @AfterEach
    fun tearDown() {
        repositoryService.delete(projectId, repoName)
    }

    @Test
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
    fun metadataQueryTest() {
        nodeService.create(createRequest("/a/b/1.txt", false, metadata = mapOf("key1" to "1", "key2" to "2")))

        val projectId = Rule.QueryRule("projectId", projectId)
        val repoName = Rule.QueryRule("repoName", repoName)
        val fullPath = Rule.QueryRule("fullPath", "/a/b/1.txt")
        val rule = Rule.NestedRule(mutableListOf(projectId, repoName, fullPath))

        val queryModel = QueryModel(
            page = PageLimit(0, 10),
            sort = Sort(listOf("fullPath"), Sort.Direction.ASC),
            select = mutableListOf("projectId", "repoName", "fullPath", "metadata"),
            rule = rule
        )

        val result = nodeQueryService.query(queryModel)
        Assertions.assertEquals(1, result.count)
        Assertions.assertEquals(1, result.records.size)
        val node = result.records[0]
        val metadata = node["metadata"] as Map<*, *>
        Assertions.assertEquals("1", metadata["key1"])
    }

    @Test
    fun metadataUserQueryTest() {
        nodeService.create(createRequest("/a/b/1.txt", false, metadata = mapOf("key1" to "1", "key2" to "2")))

        val projectId = Rule.QueryRule("projectId", projectId)
        val repoName = Rule.QueryRule("repoName", listOf(repoName, "test1", "test2"), OperationType.IN)
        val fullPath = Rule.QueryRule("fullPath", "/a/b/1.txt")
        val rule = Rule.NestedRule(mutableListOf(projectId, repoName, fullPath))

        val queryModel = QueryModel(
            page = PageLimit(0, 10),
            sort = Sort(listOf("fullPath"), Sort.Direction.ASC),
            select = mutableListOf("projectId", "repoName", "fullPath", "metadata"),
            rule = rule
        )

        val result = nodeQueryService.userQuery(operator, queryModel)
        Assertions.assertEquals(1, result.count)
        Assertions.assertEquals(1, result.records.size)
        val node = result.records[0]
        val metadata = node["metadata"] as Map<*, *>
        Assertions.assertEquals("1", metadata["key1"])
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
            operator = operator,
            metadata = metadata
        )
    }

}
