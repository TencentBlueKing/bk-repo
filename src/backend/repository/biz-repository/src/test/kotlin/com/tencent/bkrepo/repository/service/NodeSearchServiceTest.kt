/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.auth.pojo.permission.ListPathResult
import com.tencent.bkrepo.common.artifact.path.PathUtils.ROOT
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.UT_PROJECT_ID
import com.tencent.bkrepo.repository.UT_REPO_NAME
import com.tencent.bkrepo.repository.UT_USER
import com.tencent.bkrepo.repository.dao.FileReferenceDao
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import com.tencent.bkrepo.repository.search.common.LocalDatetimeRuleInterceptor
import com.tencent.bkrepo.repository.search.common.RepoNameRuleInterceptor
import com.tencent.bkrepo.repository.search.common.RepoTypeRuleInterceptor
import com.tencent.bkrepo.repository.search.node.NodeQueryInterpreter
import com.tencent.bkrepo.repository.service.node.NodeSearchService
import com.tencent.bkrepo.repository.service.node.NodeService
import com.tencent.bkrepo.repository.service.repo.ProjectService
import com.tencent.bkrepo.repository.service.repo.RepositoryService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.isNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@DisplayName("节点自定义查询测试")
@DataMongoTest
@Import(
    NodeDao::class,
    FileReferenceDao::class,
    NodeQueryInterpreter::class,
    RepoNameRuleInterceptor::class,
    RepoTypeRuleInterceptor::class,
    LocalDatetimeRuleInterceptor::class
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NodeSearchServiceTest @Autowired constructor(
    private val projectService: ProjectService,
    private val repositoryService: RepositoryService,
    private val nodeService: NodeService,
    private val nodeSearchService: NodeSearchService
) : ServiceBaseTest() {

    @BeforeAll
    fun beforeAll() {
        initRepoForUnitTest(projectService, repositoryService)
    }

    @BeforeEach
    fun beforeEach() {
        initMock()
        nodeService.deleteByPath(UT_PROJECT_ID, UT_REPO_NAME, ROOT, UT_USER)
    }

    @Test
    @DisplayName("完整路径前缀匹配查询")
    fun testFullPathSearch() {
        nodeService.createNode(createRequest("/a/b"))
        nodeService.createNode(createRequest("/a/b/1.txt", false))
        val size = 21
        repeat(size) { i -> nodeService.createNode(createRequest("/a/b/c/$i.txt", false)) }
        repeat(size) { i -> nodeService.createNode(createRequest("/a/b/d/$i.txt", false)) }

        val queryModel = createQueryBuilder()
            .fullPath("/a/b/d", OperationType.PREFIX)
            .excludeFolder()
            .page(1, 11)
            .build()

        val result = nodeSearchService.search(queryModel)
        Assertions.assertEquals(21, result.totalRecords)
        Assertions.assertEquals(11, result.records.size)
    }

    @Test
    @DisplayName("元数据精确查询")
    fun testMetadataUserSearch() {
        nodeService.createNode(createRequest("/a/b/1.txt", false, metadata = mapOf("key1" to "1", "key2" to "2")))
        nodeService.createNode(createRequest("/a/b/2.txt", false, metadata = mapOf("key1" to "11", "key2" to "2")))
        nodeService.createNode(createRequest("/a/b/3.txt", false, metadata = mapOf("key1" to "22")))
        nodeService.createNode(createRequest("/a/b/4.txt", false, metadata = mapOf("key1" to "2", "key2" to "1")))

        val queryModel = createQueryBuilder()
            .metadata("key1", "1", OperationType.EQ)
            .build()
        val result = nodeSearchService.search(queryModel)
        Assertions.assertEquals(1, result.totalRecords)
        Assertions.assertEquals(1, result.records.size)
        val node = result.records[0]
        val metadataMap = node["metadata"] as Map<*, *>
        Assertions.assertEquals("1", metadataMap["key1"])
        Assertions.assertEquals("/a/b/1.txt", node["fullPath"])
    }

    @Test
    @DisplayName("元数据前缀匹配查询")
    fun testMetadataPrefixSearch() {
        nodeService.createNode(createRequest("/a/b/1.txt", false, metadata = mapOf("key" to "1")))
        nodeService.createNode(createRequest("/a/b/2.txt", false, metadata = mapOf("key" to "11")))
        nodeService.createNode(createRequest("/a/b/3.txt", false, metadata = mapOf("key" to "22")))
        nodeService.createNode(createRequest("/a/b/4.txt", false, metadata = mapOf("key" to "22", "key1" to "1")))

        val queryModel = createQueryBuilder()
            .metadata("key", "1", OperationType.PREFIX)
            .build()
        val result = nodeSearchService.search(queryModel)
        Assertions.assertEquals(2, result.totalRecords)
        Assertions.assertEquals(2, result.records.size)
    }

    @Test
    @DisplayName("元数据模糊匹配查询")
    fun testMetadataFuzzySearch() {
        nodeService.createNode(createRequest("/a/b/1.txt", false, metadata = mapOf("key" to "121")))
        nodeService.createNode(createRequest("/a/b/2.txt", false, metadata = mapOf("key" to "131")))
        nodeService.createNode(createRequest("/a/b/3.txt", false, metadata = mapOf("key" to "144")))

        val queryModel = createQueryBuilder()
            .metadata("key", "1*1", OperationType.MATCH)
            .build()
        val result = nodeSearchService.search(queryModel)
        Assertions.assertEquals(2, result.totalRecords)
        Assertions.assertEquals(2, result.records.size)
    }

    @Test
    fun testLocalDateTimeRuleInterceptor() {
        val now = LocalDateTime.now()
        val node1 = nodeService.createNode(
            createRequest(
                "/a/b/1.txt",
                false,
                metadata = mapOf("key" to "121"),
                createdDate = now.plusMinutes(1L)
            )
        )
        val node2 = nodeService.createNode(
            createRequest(
                "/a/b/2.txt",
                false,
                metadata = mapOf("key" to "131"),
                createdDate = now.plusMinutes(2L)
            )
        )
        nodeService.createNode(
            createRequest(
                "/a/b/3.txt",
                false,
                metadata = mapOf("key" to "131"),
                createdDate = now.plusMinutes(3L)
            )
        )

        // IN
        testLocalDateTimeOperation(listOf(node1.createdDate, node2.createdDate), OperationType.IN, 2L)
        // NIN
        testLocalDateTimeOperation(listOf(node1.createdDate, node2.createdDate), OperationType.NIN, 1L)
        // EQ
        testLocalDateTimeOperation(node1.createdDate, OperationType.EQ, 1L)
        // NE
        testLocalDateTimeOperation(node1.createdDate, OperationType.NE, 2L)
        val dateTime = now.plusMinutes(2L).format(DateTimeFormatter.ISO_DATE_TIME)
        // BEFORE
        testLocalDateTimeOperation(dateTime, OperationType.BEFORE, 1L)
        // AFTER
        testLocalDateTimeOperation(dateTime, OperationType.AFTER, 1L)
        // unsupported operation
        testLocalDateTimeOperation(dateTime, OperationType.GT, 0L)

        // unsupported value
        var queryModel = createQueryBuilder()
            .rule(NodeInfo::createdDate.name, now.plusMinutes(1), OperationType.EQ)
            .excludeFolder()
            .build()
        var result = nodeSearchService.search(queryModel)
        Assertions.assertEquals(1, result.totalRecords)
        Assertions.assertEquals(1, result.records.size)

        val millis = now.plusMinutes(1).toInstant(ZoneOffset.UTC).toEpochMilli()
        queryModel = createQueryBuilder()
            .rule(NodeInfo::createdDate.name, millis, OperationType.EQ)
            .excludeFolder()
            .build()
        result = nodeSearchService.search(queryModel)
        Assertions.assertEquals(0, result.totalRecords)
        Assertions.assertEquals(0, result.records.size)

        // illegal value
        queryModel = createQueryBuilder()
            .rule(NodeInfo::createdDate.name, "illegal", OperationType.EQ)
            .excludeFolder()
            .build()
        result = nodeSearchService.search(queryModel)
        Assertions.assertEquals(0, result.totalRecords)
        Assertions.assertEquals(0, result.records.size)
    }

    @Test
    fun testNoPermissionPathSearch() {
        val utRepoName2 = "$UT_REPO_NAME-2"
        val utRepoName3 = "$UT_REPO_NAME-3"
        whenever(servicePermissionClient.listPermissionPath(anyString(), anyString(), anyString())).thenReturn(
            ResponseBuilder.success(ListPathResult(status = true, path = mapOf(OperationType.NIN to listOf("/a"))))
        )
        whenever(servicePermissionClient.listPermissionRepo(anyString(), anyString(), isNull())).thenReturn(
            ResponseBuilder.success(listOf(UT_REPO_NAME, utRepoName2, utRepoName3))
        )
        // 创建仓库
        val repoCreateRequest = RepoCreateRequest(
            projectId = UT_PROJECT_ID,
            name = utRepoName2,
            type = RepositoryType.GENERIC,
            category = RepositoryCategory.LOCAL,
            public = false,
            operator = UT_USER,
        )
        repositoryService.createRepo(repoCreateRequest)
        repositoryService.createRepo(repoCreateRequest.copy(name = utRepoName3))

        // 创建node
        val createNodeRequest = createRequest("/a/a1.txt", false)
        nodeService.createNode(createNodeRequest)
        nodeService.createNode(createNodeRequest.copy(fullPath = "/b/b1.txt"))
        nodeService.createNode(createNodeRequest.copy(fullPath = "/c/c1.txt"))

        nodeService.createNode(createNodeRequest.copy(repoName = utRepoName2, fullPath = "/a/a2.txt"))
        nodeService.createNode(createNodeRequest.copy(repoName = utRepoName2, fullPath = "/b/b2.txt"))
        nodeService.createNode(createNodeRequest.copy(repoName = utRepoName2, fullPath = "/c/c2.txt"))

        nodeService.createNode(createNodeRequest.copy(repoName = utRepoName3, fullPath = "/a/a3.txt"))
        nodeService.createNode(createNodeRequest.copy(repoName = utRepoName3, fullPath = "/b/b3.txt"))
        nodeService.createNode(createNodeRequest.copy(repoName = utRepoName3, fullPath = "/c/c3.txt"))

        // 无仓库查询测试
        var result = nodeSearchService.search(NodeQueryBuilder().projectId(UT_PROJECT_ID).build())
        Assertions.assertEquals(18, result.totalRecords)

        // 单仓库查询
        var queryModel = createQueryBuilder().build()
        result = nodeSearchService.search(queryModel)
        Assertions.assertEquals(4, result.totalRecords)

        // 多仓库查询IN
        queryModel = NodeQueryBuilder()
            .projectId(UT_PROJECT_ID)
            .repoNames(utRepoName2, utRepoName3)
            .build()
        result = nodeSearchService.search(queryModel)
        Assertions.assertEquals(8, result.totalRecords)


        // 多仓库查询NIN
        val rules = mutableListOf(
            Rule.QueryRule(NodeInfo::projectId.name, UT_PROJECT_ID) as Rule,
            Rule.QueryRule(NodeInfo::repoName.name, listOf(utRepoName2, utRepoName3), OperationType.NIN) as Rule,
        )
        queryModel = QueryModel(sort = null, select = null, rule = Rule.NestedRule(rules))
        result = nodeSearchService.search(queryModel)
        Assertions.assertEquals(4, result.totalRecords)
    }

    private fun testLocalDateTimeOperation(
        createdDate: String,
        operationType: OperationType,
        expectedCount: Long
    ) {
        val queryModel = createQueryBuilder()
            .rule(NodeInfo::createdDate.name, createdDate, operationType)
            .excludeFolder()
            .build()
        val result = nodeSearchService.search(queryModel)
        Assertions.assertEquals(expectedCount, result.totalRecords)
        Assertions.assertEquals(expectedCount, result.records.size.toLong())
    }

    private fun testLocalDateTimeOperation(
        createdDate: List<String>,
        operationType: OperationType,
        expectedCount: Long
    ) {
        val queryModel = createQueryBuilder()
            .rule(NodeInfo::createdDate.name, createdDate, operationType)
            .excludeFolder()
            .build()
        val result = nodeSearchService.search(queryModel)
        Assertions.assertEquals(expectedCount, result.totalRecords)
        Assertions.assertEquals(expectedCount, result.records.size.toLong())
    }

    private fun createRequest(
        fullPath: String = "/a/b/c",
        folder: Boolean = true,
        size: Long = 1,
        metadata: Map<String, String>? = null,
        createdDate: LocalDateTime? = null
    ): NodeCreateRequest {
        return NodeCreateRequest(
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            folder = folder,
            fullPath = fullPath,
            expires = 0,
            overwrite = false,
            size = size,
            sha256 = "sha256",
            md5 = "md5",
            operator = UT_USER,
            nodeMetadata = metadata?.map { MetadataModel(key = it.key, value = it.value) },
            createdDate = createdDate,
            lastModifiedDate = createdDate
        )
    }

    private fun createQueryBuilder(): NodeQueryBuilder {
        return NodeQueryBuilder()
            .projectId(UT_PROJECT_ID)
            .repoName(UT_REPO_NAME)
            .page(1, 10)
            .sort(Sort.Direction.ASC, "fullPath")
            .select("projectId", "repoName", "fullPath", "metadata")
    }
}
