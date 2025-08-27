package com.tencent.com.bkrepo.generic.service

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.FileSystemArtifactFile
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.dao.blocknode.BlockNodeDao
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.properties.BlockNodeProperties
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.common.storage.StorageAutoConfiguration
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import com.tencent.com.bkrepo.generic.BLOCK_SIZE
import com.tencent.com.bkrepo.generic.UT_CRC64_ECMA
import com.tencent.com.bkrepo.generic.UT_PROJECT_ID
import com.tencent.com.bkrepo.generic.UT_REPO_NAME
import com.tencent.com.bkrepo.generic.UT_SHA256
import com.tencent.com.bkrepo.generic.UT_USER
import com.tencent.com.bkrepo.generic.UT_VERSION
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDateTime
import kotlin.random.Random


@DataMongoTest
@EnableAutoConfiguration
@ComponentScan(
    basePackages =
        ["com.tencent.bkrepo.generic.service",
            "com.tencent.bkrepo.common.storage",
            "com.tencent.bkrepo.common.metadata"]
)
@ImportAutoConfiguration(StorageAutoConfiguration::class, TaskExecutionAutoConfiguration::class)
@Import(BlockNodeProperties::class)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class BlockNodeServiceTest {

    @MockitoBean
    lateinit var nodeDao: NodeDao

    @Autowired
    lateinit var blockNodeService: BlockNodeService

    @Autowired
    lateinit var blockNodeDao: BlockNodeDao

    @Autowired
    lateinit var storageService: StorageService

    private val storageCredentials = null
    private val range = Range.full(Long.MAX_VALUE)
    private lateinit var artifact: GenericArtifactInfo
    private lateinit var createdDate: LocalDateTime
    private lateinit var node: TNode

    @BeforeEach
    fun beforeEach() {
        val criteria = where(TBlockNode::repoName).isEqualTo(UT_REPO_NAME)
        blockNodeDao.remove(Query(criteria))

        artifact = GenericArtifactInfo(UT_PROJECT_ID, UT_REPO_NAME, "/newFile")
        createdDate = LocalDateTime.now().minusSeconds(1)
        node = TNode(
            createdBy = UT_USER,
            createdDate = createdDate,
            lastModifiedBy = UT_USER,
            lastModifiedDate = createdDate,
            folder = false,
            path = StringPool.ROOT,
            name = "file",
            fullPath = "/file",
            size = BLOCK_SIZE,
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME
        )
        Mockito.`when`(nodeDao.findNode(any(), any(), any()))
            .thenReturn(node)
    }

    @DisplayName("测试BlockUpload")
    @Test
    fun testBlockUpload() {
        setupBlocks()
        val blocks = listBlocks("/file")
        assertBlocks(blocks, expectedSize = 2, blockSize = BLOCK_SIZE, UT_VERSION)
    }

    @DisplayName("测试BlockCompletion")
    @Test
    fun testBlockCompletion() {
        setupBlocks()
        val blocks = listBlocks("/file")
        assertBlocks(blocks, expectedSize = 2, blockSize = BLOCK_SIZE, UT_VERSION)

        // 完成上传
        completeUpload(blocks)

        val completeBlocks = blockNodeService.listBlocks(
            range,
            UT_PROJECT_ID,
            UT_REPO_NAME,
            "/file",
            createdDate.toString()
        )
        assertBlocks(completeBlocks, expectedSize = 2, blockSize = BLOCK_SIZE, null)
        Assertions.assertEquals(0, completeBlocks[0].startPos)
        Assertions.assertEquals(BLOCK_SIZE, completeBlocks[1].startPos)
    }

    @DisplayName("测试BlockAbort")
    @Test
    fun testBlockAbort() {
        setupBlocks()
        val blocks = listBlocks("/file")
        assertBlocks(blocks, expectedSize = 2, blockSize = BLOCK_SIZE, UT_VERSION)

        // 中止上传
        blockNodeService.deleteBlocks(
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            fullPath = "/file",
            uploadId = UT_VERSION
        )

        val deleteBlocksQuery = deleteBlocksQuery("/file", UT_PROJECT_ID, UT_REPO_NAME, createdDate)
        val afterBlocks = blockNodeDao.find(deleteBlocksQuery)
        Assertions.assertEquals(2, afterBlocks.size)
        afterBlocks.forEach { afterBlock ->
            Assertions.assertNotNull(afterBlock.deleted)
        }
    }

    @DisplayName("测试获取范围内的分块")
    @Test
    fun testListRangeBlockNodes() {
        val createdDate = LocalDateTime.now().minusSeconds(1).toString()
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

    private fun createBlockNode(
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
            crc64ecma = UT_CRC64_ECMA,
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            size = 1
        )
        return blockNodeService.createBlock(blockNode, storageCredentials)
    }

    private fun createTempArtifactFile(): ArtifactFile {
        val data = Random.nextBytes(BLOCK_SIZE.toInt()) // 10MB
        val tempFile = createTempFile()
        tempFile.writeBytes(data)
        return FileSystemArtifactFile(tempFile)
    }

    private fun createAndStoreBlock(i: Int, fullPath: String = "/file") {
        val blockNode = TBlockNode(
            createdBy = UT_USER,
            createdDate = LocalDateTime.now(),
            nodeFullPath = fullPath,
            startPos = i * BLOCK_SIZE,
            sha256 = "$UT_SHA256$i",
            crc64ecma = "$UT_CRC64_ECMA$i",
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            size = BLOCK_SIZE,
            uploadId = UT_VERSION,
            expireDate = LocalDateTime.now().plusDays(1)
        )
        val artifactFile = createTempArtifactFile()
        storageService.store(blockNode.sha256, artifactFile, storageCredentials)
        blockNodeService.createBlock(blockNode, storageCredentials)
    }

    private fun listBlocks(fullPath: String = "/file"): List<TBlockNode> {
        return blockNodeService.listBlocksInUploadId(
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            fullPath = fullPath,
            uploadId = UT_VERSION,
        )
    }

    private fun assertBlocks(
        blocks: List<TBlockNode>,
        expectedSize: Int,
        blockSize: Long,
        version: String?,
    ) {
        Assertions.assertEquals(expectedSize, blocks.size)
        blocks.forEach { block ->
            Assertions.assertEquals(blockSize, block.size)
            Assertions.assertTrue(storageService.exist(block.sha256, storageCredentials))
            Assertions.assertEquals(block.uploadId, version)
        }
    }

    private fun completeUpload(blocks: List<TBlockNode>) {
        val block = blocks.first()
        block.uploadId?.let {
            blockNodeService.updateBlockUploadId(
                block.projectId,
                block.repoName,
                block.nodeFullPath,
                it
            )
        }
    }

    private fun deleteBlocksQuery(
        fullPath: String,
        projectId: String,
        repoName: String,
        createdDate: LocalDateTime
    ): Query {
        val criteria = where(TBlockNode::nodeFullPath).isEqualTo(fullPath)
            .and(TBlockNode::projectId.name).isEqualTo(projectId)
            .and(TBlockNode::repoName.name).isEqualTo(repoName)
            .and(TBlockNode::createdDate).gt(createdDate).lt(LocalDateTime.now())
        val query = Query(criteria).with(Sort.by(TBlockNode::createdDate.name))
        return query
    }

    private fun setupBlocks() {
        for (i in 0..1) {
            createAndStoreBlock(i)
        }
    }
}
