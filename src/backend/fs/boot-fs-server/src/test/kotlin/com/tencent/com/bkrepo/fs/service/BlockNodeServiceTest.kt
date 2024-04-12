package com.tencent.com.bkrepo.fs.service

import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.fs.server.api.RRepositoryClient
import com.tencent.bkrepo.fs.server.model.TBlockNode
import com.tencent.bkrepo.fs.server.repository.BlockNodeRepository
import com.tencent.bkrepo.fs.server.service.BlockNodeService
import com.tencent.com.bkrepo.fs.UT_PROJECT_ID
import com.tencent.com.bkrepo.fs.UT_REPO_NAME
import com.tencent.com.bkrepo.fs.UT_USER
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.test.context.TestPropertySource
import reactor.core.publisher.Mono
import java.time.LocalDateTime

@DataMongoTest
@Import(BlockNodeRepository::class)
@SpringBootConfiguration
@EnableAutoConfiguration
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class BlockNodeServiceTest {

    @MockBean
    lateinit var rRepositoryClient: RRepositoryClient

    @Autowired
    lateinit var blockNodeService: BlockNodeService

    @Autowired
    lateinit var blockNodeRepository: BlockNodeRepository
    private val storageCredentials = FileSystemCredentials()

    @BeforeEach
    fun beforeEach() {
        val criteria = where(TBlockNode::repoName).isEqualTo(UT_REPO_NAME)
        runBlocking { blockNodeRepository.remove(Query(criteria)) }
    }

    @DisplayName("测试创建块")
    @Test
    fun testCreateBlockNode() {
        runBlocking {
            var ref = 0
            Mockito.`when`(rRepositoryClient.increment(any(), anyOrNull())).then {
                ref++
                Mono.just(successResponse(true))
            }
            val bn = createBlockNode()
            Assertions.assertNotNull(bn)
            Assertions.assertNotNull(bn.id)
            Assertions.assertEquals(1, ref)
        }
    }

    @DisplayName("测试获取范围内的分块")
    @Test
    fun testListRangeBlockNodes() {
        runBlocking {
            val createdDate = LocalDateTime.now().minusSeconds(1).toString()
            Mockito.`when`(rRepositoryClient.increment(any(), anyOrNull()))
                .thenReturn(Mono.just(successResponse(true)))
            createBlockNode(10)
            createBlockNode(20)
            createBlockNode(30)
            val range = Range(startPosition = 20, endPosition = 40, total = 100)
            val blocks = blockNodeService.listBlocks(
                range = range,
                projectId = UT_PROJECT_ID,
                repoName = UT_REPO_NAME,
                fullPath = "/file",
                createdDate = createdDate
            )
            Assertions.assertEquals(2, blocks.size)
            Assertions.assertEquals(20, blocks.first().startPos)
            Assertions.assertEquals(30, blocks[1].startPos)
        }
    }

    @DisplayName("测试删除节点所有分块")
    @Test
    fun testDeleteBlocks() {
        runBlocking {
            val createdDate = LocalDateTime.now().minusSeconds(1).toString()
            Mockito.`when`(rRepositoryClient.increment(any(), anyOrNull()))
                .thenReturn(Mono.just(successResponse(true)))
            createBlockNode(startPos = 10)
            createBlockNode(startPos = 20)
            val blocks0 = blockNodeService.listBlocks(
                Range.full(Long.MAX_VALUE),
                UT_PROJECT_ID,
                UT_REPO_NAME,
                "/file",
                createdDate
            )
            Assertions.assertEquals(2, blocks0.size)
            // 删除所有分块
            blockNodeService.deleteBlocks(
                projectId = UT_PROJECT_ID,
                repoName = UT_REPO_NAME,
                fullPath = "/file",
            )
            val blocks1 = blockNodeService.listBlocks(
                Range.full(Long.MAX_VALUE),
                UT_PROJECT_ID,
                UT_REPO_NAME,
                "/file",
                createdDate
            )
            // 所有分块已被删除
            Assertions.assertEquals(0, blocks1.size)
        }
    }

    @DisplayName("测试移动节点")
    @Test
    fun testMoveNode() {
        runBlocking {
            val createdDate = LocalDateTime.now().minusSeconds(1).toString()
            Mockito.`when`(rRepositoryClient.increment(any(), anyOrNull()))
                .thenReturn(Mono.just(successResponse(true)))
            createBlockNode(startPos = 10)
            createBlockNode(startPos = 20)
            val fullPath = "/file"
            val newFullPath = "newFile"
            blockNodeService.moveBlocks(UT_PROJECT_ID, UT_REPO_NAME, fullPath, newFullPath)
            val blocks = blockNodeService.listBlocks(
                Range.full(Long.MAX_VALUE),
                UT_PROJECT_ID,
                UT_REPO_NAME,
                newFullPath,
                createdDate
            )
            Assertions.assertEquals(2, blocks.size)
            val blocks1 = blockNodeService.listBlocks(
                Range.full(Long.MAX_VALUE),
                UT_PROJECT_ID,
                UT_REPO_NAME,
                fullPath,
                createdDate
            )
            Assertions.assertEquals(0, blocks1.size)
        }
    }

    private suspend fun createBlockNode(
        startPos: Long = 0,
        fullPath: String = "/file",
        sha256: String = ""
    ): TBlockNode {
        val blockNode = TBlockNode(
            createdBy = UT_USER,
            createdDate = LocalDateTime.now(),
            nodeFullPath = fullPath,
            startPos = startPos,
            sha256 = sha256,
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            size = 1
        )
        return blockNodeService.createBlock(blockNode, storageCredentials)
    }

    private fun <T> successResponse(data: T) = Response(CommonMessageCode.SUCCESS.getCode(), null, data, null)
}
