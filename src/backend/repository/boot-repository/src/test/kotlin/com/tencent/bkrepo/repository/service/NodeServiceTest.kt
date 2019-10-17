package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.repository.constant.enum.RepositoryCategoryEnum
import com.tencent.bkrepo.repository.pojo.node.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import org.apache.commons.lang.RandomStringUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * NodeServiceTest
 *
 * @author: carrypan
 * @date: 2019-09-23
 */
@DisplayName("节点服务测试")
@SpringBootTest
internal class NodeServiceTest @Autowired constructor(
    private val nodeService: NodeService,
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
    fun list() {
        nodeService.create(createRequest("/a/b"))
        nodeService.create(createRequest("/a/b/1.txt", false))
        val size = 20
        repeat(size) { i -> nodeService.create(createRequest("/a/b/c/$i.txt", false)) }
        repeat(size) { i -> nodeService.create(createRequest("/a/b/d/$i.txt", false)) }

        assertEquals(1, nodeService.list(projectId, repoName, "/a/b", includeFolder = false, deep = false).size)
        assertEquals(3, nodeService.list(projectId, repoName, "/a/b", includeFolder = true, deep = false).size)
        assertEquals(size*2 + 1, nodeService.list(projectId, repoName, "/a/b", includeFolder = false, deep = true).size)
        assertEquals(size*2 + 3, nodeService.list(projectId, repoName, "/a/b", includeFolder = true, deep = true).size)
        assertEquals(size, nodeService.list(projectId, repoName, "/a/b/c", includeFolder = true, deep = true).size)
    }

    @Test
    fun list2() {
        nodeService.create(createRequest("/a/"))
        nodeService.create(createRequest("/b"))
        val size = 20
        repeat(size) { i -> nodeService.create(createRequest("/a/$i.txt", false)) }
        repeat(size) { i -> nodeService.create(createRequest("/b/$i.txt", false)) }

        assertEquals(0, nodeService.list(projectId, repoName, "", includeFolder = false, deep = false).size)
        assertEquals(2, nodeService.list(projectId, repoName, "", includeFolder = true, deep = false).size)
        assertEquals(42, nodeService.list(projectId, repoName, "", includeFolder = true, deep = true).size)
    }

    @Test
    fun page() {
        nodeService.create(createRequest("/a/b/c"))

        val size = 51L
        repeat(size.toInt()) { i -> nodeService.create(createRequest("/a/b/c/$i.txt", false)) }

        var page = nodeService.page(projectId, repoName, "/a/b/c", 0, 10, includeFolder = false, deep = false)
        assertEquals(10, page.records.size)
        assertEquals(size, page.count)
        assertEquals(0, page.page)
        assertEquals(10, page.pageSize)

        page = nodeService.page(projectId, repoName, "/a/b/c", 5, 10, includeFolder = false, deep = false)
        assertEquals(1, page.records.size)
        page = nodeService.page(projectId, repoName, "/a/b/c", 6, 10, includeFolder = false, deep = false)
        assertEquals(0, page.records.size)

        page = nodeService.page(projectId, repoName, "/a/b/c", 0, 20, includeFolder = false, deep = false)
        assertEquals(20, page.records.size)
        assertEquals(size, page.count)
        assertEquals(0, page.page)
        assertEquals(20, page.pageSize)
    }

    @Test
    fun exist() {
        assertTrue(nodeService.exist(projectId, repoName, ""))
        assertTrue(nodeService.exist(projectId, repoName, "/"))

        createRequest("  / a /   b /  1.txt   ", false)
        nodeService.create(createRequest("  / a /   b /  1.txt   ", false))
        assertTrue(nodeService.exist(projectId, repoName, "/a/b/1.txt"))
        assertTrue(nodeService.exist(projectId, repoName, "/a"))
        assertTrue(nodeService.exist(projectId, repoName, "/a/b"))
    }

    @Test
    fun createThrow() {
        assertThrows<ErrorCodeException> { nodeService.create(createRequest(" a /   b /  1.txt   ", false))}
        assertThrows<ErrorCodeException> { nodeService.create(createRequest(" a /   b /  1./txt   ", false))}
        assertThrows<ErrorCodeException> { nodeService.create(createRequest("/a/b/..", true))}
        assertThrows<ErrorCodeException> { nodeService.create(createRequest("/a/b/.", true))}
        nodeService.create(createRequest("/a/b", true))
    }

    @Test
    fun createFile() {
        nodeService.create(createRequest("  / a /   b /  1.txt  ", false))
        assertThrows<ErrorCodeException> { nodeService.create(createRequest("  / a /   b /  1.txt  ", false)) }
        val node = nodeService.queryDetail(projectId, repoName, "/a/b/1.txt")!!.nodeInfo

        assertEquals(operator, node.createdBy)
        assertNotNull(node.createdDate)
        assertEquals(operator, node.lastModifiedBy)
        assertNotNull(node.lastModifiedDate)

        assertEquals(false, node.folder)
        assertEquals("/a/b/", node.path)
        assertEquals("1.txt", node.name)
        assertEquals("/a/b/1.txt", node.fullPath)
        assertEquals(1L, node.size)
        assertEquals("sha256", node.sha256)
    }

    @Test
    fun createDir() {
        nodeService.create(createRequest("  /// a /   c ////    中文.@_-`~...  "))
        val node = nodeService.queryDetail(projectId, repoName, "/a/c/中文.@_-`~...")!!.nodeInfo

        assertEquals(operator, node.createdBy)
        assertNotNull(node.createdDate)
        assertEquals(operator, node.lastModifiedBy)
        assertNotNull(node.lastModifiedDate)

        assertEquals(true, node.folder)
        assertEquals("/a/c/", node.path)
        assertEquals("中文.@_-`~...", node.name)
        assertEquals("/a/c/中文.@_-`~...", node.fullPath)
        assertEquals(0L, node.size)
        assertEquals(null, node.sha256)
    }


    @Test
    fun softDeleteById() {
        nodeService.create(createRequest("/a/b/1.txt", false))
        nodeService.delete(NodeDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = "/a/b/1.txt",
                operator = operator
        ))

        assertFalse(nodeService.exist(projectId, repoName, "/a/b/1.txt"))

        nodeService.create(createRequest("/a/b/1.txt"))

        nodeService.create(createRequest("/a/b/c"))
        nodeService.create(createRequest("/a/b/c/1.txt", false))

        assertTrue(nodeService.exist(projectId, repoName, "/a/b/c/1.txt"))

        nodeService.delete(NodeDeleteRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = "/a/b/c/1.txt",
                operator = operator
        ))

        assertFalse(nodeService.exist(projectId, repoName, "/a/b/c/1.txt"))

    }

    @Test
    fun escapeTest() {
        nodeService.create(createRequest("/.*|^/a/1.txt", false))
        nodeService.create(createRequest("/a/1.txt", false))


        assertEquals(1, nodeService.list(projectId, repoName, "/.*|^/a", includeFolder = true, deep = true).size)
        nodeService.deleteByPath(projectId, repoName, "/.*|^/a", operator)
        assertEquals(0, nodeService.list(projectId, repoName, "/.*|^/a", includeFolder = true, deep = true).size)
        assertEquals(1, nodeService.list(projectId, repoName, "/a", includeFolder = true, deep = true).size)
    }

    @Test
    fun getNodeSize() {
        val size = 20
        repeat(size) { i -> nodeService.create(createRequest("/a/b/c/$i.txt", false)) }
        repeat(size) { i -> nodeService.create(createRequest("/a/b/d/$i.txt", false)) }

        val nodeSizeInfo = nodeService.getSize(projectId, repoName, "/a/b")

        assertEquals(42, nodeSizeInfo.subNodeCount)
        assertEquals(40, nodeSizeInfo.size)

    }

    private fun createRequest(fullPath: String = "/a/b/c", folder: Boolean = true): NodeCreateRequest{
        return NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                folder = folder,
                fullPath = fullPath,
                expires = 0,
                overwrite = false,
                size = 1,
                sha256 = "sha256",
                operator = operator
        )
    }

}
