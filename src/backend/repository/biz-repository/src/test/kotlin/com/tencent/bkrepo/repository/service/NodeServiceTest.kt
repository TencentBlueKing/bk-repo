/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.  
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *
 */

package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.path.PathUtils.ROOT
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.repository.UT_PROJECT_ID
import com.tencent.bkrepo.repository.UT_REPO_DESC
import com.tencent.bkrepo.repository.UT_REPO_DISPLAY
import com.tencent.bkrepo.repository.UT_REPO_NAME
import com.tencent.bkrepo.repository.UT_USER
import com.tencent.bkrepo.repository.dao.FileReferenceDao
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.pojo.node.service.NodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import

@DisplayName("节点服务测试")
@DataMongoTest
@Import(
    NodeDao::class,
    FileReferenceDao::class
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NodeServiceTest @Autowired constructor(
    private val projectService: ProjectService,
    private val repositoryService: RepositoryService,
    private val nodeService: NodeService
) : ServiceBaseTest() {

    @BeforeAll
    fun beforeAll() {
        initMock()

        if (!projectService.exist(UT_PROJECT_ID)) {
            val projectCreateRequest = ProjectCreateRequest(UT_PROJECT_ID, UT_REPO_NAME, UT_REPO_DISPLAY, UT_USER)
            projectService.create(projectCreateRequest)
        }
        if (!repositoryService.exist(UT_PROJECT_ID, UT_REPO_NAME)) {
            val repoCreateRequest = RepoCreateRequest(
                projectId = UT_PROJECT_ID,
                name = UT_REPO_NAME,
                type = RepositoryType.GENERIC,
                category = RepositoryCategory.LOCAL,
                public = false,
                description = UT_REPO_DESC,
                configuration = LocalConfiguration(),
                operator = UT_USER
            )
            repositoryService.create(repoCreateRequest)
        }
    }

    @BeforeEach
    fun beforeEach() {
        initMock()
        nodeService.deleteByPath(UT_PROJECT_ID, UT_REPO_NAME, ROOT, UT_USER)
    }

    @Test
    @DisplayName("测试根节点")
    fun testRootNode() {
        // 查询根节点，一直存在
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, ""))
        val nodeDetail = nodeService.detail(UT_PROJECT_ID, UT_REPO_NAME, "")
        assertNotNull(nodeDetail)
        assertEquals("", nodeDetail?.name)
        assertEquals(ROOT, nodeDetail?.path)
        assertEquals(ROOT, nodeDetail?.fullPath)
    }

    @Test
    @DisplayName("测试创建文件")
    fun testCreateFile() {
        nodeService.create(createRequest("/1/2/3.txt", folder = false, size = 100, metadata = mapOf("key" to "value")))
        val node = nodeService.detail(UT_PROJECT_ID, UT_REPO_NAME, "/1/2/3.txt")!!
        assertEquals(UT_USER, node.createdBy)
        assertNotNull(node.createdDate)
        assertEquals(UT_USER, node.lastModifiedBy)
        assertNotNull(node.lastModifiedDate)
        assertFalse(node.folder)
        assertEquals("/1/2/", node.path)
        assertEquals("3.txt", node.name)
        assertEquals("/1/2/3.txt", node.fullPath)
        assertEquals(100, node.size)
        assertEquals("sha256", node.sha256)
        assertEquals("md5", node.md5)
        assertEquals("value", node.metadata["key"])
    }

    @Test
    @DisplayName("测试创建目录")
    fun testCreateDir() {
        nodeService.create(createRequest("/1/2/3.txt", folder = true, size = 100, metadata = mapOf("key" to "value")))
        val node = nodeService.detail(UT_PROJECT_ID, UT_REPO_NAME, "/1/2/3.txt")!!
        assertEquals(UT_USER, node.createdBy)
        assertNotNull(node.createdDate)
        assertEquals(UT_USER, node.lastModifiedBy)
        assertNotNull(node.lastModifiedDate)
        assertTrue(node.folder)
        assertEquals("/1/2/", node.path)
        assertEquals("3.txt", node.name)
        assertEquals("/1/2/3.txt", node.fullPath)
        assertEquals(0, node.size)
        assertNull(node.sha256)
        assertNull(node.md5)
        assertEquals("value", node.metadata["key"])
    }

    @Test
    @DisplayName("测试自动创建父目录")
    fun testCreateParentPath() {
        nodeService.create(createRequest("/1/2/3.txt", folder = false))
        assertNotNull(nodeService.detail(UT_PROJECT_ID, UT_REPO_NAME, ""))
        assertNotNull(nodeService.detail(UT_PROJECT_ID, UT_REPO_NAME, "/1"))
        assertNotNull(nodeService.detail(UT_PROJECT_ID, UT_REPO_NAME, "/1/2"))
        assertNotNull(nodeService.detail(UT_PROJECT_ID, UT_REPO_NAME, "/1/2/3.txt"))

        nodeService.create(createRequest("/a/b/c", folder = true))
        assertNotNull(nodeService.detail(UT_PROJECT_ID, UT_REPO_NAME, ""))
        assertNotNull(nodeService.detail(UT_PROJECT_ID, UT_REPO_NAME, "/a"))
        assertNotNull(nodeService.detail(UT_PROJECT_ID, UT_REPO_NAME, "/a/b"))
        assertNotNull(nodeService.detail(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c"))
    }

    @Test
    @DisplayName("测试使用.路径创建节点")
    fun testCreateWithDot() {
        nodeService.create(createRequest("/ a / b / . / 2.txt", true))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "./a/b/./2.txt"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/"))
    }

    @Test
    @DisplayName("测试使用..路径创建节点")
    fun testCreateWithDoubleDot() {
        nodeService.create(createRequest("/a/b/ .. ", true))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b"))
        // /aa/bb/.. 应该存在，因为会格式化
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/c/.././a/b/.."))

        nodeService.create(createRequest("/aa/bb/ . . ", true))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/aa"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/aa/bb"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/aa/bb/. ."))
    }


    @Test
    @DisplayName("测试元数据查询")
    fun testIncludeMetadata() {
        nodeService.create(createRequest("/a/b/1.txt", folder = false, metadata = mapOf("key" to "value")))
        nodeService.create(createRequest("/a/b/2.txt", folder = false))

        val node1 = nodeService.detail(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/1.txt")
        val node2 = nodeService.detail(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/2.txt")
        assertNotNull(node1!!.metadata)
        assertNotNull(node1.metadata["key"])
        assertNotNull(node2!!.metadata)
    }

    @Test
    @DisplayName("测试列表查询")
    fun testListNode() {
        assertEquals(0, nodeService.list(UT_PROJECT_ID, UT_REPO_NAME, "", includeFolder = false, deep = false).size)

        nodeService.create(createRequest("/a/b/1.txt", false))
        assertEquals(1, nodeService.list(UT_PROJECT_ID, UT_REPO_NAME, "", includeFolder = true, deep = false).size)

        val size = 20
        repeat(size) { i -> nodeService.create(createRequest("/a/b/c/$i.txt", false)) }
        repeat(size) { i -> nodeService.create(createRequest("/a/b/d/$i.txt", false)) }

        assertEquals(1, nodeService.list(UT_PROJECT_ID, UT_REPO_NAME, "/a/b", includeFolder = false, deep = false).size)
        assertEquals(3, nodeService.list(UT_PROJECT_ID, UT_REPO_NAME, "/a/b", includeFolder = true, deep = false).size)
        assertEquals(size, nodeService.list(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c", includeFolder = true, deep = true).size)
        assertEquals(size * 2 + 1, nodeService.list(UT_PROJECT_ID, UT_REPO_NAME, "/a/b", includeFolder = false, deep = true).size)
        assertEquals(size * 2 + 1 + 2, nodeService.list(UT_PROJECT_ID, UT_REPO_NAME, "/a/b", includeFolder = true, deep = true).size)
        assertEquals(size * 2 + 1 + 2 + 2, nodeService.list(UT_PROJECT_ID, UT_REPO_NAME, "/", includeFolder = true, deep = true).size)
    }

    @Test
    @DisplayName("测试分页查询")
    fun testListNodePage() {
        val size = 51L
        repeat(size.toInt()) { i -> nodeService.create(createRequest("/a/b/c/$i.txt", false)) }

        // 测试从第0页开始，兼容性测试
        var page = nodeService.page(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c", 0, 10, includeFolder = false, deep = false)
        assertEquals(10, page.records.size)
        assertEquals(size, page.totalRecords)
        assertEquals(6, page.totalPages)
        assertEquals(10, page.pageSize)
        assertEquals(1, page.pageNumber)

        page = nodeService.page(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c", 1, 10, includeFolder = false, deep = false)
        assertEquals(10, page.records.size)
        assertEquals(size, page.totalRecords)
        assertEquals(6, page.totalPages)
        assertEquals(10, page.pageSize)
        assertEquals(1, page.pageNumber)

        page = nodeService.page(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c", 6, 10, includeFolder = false, deep = false)
        assertEquals(1, page.records.size)
        assertEquals(size, page.totalRecords)
        assertEquals(6, page.totalPages)
        assertEquals(10, page.pageSize)
        assertEquals(6, page.pageNumber)

        // 测试空页码
        page = nodeService.page(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c", 7, 10, includeFolder = false, deep = false)
        assertEquals(0, page.records.size)
        assertEquals(size, page.totalRecords)
        assertEquals(6, page.totalPages)
        assertEquals(10, page.pageSize)
        assertEquals(7, page.pageNumber)
    }

    @Test
    @DisplayName("测试删除文件")
    fun testDeleteFile() {
        nodeService.create(createRequest("/a/b/1.txt", false))
        nodeService.delete(
            NodeDeleteRequest(
                projectId = UT_PROJECT_ID,
                repoName = UT_REPO_NAME,
                fullPath = "/a/b/1.txt",
                operator = UT_USER
            )
        )
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/1.txt"))
    }

    @Test
    @DisplayName("测试删除目录")
    fun testDeleteDir() {
        nodeService.create(createRequest("/a/b/1.txt", false))
        nodeService.delete(
            NodeDeleteRequest(
                projectId = UT_PROJECT_ID,
                repoName = UT_REPO_NAME,
                fullPath = "/a",
                operator = UT_USER
            )
        )
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/1.txt"))
    }

    @Test
    @DisplayName("测试正则转义")
    fun testWindowsSeparator() {
        nodeService.create(createRequest("/a\\b\\\\c\\/\\d", false))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c/d"))
    }

    @Test
    @DisplayName("测试正则转义")
    fun testEscape() {
        nodeService.create(createRequest("/.*|^/a/1.txt", false))
        nodeService.create(createRequest("/a/1.txt", false))

        assertEquals(1, nodeService.list(UT_PROJECT_ID, UT_REPO_NAME, "/.*|^/a", includeFolder = true, deep = true).size)
        nodeService.deleteByPath(UT_PROJECT_ID, UT_REPO_NAME, "/.*|^/a", UT_USER)
        assertEquals(0, nodeService.list(UT_PROJECT_ID, UT_REPO_NAME, "/.*|^/a", includeFolder = true, deep = true).size)
        assertEquals(1, nodeService.list(UT_PROJECT_ID, UT_REPO_NAME, "/a", includeFolder = true, deep = true).size)
    }

    @Test
    @DisplayName("测试特殊字符")
    fun testSpecialCharacter() {
        nodeService.create(createRequest("/~`!@#$%^&*()_-+=<,>.?/:;\"'{[}]|"))
        val nodeDetail = nodeService.detail(UT_PROJECT_ID, UT_REPO_NAME, "/~`!@#$%^&*()_-+=<,>.?/:;\"'{[}]|\\")
        assertEquals("/~`!@#$%^&*()_-+=<,>.?/:;\"'{[}]|", nodeDetail?.fullPath)
    }

    @Test
    @DisplayName("测试计算目录大小")
    fun testComputeDirSize() {
        val size = 20
        repeat(size) { i -> nodeService.create(createRequest("/a/b/c/$i.txt", false)) }
        repeat(size) { i -> nodeService.create(createRequest("/a/b/d/$i.txt", false)) }

        val pathSizeInfo = nodeService.computeSize(UT_PROJECT_ID, UT_REPO_NAME, "/a/b")

        assertEquals(42, pathSizeInfo.subNodeCount)
        assertEquals(40, pathSizeInfo.size)
    }

    @Test
    @DisplayName("测试计算文件大小")
    fun testComputeFileSize() {
        nodeService.create(createRequest("/a/b/c/1.txt", false))

        val fileSizeInfo = nodeService.computeSize(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c/1.txt")
        assertEquals(0, fileSizeInfo.subNodeCount)
        assertEquals(1, fileSizeInfo.size)
    }

    @Test
    @DisplayName("测试计算根节点大小")
    fun testComputeRootNodeSize() {
        val size = 20
        repeat(size) { i -> nodeService.create(createRequest("/a/$i.txt", false)) }
        repeat(size) { i -> nodeService.create(createRequest("/b/$i.txt", false)) }

        val pathSizeInfo = nodeService.computeSize(UT_PROJECT_ID, UT_REPO_NAME, "/")

        assertEquals(42, pathSizeInfo.subNodeCount)
        assertEquals(40, pathSizeInfo.size)
    }

    @Test
    @DisplayName("重命名目录")
    fun testRenamePath() {
        nodeService.create(createRequest("/a/b/1.txt", false))
        nodeService.create(createRequest("/a/b/2.txt", false))
        nodeService.create(createRequest("/a/b/c/1.txt", false))
        nodeService.create(createRequest("/a/b/c/2.txt", false))

        val renameRequest = NodeRenameRequest(
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            fullPath = "/a",
            newFullPath = "/aa",
            operator = UT_USER
        )
        nodeService.rename(renameRequest)

        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/1.txt"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c/2.txt"))

        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/aa"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/aa/b"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/aa/b/c"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/aa/b/1.txt"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/aa/b/c/2.txt"))
    }

    @Test
    @DisplayName("重命名中间目录")
    fun testRenameSubPath() {
        nodeService.create(createRequest("/a/b/c", true))

        val renameRequest = NodeRenameRequest(
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            fullPath = "/a/b/c",
            newFullPath = "/a/d/c",
            operator = UT_USER
        )
        nodeService.rename(renameRequest)

        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c"))

        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/d"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/d/c"))
    }

    @Test
    @DisplayName("重命名文件，遇同名文件抛异常")
    fun testRenameThrow() {
        nodeService.create(createRequest("/a/b/1.txt", false))
        nodeService.create(createRequest("/a/b/2.txt", false))
        nodeService.create(createRequest("/a/b/c/1.txt", false))
        nodeService.create(createRequest("/a/b/c/2.txt", false))

        nodeService.create(createRequest("/aa/b/c/2.txt", false))

        val renameRequest = NodeRenameRequest(
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            fullPath = "/a",
            newFullPath = "/aa",
            operator = UT_USER
        )
        assertThrows<ErrorCodeException> { nodeService.rename(renameRequest) }
    }

    @Test
    @DisplayName("移动文件，目录 -> 不存在的目录")
    fun testMovePathToNotExistPath() {
        nodeService.create(createRequest("/a/b/c/d/1.txt", false))
        nodeService.create(createRequest("/a/b/c/d/2.txt", false))
        nodeService.create(createRequest("/a/b/c/d/e/1.txt", false))
        nodeService.create(createRequest("/a/b/c/d/e/2.txt", false))
        nodeService.create(createRequest("/a/1.txt", false))

        val moveRequest = NodeMoveRequest(
            srcProjectId = UT_PROJECT_ID,
            srcRepoName = UT_REPO_NAME,
            srcFullPath = "/a/b",
            destFullPath = "/ab",
            operator = UT_USER
        )
        nodeService.move(moveRequest)

        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/1.txt"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c/d"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c/d/e"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c/d/1.txt"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c/d/e/2.txt"))

        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/ab"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/ab/c"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/ab/c/d"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/ab/c/d/e"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/ab/c/d/1.txt"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/ab/c/d/e/2.txt"))
    }

    @Test
    @DisplayName("移动文件，目录 -> 存在的目录")
    fun testMovePathToExistPath() {
        nodeService.create(createRequest("/a/b/c/d/1.txt", false))
        nodeService.create(createRequest("/a/b/c/d/2.txt", false))
        nodeService.create(createRequest("/a/b/c/d/e/1.txt", false))
        nodeService.create(createRequest("/a/b/c/d/e/2.txt", false))
        nodeService.create(createRequest("/a/1.txt", false))
        nodeService.create(createRequest("/ab", true))

        var moveRequest = NodeMoveRequest(
            srcProjectId = UT_PROJECT_ID,
            srcRepoName = UT_REPO_NAME,
            srcFullPath = "/a/b",
            destFullPath = "/ab",
            operator = UT_USER
        )
        nodeService.move(moveRequest)

        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/1.txt"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c/d"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c/d/e"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c/d/1.txt"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c/d/e/2.txt"))

        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/ab"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/ab/b"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/ab/b/c"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/ab/b/c/d"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/ab/b/c/d/1.txt"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/ab/b/c/d/e/2.txt"))

        nodeService.create(createRequest("/data/mkdir/aa.txt", false))
        nodeService.create(createRequest("/data/dir3", true))

        moveRequest = NodeMoveRequest(
            srcProjectId = UT_PROJECT_ID,
            srcRepoName = UT_REPO_NAME,
            srcFullPath = "/data/mkdir/aa.txt",
            destFullPath = "/data/dir3",
            operator = UT_USER
        )
        nodeService.move(moveRequest)
        assertTrue(nodeService.list(UT_PROJECT_ID, UT_REPO_NAME, "/data/dir3", includeFolder = false, deep = false).size == 1)
    }

    @Test
    @DisplayName("移动文件 -> 存在的目录")
    fun testMoveFileToExistPath() {
        nodeService.create(createRequest("/a/1.txt", false))
        nodeService.create(createRequest("/ab", true))

        val moveRequest = NodeMoveRequest(
            srcProjectId = UT_PROJECT_ID,
            srcRepoName = UT_REPO_NAME,
            srcFullPath = "/a/1.txt",
            destFullPath = "/ab",
            operator = UT_USER
        )
        nodeService.move(moveRequest)

        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/1.txt"))

        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/ab"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/ab/1.txt"))
    }

    @Test
    @DisplayName("移动文件，文件 -> 不存在的路径")
    fun testMoveFileToNotExistPath() {
        nodeService.create(createRequest("/a/1.txt", false))

        val moveRequest = NodeMoveRequest(
            srcProjectId = UT_PROJECT_ID,
            srcRepoName = UT_REPO_NAME,
            srcFullPath = "/a/1.txt",
            destFullPath = "/ab",
            operator = UT_USER
        )
        nodeService.move(moveRequest)

        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/1.txt"))

        val destNode = nodeService.detail(UT_PROJECT_ID, UT_REPO_NAME, "/ab")
        assertNotNull(destNode)
        assertFalse(destNode!!.folder)
    }

    @Test
    @DisplayName("移动文件，文件 -> 存在的文件且覆盖")
    fun testMoveFileToExistFileAndOverwrite() {
        nodeService.create(createRequest("/a/1.txt", false, size = 1))
        nodeService.create(createRequest("/ab/a/1.txt", false, size = 2))
        nodeService.create(createRequest("/abc/a/1.txt", false, size = 2))

        // path -> path
        var moveRequest = NodeMoveRequest(
            srcProjectId = UT_PROJECT_ID,
            srcRepoName = UT_REPO_NAME,
            srcFullPath = "/a",
            destFullPath = "/ab",
            operator = UT_USER,
            overwrite = true
        )
        nodeService.move(moveRequest)

        // file -> file
        var node = nodeService.detail(UT_PROJECT_ID, UT_REPO_NAME, "/ab/a/1.txt")!!
        assertEquals(1, node.size)

        moveRequest = NodeMoveRequest(
            srcProjectId = UT_PROJECT_ID,
            srcRepoName = UT_REPO_NAME,
            srcFullPath = "/ab/a/1.txt",
            destFullPath = "/abc/a/1.txt",
            operator = UT_USER,
            overwrite = true
        )
        nodeService.move(moveRequest)

        node = nodeService.detail(UT_PROJECT_ID, UT_REPO_NAME, "/abc/a/1.txt")!!
        assertEquals(1, node.size)
    }

    @Test
    @DisplayName("移动文件，文件 -> 存在的文件且不覆盖")
    fun testMoveOverwriteThrow() {
        nodeService.create(createRequest("/a/1.txt", false))
        nodeService.create(createRequest("/ab/a/1.txt", false))

        val moveRequest = NodeMoveRequest(
            srcProjectId = UT_PROJECT_ID,
            srcRepoName = UT_REPO_NAME,
            srcFullPath = "/a",
            destFullPath = "/ab",
            operator = UT_USER
        )
        assertThrows<ErrorCodeException> { nodeService.move(moveRequest) }
    }

    @Test
    @DisplayName("移动文件，目录 -> 自己")
    fun testMovePathToSelf() {
        nodeService.create(createRequest("/a/1.txt", false))
        nodeService.create(createRequest("/a/b/1.txt", false))

        val moveRequest = NodeMoveRequest(
            srcProjectId = UT_PROJECT_ID,
            srcRepoName = UT_REPO_NAME,
            srcFullPath = "/a",
            destFullPath = "/a",
            operator = UT_USER
        )
        nodeService.move(moveRequest)
    }

    @Test
    @DisplayName("移动文件, 目录 -> 父目录")
    fun testMovePathToParentPath() {
        nodeService.create(createRequest("/a/b/1.txt", false))

        val moveRequest = NodeMoveRequest(
            srcProjectId = UT_PROJECT_ID,
            srcRepoName = UT_REPO_NAME,
            srcFullPath = "/a/b",
            destFullPath = "/a",
            operator = UT_USER
        )
        nodeService.move(moveRequest)

        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/1.txt"))
    }

    @Test
    @DisplayName("移动文件, 目录 -> 根目录")
    fun testMoveToRootPath() {
        nodeService.create(createRequest("/a/b/1.txt", false))

        val moveRequest = NodeMoveRequest(
            srcProjectId = UT_PROJECT_ID,
            srcRepoName = UT_REPO_NAME,
            srcFullPath = "/a/b",
            destFullPath = "",
            operator = UT_USER
        )
        nodeService.move(moveRequest)
        nodeService.list(UT_PROJECT_ID, UT_REPO_NAME, "").forEach {
            println("path: ${it.path}, name: ${it.name}, fullPath: ${it.fullPath}")
        }
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/b/1.txt"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a"))
        assertFalse(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b"))
    }

    @Test
    @DisplayName("移动文件, 文件 -> 父目录")
    fun testMoveFileToRootPath() {
        nodeService.create(createRequest("/a/b/1.txt", false))

        val moveRequest = NodeMoveRequest(
            srcProjectId = UT_PROJECT_ID,
            srcRepoName = UT_REPO_NAME,
            srcFullPath = "/a/b/1.txt",
            destFullPath = "/a/b",
            operator = UT_USER
        )
        nodeService.move(moveRequest)
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/1.txt"))
    }

    @Test
    @DisplayName("拷贝文件, 目录 -> 不存在的目录")
    fun testCopy() {
        nodeService.create(createRequest("/a/b/c/d/1.txt", false))
        nodeService.create(createRequest("/a/b/c/d/2.txt", false))
        nodeService.create(createRequest("/a/b/c/d/e/1.txt", false))
        nodeService.create(createRequest("/a/b/c/d/e/2.txt", false))
        nodeService.create(createRequest("/a/1.txt", false))

        val copyRequest = NodeCopyRequest(
            srcProjectId = UT_PROJECT_ID,
            srcRepoName = UT_REPO_NAME,
            srcFullPath = "/a/b",
            destFullPath = "/ab",
            operator = UT_USER
        )
        nodeService.copy(copyRequest)

        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/1.txt"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c/d"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c/d/e"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c/d/1.txt"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/a/b/c/d/e/2.txt"))

        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/ab"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/ab/c"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/ab/c/d"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/ab/c/d/e"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/ab/c/d/1.txt"))
        assertTrue(nodeService.exist(UT_PROJECT_ID, UT_REPO_NAME, "/ab/c/d/e/2.txt"))
    }

    @Test
    @DisplayName("拷贝文件 -> 存在的目录")
    fun testCopyFileToExistPath() {
        nodeService.create(createRequest("/a/1.txt", false))
        nodeService.create(createRequest("/b", true))

        val copyRequest = NodeCopyRequest(
            srcProjectId = UT_PROJECT_ID,
            srcRepoName = UT_REPO_NAME,
            srcFullPath = "/a/1.txt",
            destFullPath = "/b/",
            operator = UT_USER,
            overwrite = true
        )
        nodeService.copy(copyRequest)

        assertTrue(nodeService.detail(UT_PROJECT_ID, UT_REPO_NAME, "/b")?.folder == true)
        assertTrue(nodeService.detail(UT_PROJECT_ID, UT_REPO_NAME, "/b/1.txt")?.folder == false)
        nodeService.list(UT_PROJECT_ID, UT_REPO_NAME, "/", true, deep = true).forEach { println(it) }
    }

    @Test
    @DisplayName("拷贝文件, 元数据一起拷贝")
    fun testCopyWithMetadata() {
        nodeService.create(createRequest("/a", false, metadata = mapOf("key" to "value")))

        val copyRequest = NodeCopyRequest(
            srcProjectId = UT_PROJECT_ID,
            srcRepoName = UT_REPO_NAME,
            srcFullPath = "/a",
            destFullPath = "/b",
            operator = UT_USER
        )
        nodeService.copy(copyRequest)
        assertEquals("value", nodeService.detail(UT_PROJECT_ID, UT_REPO_NAME, "/b")!!.metadata["key"])
    }

    private fun createRequest(fullPath: String = "/a/b/c", folder: Boolean = true, size: Long = 1, metadata: Map<String, String>? = null): NodeCreateRequest {
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
            metadata = metadata
        )
    }
}
