package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.repository.constant.enum.RepositoryCategoryEnum
import com.tencent.bkrepo.repository.pojo.node.NodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.NodeMoveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.NodeSearchRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import org.apache.commons.lang.RandomStringUtils
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
    @DisplayName("根节点相关测试")
    fun rootNodeTest() {
        assertNull(nodeService.queryDetail(projectId, repoName, "/"))
        assertEquals(0, nodeService.list(projectId, repoName, "/", true, deep = true).size)

        nodeService.create(createRequest("/1.txt", false))
        assertNotNull(nodeService.queryDetail(projectId, repoName, "/"))
        assertEquals(1, nodeService.list(projectId, repoName, "/", false, deep = true).size)

        nodeService.create(createRequest("/a/b/1.txt", false))
        assertNotNull(nodeService.queryDetail(projectId, repoName, "/"))

        assertEquals(2, nodeService.list(projectId, repoName, "/", false, deep = true).size)
    }


    @Test
    @DisplayName("列表查询")
    fun listTest() {
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
    @DisplayName("列表查询")
    fun list2Test() {
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
    @DisplayName("分页查询")
    fun pageTest() {
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
    @DisplayName("搜索测试")
    fun searchTest() {
        val size = 11L
        val metadata = mutableMapOf<String, String>()
        repeat(size.toInt()) { i ->
            run {
                metadata["key"] = i.toString()
                val createRequest = createRequest("/a/b/$i.txt", false, metadata = metadata)
                nodeService.create(createRequest)
            }
        }

        repeat(size.toInt()) { i ->
            run {
                metadata["key"] = i.toString()
                val createRequest = createRequest("/a/c/$i.txt", false, metadata = metadata)
                nodeService.create(createRequest)
            }
        }

        val metadataCondition = mutableMapOf<String, String>()
        metadataCondition["key"] = "1"
        val searchRequest = NodeSearchRequest(projectId, listOf(repoName), listOf("/a/b", "/a/c"), metadataCondition, 0, 10)

        val page = nodeService.search(searchRequest)
        assertEquals(2, page.records.size)
        assertEquals(2, page.count)
    }

    @Test
    @DisplayName("判断节点是否存在")
    fun existTest() {
        assertFalse(nodeService.exist(projectId, repoName, ""))
        assertFalse(nodeService.exist(projectId, repoName, "/"))

        createRequest("  / a /   b /  1.txt   ", false)
        nodeService.create(createRequest("  / a /   b /  1.txt   ", false))
        assertTrue(nodeService.exist(projectId, repoName, "/a/b/1.txt"))
        assertTrue(nodeService.exist(projectId, repoName, "/a"))
        assertTrue(nodeService.exist(projectId, repoName, "/a/b"))
    }

    @Test
    @DisplayName("创建节点，非法名称抛异常")
    fun createThrowTest() {
        assertThrows<ErrorCodeException> { nodeService.create(createRequest(" a /   b /  1.txt   ", false))}
        assertThrows<ErrorCodeException> { nodeService.create(createRequest(" a /   b /  1./txt   ", false))}
        assertThrows<ErrorCodeException> { nodeService.create(createRequest("/a/b/..", true))}
        assertThrows<ErrorCodeException> { nodeService.create(createRequest("/a/b/.", true))}
        nodeService.create(createRequest("/a/b", true))
    }

    @Test
    @DisplayName("创建文件")
    fun createFileTest() {
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
    @DisplayName("创建目录测试")
    fun createPathTest() {
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
    @DisplayName("删除节点")
    fun deleteTest() {
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
    @DisplayName("正则转义")
    fun escapeTest() {
        nodeService.create(createRequest("/.*|^/a/1.txt", false))
        nodeService.create(createRequest("/a/1.txt", false))


        assertEquals(1, nodeService.list(projectId, repoName, "/.*|^/a", includeFolder = true, deep = true).size)
        nodeService.deleteByPath(projectId, repoName, "/.*|^/a", operator)
        assertEquals(0, nodeService.list(projectId, repoName, "/.*|^/a", includeFolder = true, deep = true).size)
        assertEquals(1, nodeService.list(projectId, repoName, "/a", includeFolder = true, deep = true).size)
    }

    @Test
    @DisplayName("计算节点大小")
    fun getSizeTest() {
        val size = 20
        repeat(size) { i -> nodeService.create(createRequest("/a/b/c/$i.txt", false)) }
        repeat(size) { i -> nodeService.create(createRequest("/a/b/d/$i.txt", false)) }

        val pathSizeInfo = nodeService.getSize(projectId, repoName, "/a/b")

        assertEquals(42, pathSizeInfo.subNodeCount)
        assertEquals(40, pathSizeInfo.size)

        val fileSizeInfo = nodeService.getSize(projectId, repoName, "/a/b/c/1.txt")

        assertEquals(0, fileSizeInfo.subNodeCount)
        assertEquals(1, fileSizeInfo.size)

    }

    @Test
    @DisplayName("重命名文件")
    fun renameTest() {
        nodeService.create(createRequest("/a/b/1.txt", false))
        nodeService.create(createRequest("/a/b/2.txt", false))
        nodeService.create(createRequest("/a/b/c/1.txt", false))
        nodeService.create(createRequest("/a/b/c/2.txt", false))

        val renameRequest = NodeRenameRequest(projectId = projectId, repoName = repoName, fullPath = "/a", newFullPath = "/aa", operator = operator)
        nodeService.rename(renameRequest)

        assertFalse(nodeService.exist(projectId, repoName, "/a"))
        assertFalse(nodeService.exist(projectId, repoName, "/a/b"))
        assertFalse(nodeService.exist(projectId, repoName, "/a/b/c"))
        assertFalse(nodeService.exist(projectId, repoName, "/a/b/1.txt"))
        assertFalse(nodeService.exist(projectId, repoName, "/a/b/c/2.txt"))


        assertTrue(nodeService.exist(projectId, repoName, "/aa"))
        assertTrue(nodeService.exist(projectId, repoName, "/aa/b"))
        assertTrue(nodeService.exist(projectId, repoName, "/aa/b/c"))
        assertTrue(nodeService.exist(projectId, repoName, "/aa/b/1.txt"))
        assertTrue(nodeService.exist(projectId, repoName, "/aa/b/c/2.txt"))

    }

    @Test
    @DisplayName("重命名文件2")
    fun rename2Test() {
        nodeService.create(createRequest("/a/b/c", true))


        val renameRequest = NodeRenameRequest(projectId = projectId, repoName = repoName, fullPath = "/a/b/c", newFullPath = "/a/d/c", operator = operator)
        nodeService.rename(renameRequest)

        assertTrue(nodeService.exist(projectId, repoName, "/a"))
        assertTrue(nodeService.exist(projectId, repoName, "/a/b"))
        assertFalse(nodeService.exist(projectId, repoName, "/a/b/c"))

        assertTrue(nodeService.exist(projectId, repoName, "/a/d"))
        assertTrue(nodeService.exist(projectId, repoName, "/a/d/c"))

    }

    @Test
    @DisplayName("重命名文件，遇同名文件抛异常")
    fun renameThrowTest() {
        nodeService.create(createRequest("/a/b/1.txt", false))
        nodeService.create(createRequest("/a/b/2.txt", false))
        nodeService.create(createRequest("/a/b/c/1.txt", false))
        nodeService.create(createRequest("/a/b/c/2.txt", false))

        nodeService.create(createRequest("/aa/b/c/2.txt", false))

        val renameRequest = NodeRenameRequest(projectId = projectId, repoName = repoName, fullPath = "/a", newFullPath = "/aa", operator = operator)
        assertThrows<ErrorCodeException> { nodeService.rename(renameRequest) }
    }

    @Test
    @DisplayName("移动文件")
    fun moveTest() {
        nodeService.create(createRequest("/a/b/1.txt", false))
        nodeService.create(createRequest("/a/b/2.txt", false))
        nodeService.create(createRequest("/a/b/c/1.txt", false))
        nodeService.create(createRequest("/a/b/c/2.txt", false))
        nodeService.create(createRequest("/a/1.txt", false))

        val moveRequest = NodeMoveRequest(srcProjectId = projectId, srcRepoName = repoName, srcFullPath = "/a/b", destPath = "/ab", operator = operator)
        nodeService.move(moveRequest)

        assertTrue(nodeService.exist(projectId, repoName, "/a"))
        assertTrue(nodeService.exist(projectId, repoName, "/a/1.txt"))
        assertFalse(nodeService.exist(projectId, repoName, "/a/b"))
        assertFalse(nodeService.exist(projectId, repoName, "/a/b/c"))
        assertFalse(nodeService.exist(projectId, repoName, "/a/b/1.txt"))
        assertFalse(nodeService.exist(projectId, repoName, "/a/b/c/2.txt"))


        assertTrue(nodeService.exist(projectId, repoName, "/ab"))
        assertTrue(nodeService.exist(projectId, repoName, "/ab/b"))
        assertTrue(nodeService.exist(projectId, repoName, "/ab/b/c"))
        assertTrue(nodeService.exist(projectId, repoName, "/ab/b/1.txt"))
        assertTrue(nodeService.exist(projectId, repoName, "/ab/b/c/2.txt"))

    }

    @Test
    @DisplayName("移动文件，遇同名文件抛覆盖")
    fun moveOverwriteTest() {
        nodeService.create(createRequest("/a/1.txt", false))
        nodeService.create(createRequest("/ab/a/1.txt", false, size = 2))

        val moveRequest = NodeMoveRequest(srcProjectId = projectId, srcRepoName = repoName, srcFullPath = "/a", destPath = "/ab", operator = operator, overwrite = true)
        nodeService.move(moveRequest)

        val node = nodeService.queryDetail(projectId, repoName, "/ab/a/1.txt")!!
        assertEquals(1, node.nodeInfo.size)
    }

    @Test
    @DisplayName("移动文件，遇同名文件抛异常")
    fun moveThrowTest() {
        nodeService.create(createRequest("/a/1.txt", false))
        nodeService.create(createRequest("/ab/a/1.txt", false))

        val moveRequest = NodeMoveRequest(srcProjectId = projectId, srcRepoName = repoName, srcFullPath = "/a", destPath = "/ab", operator = operator)
        assertThrows<ErrorCodeException> { nodeService.move(moveRequest) }
    }

    @Test
    @DisplayName("移动文件，遇同名文件夹跳过")
    fun moveSkipTest() {
        nodeService.create(createRequest("/a/1.txt", false))
        nodeService.create(createRequest("/ab/a", true))

        val moveRequest = NodeMoveRequest(srcProjectId = projectId, srcRepoName = repoName, srcFullPath = "/a", destPath = "/ab", operator = operator)
        nodeService.move(moveRequest)

    }

    @Test
    @DisplayName("拷贝文件")
    fun copyTest() {
        nodeService.create(createRequest("/a/b/1.txt", false))
        nodeService.create(createRequest("/a/b/2.txt", false))
        nodeService.create(createRequest("/a/b/c/1.txt", false))
        nodeService.create(createRequest("/a/b/c/2.txt", false))
        nodeService.create(createRequest("/a/1.txt", false))

        val copyRequest = NodeCopyRequest(srcProjectId = projectId, srcRepoName = repoName, srcFullPath = "/a/b", destPath = "/ab", operator = operator)
        nodeService.copy(copyRequest)

        assertTrue(nodeService.exist(projectId, repoName, "/a"))
        assertTrue(nodeService.exist(projectId, repoName, "/a/1.txt"))
        assertTrue(nodeService.exist(projectId, repoName, "/a/b"))
        assertTrue(nodeService.exist(projectId, repoName, "/a/b/c"))
        assertTrue(nodeService.exist(projectId, repoName, "/a/b/1.txt"))
        assertTrue(nodeService.exist(projectId, repoName, "/a/b/c/2.txt"))


        assertTrue(nodeService.exist(projectId, repoName, "/ab"))
        assertTrue(nodeService.exist(projectId, repoName, "/ab/b"))
        assertTrue(nodeService.exist(projectId, repoName, "/ab/b/c"))
        assertTrue(nodeService.exist(projectId, repoName, "/ab/b/1.txt"))
        assertTrue(nodeService.exist(projectId, repoName, "/ab/b/c/2.txt"))
        assertFalse(nodeService.exist(projectId, repoName, "/ab/1.txt"))

    }

    @Test
    @DisplayName("拷贝文件，遇同名文件覆盖")
    fun copyOverwriteTest() {
        nodeService.create(createRequest("/a/1.txt", false))
        nodeService.create(createRequest("/ab/a/1.txt", false, size = 2))

        val copyRequest = NodeCopyRequest(srcProjectId = projectId, srcRepoName = repoName, srcFullPath = "/a", destPath = "/ab", operator = operator, overwrite = true)
        nodeService.copy(copyRequest)

        val node = nodeService.queryDetail(projectId, repoName, "/ab/a/1.txt")!!
        assertEquals(1, node.nodeInfo.size)
    }

    @Test
    @DisplayName("移动文件，遇同名文件抛异常")
    fun copyThrowTest() {
        nodeService.create(createRequest("/a/1.txt", false))
        nodeService.create(createRequest("/ab/a/1.txt", false))

        val copyRequest = NodeCopyRequest(srcProjectId = projectId, srcRepoName = repoName, srcFullPath = "/a", destPath = "/ab", operator = operator)
        assertThrows<ErrorCodeException> { nodeService.copy(copyRequest) }
    }

    @Test
    @DisplayName("拷贝文件，遇同名文件夹跳过")
    fun copySkipTest() {
        nodeService.create(createRequest("/a/1.txt", false))
        nodeService.create(createRequest("/ab/a", true))

        val copyRequest = NodeCopyRequest(srcProjectId = projectId, srcRepoName = repoName, srcFullPath = "/a", destPath = "/ab", operator = operator)
        nodeService.copy(copyRequest)

    }

    private fun createRequest(fullPath: String = "/a/b/c", folder: Boolean = true, size: Long = 1, metadata: Map<String, String>? = null): NodeCreateRequest{
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
