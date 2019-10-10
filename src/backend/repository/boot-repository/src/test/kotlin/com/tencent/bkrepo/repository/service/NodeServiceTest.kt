package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.repository.constant.enum.RepositoryCategoryEnum
import com.tencent.bkrepo.repository.pojo.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.NodeUpdateRequest
import com.tencent.bkrepo.repository.pojo.RepoCreateRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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

    private var repoId = ""

    @BeforeEach
    fun setUp() {
        repositoryService.list(projectId).forEach { repositoryService.deleteById(it.id) }
        repoId = repositoryService.create(RepoCreateRequest(operator, "测试仓库", "BINARY", RepositoryCategoryEnum.LOCAL, true, projectId, "简单描述")).id
    }

    @AfterEach
    fun tearDown() {
        repositoryService.deleteById(repoId)
    }

    @Test
    fun getDetailById() {
        assertNull(nodeService.getDetailById(""))
    }

    @Test
    fun list() {
        nodeService.create(NodeCreateRequest(true, "/a/b/", "c", repoId, operator, 0, false,1024L, "sha256")).id

        val size = 20
        repeat(size) { i -> nodeService.create(NodeCreateRequest(false, "/a/b/c", "$i.txt", repoId, operator, 0, false,1024L, "sha256")).id }

        assertEquals(size, nodeService.list(repoId, "/a/b/c").size)
    }

    @Test
    fun page() {
        nodeService.create(NodeCreateRequest(true, "/a/b/", "c", repoId, operator, 0, false, 1024L, "sha256")).id

        val size = 51L
        repeat(size.toInt()) { i -> nodeService.create(NodeCreateRequest(false, "/a/b/c", "$i.txt", repoId, operator, 0, false,1024L, "sha256")).id }

        var page = nodeService.page(repoId, "/a/b/c", 0, 10)
        assertEquals(10, page.records.size)
        assertEquals(size, page.count)
        assertEquals(0, page.page)
        assertEquals(10, page.pageSize)

        page = nodeService.page(repoId, "/a/b/c", 5, 10)
        assertEquals(1, page.records.size)
        page = nodeService.page(repoId, "/a/b/c", 6, 10)
        assertEquals(0, page.records.size)

        page = nodeService.page(repoId, "/a/b/c", 0, 20)
        assertEquals(20, page.records.size)
        assertEquals(size, page.count)
        assertEquals(0, page.page)
        assertEquals(20, page.pageSize)
    }

    @Test
    fun exist() {
        assertTrue(nodeService.exist(repoId, ""))
        assertTrue(nodeService.exist(repoId, "/"))

        nodeService.create(NodeCreateRequest(false, "  / a /   b /  ", " 1.txt  ", repoId, operator, 0, false,1024, "sha256")).id
        assertTrue(nodeService.exist(repoId, "/a/b/1.txt"))
    }

    @Test
    fun createThrow() {
        assertThrows<ErrorCodeException> { nodeService.create(NodeCreateRequest(false, "   a /   b /  ", " 1.txt  ", repoId, operator, 0, false,1024, "sha256")) }
        assertThrows<ErrorCodeException> { nodeService.create(NodeCreateRequest(false, "   a /   b /  ", " 1./txt  ", repoId, operator, 0, false,1024, "sha256")) }
        assertThrows<ErrorCodeException> { nodeService.create(NodeCreateRequest(true, "/a/b/", "..", repoId, operator, 0, false,1024, "sha256")) }
        assertThrows<ErrorCodeException> { nodeService.create(NodeCreateRequest(true, "/a/b/", ".", repoId, operator, 0, false,1024, "sha256")) }
    }

    @Test
    fun createFile() {
        val nodeid = nodeService.create(NodeCreateRequest(false, "  / a /   b /  ", " 1.txt  ", repoId, operator, 0, false,1024, "sha256")).id
        assertThrows<ErrorCodeException> { nodeService.create(NodeCreateRequest(false, "  / a /   b /  ", " 1.txt  ", repoId, operator, 0, false,1024, "sha256")) }

        val node = nodeService.getDetailById(nodeid)

        assertEquals(operator, node!!.createdBy)
        assertNotNull(node.createdDate)
        assertEquals(operator, node.lastModifiedBy)
        assertNotNull(node.lastModifiedDate)

        assertEquals(false, node.folder)
        assertEquals("/a/b/", node.path)
        assertEquals("1.txt", node.name)
        assertEquals("/a/b/1.txt", node.fullPath)
        assertEquals(repoId, node.repositoryId)
        assertEquals(1024L, node.size)
        assertEquals("sha256", node.sha256)
    }

    @Test
    fun createDir() {
        val nodeid = nodeService.create(NodeCreateRequest(true, "  /// a /   c ////  ", "  中文.@_-`~...  ", repoId, operator, 0, false,1024, "sha256")).id

        val node = nodeService.getDetailById(nodeid)

        assertEquals(operator, node!!.createdBy)
        assertNotNull(node.createdDate)
        assertEquals(operator, node.lastModifiedBy)
        assertNotNull(node.lastModifiedDate)

        assertEquals(true, node.folder)
        assertEquals("/a/c/", node.path)
        assertEquals("中文.@_-`~...", node.name)
        assertEquals("/a/c/中文.@_-`~...", node.fullPath)
        assertEquals(repoId, node.repositoryId)
        assertEquals(0L, node.size)
        assertEquals(null, node.sha256)
    }

    @Test
    fun updateById() {
        val nodeid = nodeService.create(NodeCreateRequest(false, "  / a /   b /  ", " 1.txt  ", repoId, operator, 0, false,1024, "sha256")).id

        nodeService.updateById(nodeid, NodeUpdateRequest(operator, path = "/c/d/e", name = "2.txt.txt", size = 0L, sha256 = "sha2561"))

        val node = nodeService.getDetailById(nodeid)

        assertEquals(operator, node!!.createdBy)
        assertNotNull(node.createdDate)
        assertEquals(operator, node.lastModifiedBy)
        assertNotNull(node.lastModifiedDate)

        assertEquals(false, node.folder)
        assertEquals("/c/d/e/", node.path)
        assertEquals("2.txt.txt", node.name)
        assertEquals("/c/d/e/2.txt.txt", node.fullPath)
        assertEquals(repoId, node.repositoryId)
        assertEquals(0L, node.size)
        assertEquals("sha2561", node.sha256)
    }

    @Test
    fun softDeleteById() {
        var nodeid = nodeService.create(NodeCreateRequest(false, "/a/b/", "1.txt", repoId, operator, 0, false,1024, "sha256")).id
        nodeService.deleteById(nodeid, operator)

        assertFalse(nodeService.exist(repoId, "/a/b/1.txt"))

        nodeid = nodeService.create(NodeCreateRequest(true, "/a/b/", "c", repoId, operator, 0, false,1024, "sha256")).id
        nodeService.create(NodeCreateRequest(false, "/a/b/c", "1.txt", repoId, operator, 0, false,1024, "sha256")).id

        assertTrue(nodeService.exist(repoId, "/a/b/c/1.txt"))

        nodeService.deleteById(nodeid, operator)

        assertFalse(nodeService.exist(repoId, "/a/b/c/1.txt"))

    }

    @Test
    fun escapeTest() {
        nodeService.create(NodeCreateRequest(false, "/.*|^/a", "1.txt", repoId, operator, 0, false,1024, "sha256")).id
        nodeService.create(NodeCreateRequest(false, "/a", "1.txt", repoId, operator, 0, false,1024, "sha256")).id


        assertEquals(1, nodeService.list(repoId, "/.*|^/a").size)
        nodeService.deleteByPath(repoId, "/.*|^/a", operator)
        assertEquals(0, nodeService.list(repoId, "/.*|^/a").size)
        assertEquals(1, nodeService.list(repoId, "/a").size)
    }
}
