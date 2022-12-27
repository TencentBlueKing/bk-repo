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
import org.springframework.data.mongodb.core.query.Criteria
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
        val criteria = where(TBlockNode::nodeFullPath).regex("^/")
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

    @DisplayName("测试覆盖创建块")
    @Test
    fun testCreateOnOverride() {
        runBlocking {
            Mockito.`when`(rRepositoryClient.increment(any(), anyOrNull())).then {
                Mono.just(successResponse(true))
            }
            val bn0 = createBlockNode()
            val bn1 = createBlockNode()
            val criteria = where(TBlockNode::nodeFullPath).isEqualTo(bn0.nodeFullPath)
                .and("_id").isEqualTo(bn0.id)
            val bn2 = blockNodeRepository.findOne(Query(criteria))
            Assertions.assertNotNull(bn2)
            Assertions.assertEquals(true, bn2!!.isDeleted)
            Assertions.assertEquals(false, bn1.isDeleted)
        }
    }

    @DisplayName("测试删除块")
    @Test
    fun testDeleteBlockNode() {
        runBlocking {
            var ref = 1
            Mockito.`when`(rRepositoryClient.decrement(any(), anyOrNull())).then {
                ref--
                Mono.just(successResponse(true))
            }
            Mockito.`when`(rRepositoryClient.increment(any(), anyOrNull()))
                .thenReturn(Mono.just(successResponse(true)))

            val bn = createBlockNode()
            blockNodeService.deleteBlock(bn, storageCredentials)
            val criteria = Criteria.where("_id").isEqualTo(bn.id)
                .and(TBlockNode::nodeFullPath.name).isEqualTo(bn.nodeFullPath)
            val findOne = blockNodeRepository.findOne(Query(criteria))
            Assertions.assertEquals(0, ref)
            Assertions.assertNull(findOne)
        }
    }

    @DisplayName("测试获取最后一个分块")
    @Test
    fun testGetLatestBlockNode() {
        runBlocking {
            Mockito.`when`(rRepositoryClient.increment(any(), anyOrNull()))
                .thenReturn(Mono.just(successResponse(true)))
            repeat(3) {
                createBlockNode(it.toLong())
            }
            val bn = blockNodeService.getLatestBlock(
                projectId = UT_PROJECT_ID,
                repoName = UT_REPO_NAME,
                fullPath = "/file",
                nodeSha256 = "sha256"
            )
            Assertions.assertNotNull(bn)
            Assertions.assertEquals(2, bn!!.startPos)
        }
    }

    @DisplayName("测试获取范围内的分块")
    @Test
    fun testListRangeBlockNodes() {
        runBlocking {
            Mockito.`when`(rRepositoryClient.increment(any(), anyOrNull()))
                .thenReturn(Mono.just(successResponse(true)))
            createBlockNode(10)
            // test order
            createBlockNode(30)
            createBlockNode(20)
            val range = Range(startPosition = 20, endPosition = 40, total = 100)
            val blocks = blockNodeService.listBlocks(
                range = range,
                projectId = UT_PROJECT_ID,
                repoName = UT_REPO_NAME,
                fullPath = "/file",
                nodeSha256 = "sha256"
            )
            Assertions.assertEquals(2, blocks.size)
            Assertions.assertEquals(20, blocks.first().startPos)
            Assertions.assertEquals(30, blocks[1].startPos)

            // 测试当有新增时，获取最新的。
            createBlockNode(startPos = 20, sha256 = "2")
            createBlockNode(startPos = 20, sha256 = "3")
            val blocks1 = blockNodeService.listBlocks(
                range = range,
                projectId = UT_PROJECT_ID,
                repoName = UT_REPO_NAME,
                fullPath = "/file",
                nodeSha256 = "sha256"
            )
            Assertions.assertEquals(2, blocks1.size)
            Assertions.assertEquals(20, blocks1.first().startPos)
            Assertions.assertEquals("3", blocks1.first().sha256)
            Assertions.assertEquals(30, blocks1[1].startPos)
        }
    }

    @DisplayName("测试查找旧的文件块")
    @Test
    fun testFindOldBlocks() {
        runBlocking {
            Mockito.`when`(rRepositoryClient.increment(any(), anyOrNull()))
                .thenReturn(Mono.just(successResponse(true)))
            val bn0 = createBlockNode()
            val bn1 = createBlockNode()
            val oldBlocks = blockNodeService.listOldBlocks(
                projectId = UT_PROJECT_ID,
                repoName = UT_REPO_NAME,
                fullPath = "/file",
                nodeCurrentSha256 = "newSha256"
            )
            Assertions.assertEquals(2, oldBlocks.size)
            Assertions.assertEquals(bn0.id, oldBlocks.first().id)
            Assertions.assertEquals(bn1.id, oldBlocks[1].id)
        }
    }

    private suspend fun createBlockNode(
        startPos: Long = 0,
        fullPath: String = "/file",
        sha256: String = "",
        nodeSha256: String = "sha256"
    ): TBlockNode {
        val blockNode = TBlockNode(
            createdBy = UT_USER,
            createdDate = LocalDateTime.now(),
            nodeFullPath = fullPath,
            nodeSha256 = nodeSha256,
            startPos = startPos,
            sha256 = sha256,
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            size = 1,
            isDeleted = false
        )
        return blockNodeService.createBlock(blockNode, storageCredentials)
    }

    private fun <T> successResponse(data: T) = Response(CommonMessageCode.SUCCESS.getCode(), null, data, null)
}
