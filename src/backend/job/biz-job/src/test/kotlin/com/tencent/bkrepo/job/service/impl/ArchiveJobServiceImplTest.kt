package com.tencent.bkrepo.job.service.impl

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.job.UT_MD5
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.UT_SHA256
import com.tencent.bkrepo.job.batch.task.archive.IdleNodeArchiveJob
import com.tencent.bkrepo.job.migrate.MigrateRepoStorageService
import com.tencent.bkrepo.job.model.TNode
import com.tencent.bkrepo.job.pojo.ArchiveRestoreRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.time.LocalDateTime

@DisplayName("归档任务服务测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(ArchiveJobServiceImpl::class)
class ArchiveJobServiceImplTest @Autowired constructor(
    private val mongoTemplate: MongoTemplate,
    private val service: ArchiveJobServiceImpl,
) {
    @MockBean
    private lateinit var archiveJob: IdleNodeArchiveJob

    @MockBean
    private lateinit var archiveClient: ArchiveClient

    @MockBean
    private lateinit var migrateRepoStorageService: MigrateRepoStorageService

    @Test
    fun test() {
        mockNode()
        val req = ArchiveRestoreRequest(
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            prefix = "/a",
        )
        // 根据projectId筛选
        var nodes = findNodes(req)
        assertEquals(1, nodes.size)

        nodes = findNodes(req.copy(projectId = "other"))
        assertEquals(2, nodes.size)

        // 根据repo筛选
        nodes = findNodes(req.copy(repoName = "other"))
        assertEquals(2, nodes.size)

        // 根据路径前缀筛选
        nodes = findNodes(req.copy(prefix = "/e/f/"))
        assertEquals(2, nodes.size)

        // 根据元数据筛选
        nodes = findNodes(
            req.copy(
                projectId = "metadata-test",
                metadata = mapOf(
                    "pid" to "ppp",
                    "bid" to "bbb"
                )
            )
        )
        assertEquals(2, nodes.size)
    }

    private fun findNodes(request: ArchiveRestoreRequest): List<TNode> {
        return mongoTemplate.find(Query(service.buildCriteria(request)), TNode::class.java, COLLECTION_NAME)
    }

    private fun mockNode() {
        val node = TNode(
            id = null,
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            fullPath = "/a/b/c.txt",
            size = 100L,
            sha256 = UT_SHA256,
            md5 = UT_MD5,
            createdDate = LocalDateTime.now(),
            folder = false,
            archived = true,
            compressed = true,
        )

        mongoTemplate.insert(node, COLLECTION_NAME)
        mongoTemplate.insert(node.copy(projectId = "other"), COLLECTION_NAME)
        mongoTemplate.insert(node.copy(projectId = "other"), COLLECTION_NAME)
        mongoTemplate.insert(node.copy(repoName = "other"), COLLECTION_NAME)
        mongoTemplate.insert(node.copy(repoName = "other"), COLLECTION_NAME)
        mongoTemplate.insert(node.copy(fullPath = "/e/f/g.txt"), COLLECTION_NAME)
        mongoTemplate.insert(node.copy(fullPath = "/e/f/t.txt"), COLLECTION_NAME)
        mongoTemplate.insert(
            node.copy(
                projectId = "metadata-test",
                metadata = listOf(
                    MetadataModel(key = "pid", value = "ppp"),
                    MetadataModel(key = "bid", value = "bbb"),
                    MetadataModel(key = "xxx", value = "yyy"),
                )
            ),
            COLLECTION_NAME
        )
        mongoTemplate.insert(
            node.copy(
                projectId = "metadata-test",
                metadata = listOf(
                    MetadataModel(key = "pid", value = "ppp"),
                    MetadataModel(key = "bid", value = "bbb"),
                    MetadataModel(key = "xxx", value = "yyy"),
                )
            ),
            COLLECTION_NAME
        )
        mongoTemplate.insert(
            node.copy(
                projectId = "metadata-test",
                metadata = listOf(
                    MetadataModel(key = "pid", value = "ppp"),
                    MetadataModel(key = "bid", value = "bbb2"),
                    MetadataModel(key = "xxx", value = "yyy"),
                )
            ),
            COLLECTION_NAME
        )
    }

    companion object {
        private const val COLLECTION_NAME = "node_1"
    }
}
