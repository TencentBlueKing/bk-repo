/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.UT_PROJECT_ID
import com.tencent.bkrepo.repository.UT_REPO_NAME
import com.tencent.bkrepo.repository.UT_USER
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import com.tencent.bkrepo.common.metadata.dao.file.FileReferenceDao
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

@DisplayName("节点不带ShardingKey查询测试")
@DataMongoTest
@Import(NodeDao::class, FileReferenceDao::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = ["sharding.count:$SHARDING_COUNT"])
class NodeQueryWithoutShardingKeyTest @Autowired constructor(
    private val projectService: ProjectService,
    private val repositoryService: RepositoryService,
    private val nodeService: NodeService
) : ServiceBaseTest() {

    // 创建测试数据sha256为sha256-0,sha256-1...sha256-8,sha256-9
    // sha256-0和sha256-1有18条数据，其余sha256各有15条数据
    val generateSha256Func = { i: Int -> "sha256-${i % 10}" }

    @BeforeAll
    fun beforeAll() {
        initMock()
        generateTestData(52, generateSha256Func)
    }

    @Test
    @DisplayName("测试按SHA256分页查询")
    fun testListNodePageBySha256() {
        val option = NodeListOption(1, 5, includeMetadata = true, sort = true)
        // 测试获取不存在的Node列表
        nodeService.listNodePageBySha256("notExistsSha256", option).apply {
            assertEquals(0L, totalRecords)
            assertEquals(0L, totalPages)
            assertTrue(records.isEmpty())
        }

        // 测试数据量小于pageSize的情况
        nodeService.listNodePageBySha256(generateSha256Func(1), option.copy(pageSize = 20)).apply {
            assertEquals(18, totalRecords)
            assertEquals(1, totalPages)
            assertEquals(18, records.size)
        }

        // 测试获取数据量等于pageSize的页
        nodeService.listNodePageBySha256(generateSha256Func(1), option.copy(pageSize = 4)).apply {
            assertEquals(18, totalRecords)
            assertEquals(5, totalPages)
            assertEquals(4, records.size)
        }


        // 测试获数据量小于pageSize的页
        nodeService.listNodePageBySha256(generateSha256Func(1), option.copy(pageSize = 4, pageNumber = 5)).apply {
            assertEquals(18, totalRecords)
            assertEquals(5, totalPages)
            assertEquals(2, records.size)
        }

        // 测试获取不存在的页
        nodeService.listNodePageBySha256(generateSha256Func(1), option.copy(pageSize = 4, pageNumber = 6)).apply {
            assertEquals(18, totalRecords)
            assertEquals(5, totalPages)
            assertEquals(0, records.size)
        }

        // 测试分页数据在两个分表的情况,[0,1,2,3,4,  5][6,7,8,9,  10,11][12,13,14,15,16,17]
        nodeService.listNodePageBySha256(generateSha256Func(1), option.copy(pageSize = 5, pageNumber = 2)).apply {
            assertEquals(18, totalRecords)
            assertEquals(4, totalPages)
            assertEquals(5, records.size)
            assertEquals(PROJECT_SHARDING_207, records[0].projectId)
            assertEquals(PROJECT_SHARDING_208, records[1].projectId)
            assertEquals(PROJECT_SHARDING_208, records[2].projectId)
            assertEquals(PROJECT_SHARDING_208, records[3].projectId)
            assertEquals(PROJECT_SHARDING_208, records[4].projectId)
        }
    }

    @Test
    @DisplayName("测试按SHA256查询")
    fun testListNodeBySha256() {
        val sha256 = generateSha256Func(1)

        // 测试获取不存在的Node列表
        nodeService.listNodeBySha256("notExistsSha256").apply { assertEquals(0, size) }

        // 测试单表数据量小于limit的情况
        nodeService.listNodeBySha256(sha256, 20, tillLimit = false).apply { assertEquals(18, size) }
        nodeService.listNodeBySha256(sha256, 100, tillLimit = true).apply { assertEquals(20, size) }

        // 测试单表数据量等于limit的页
        nodeService.listNodeBySha256(sha256, 18, tillLimit = true).apply { assertEquals(18, size) }
        nodeService.listNodeBySha256(sha256, 18, tillLimit = false).apply { assertEquals(18, size) }

        // 测试获数据量小于pageSize的页
        nodeService.listNodeBySha256(sha256, 10, tillLimit = true).apply { assertEquals(10, size) }
        nodeService.listNodeBySha256(sha256, 10, tillLimit = false).apply { assertEquals(10, size) }
    }

    private fun generateTestData(size: Int, generateSha256Func: (Int) -> String) {
        for (projectId in arrayOf(PROJECT_SHARDING_207, PROJECT_SHARDING_208, PROJECT_SHARDING_209)) {
            createProject(projectService, projectId)
            createRepository(repositoryService, projectId = projectId)
            repeat(size) { i ->
                createNode("/a/b/c/$i.txt", false, sha256 = generateSha256Func(i), projectId = projectId)
            }
        }
    }

    private fun createNode(
        fullPath: String,
        folder: Boolean = true,
        metadata: List<MetadataModel>? = null,
        sha256: String? = null,
        projectId: String = UT_PROJECT_ID,
        repoName: String = UT_REPO_NAME
    ): NodeDetail {
        val request = NodeCreateRequest(
            projectId = projectId,
            repoName = repoName,
            folder = folder,
            fullPath = fullPath,
            expires = 0,
            overwrite = false,
            size = 1L,
            sha256 = sha256,
            md5 = "md5",
            operator = UT_USER,
            nodeMetadata = metadata
        )
        return nodeService.createNode(request)
    }

    private companion object {
        private const val PROJECT_SHARDING_207 = "$UT_PROJECT_ID-1"
        private const val PROJECT_SHARDING_208 = "$UT_PROJECT_ID-2"
        private const val PROJECT_SHARDING_209 = "$UT_PROJECT_ID-3"
    }
}
