package com.tencent.bkrepo.job.batch.task.archive

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.archive.constant.ArchiveStorageClass
import com.tencent.bkrepo.archive.pojo.ArchiveFile
import com.tencent.bkrepo.auth.api.ServiceBkiamV3ResourceClient
import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import com.tencent.bkrepo.common.metadata.service.log.OperateLogService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.UT_SHA256
import com.tencent.bkrepo.job.batch.JobBaseTest
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.config.properties.IdleNodeArchiveJobProperties
import com.tencent.bkrepo.job.migrate.MigrateRepoStorageService
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.buildRepo
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDateTime

@DisplayName("空闲节点归档Job测试")
@DataMongoTest
@Import(IdleNodeArchiveJobProperties::class)
@TestMethodOrder(MethodOrderer.MethodName::class)
class IdleNodeArchiveJobTest @Autowired constructor(
    private val mongoTemplate: MongoTemplate,
    private val job: IdleNodeArchiveJob,
    private val properties: IdleNodeArchiveJobProperties
) : JobBaseTest() {

    @Autowired
    lateinit var repositoryCommonUtils: RepositoryCommonUtils

    @MockitoBean
    private lateinit var messageSupplier: MessageSupplier

    @MockitoBean
    private lateinit var servicePermissionClient: ServicePermissionClient

    @MockitoBean
    lateinit var repositoryService: RepositoryService

    @MockitoBean
    lateinit var migrateRepoStorageService: MigrateRepoStorageService

    @MockitoBean
    private lateinit var serviceBkiamV3ResourceClient: ServiceBkiamV3ResourceClient

    @MockitoBean
    private lateinit var archiveClient: ArchiveClient

    @MockitoBean
    private lateinit var fileReferenceService: FileReferenceService

    @MockitoBean
    lateinit var operateLogService: OperateLogService

    @BeforeEach
    fun setup() {
        mongoTemplate.remove(Query(), NodeDetail::class.java)
        // 初始化真实配置对象
        properties.storageClass = ArchiveStorageClass.DEEP_ARCHIVE
        properties.days = 365
        properties.enabled = true

        // 模拟仓库服务返回有效数据
        // 模拟返回包含有效storageCredentials的RepositoryDetail
        Mockito.`when`(repositoryService.getRepoDetail(eq(UT_PROJECT_ID_1), anyString(), anyOrNull())).thenReturn(
            buildRepo(UT_PROJECT_ID_1)
        )
        Mockito.`when`(repositoryService.getRepoDetail(eq(UT_PROJECT_ID_2), anyString(), anyOrNull())).thenReturn(
            buildRepo(UT_PROJECT_ID_2)
        )

        whenever(repositoryService.updateStorageCredentialsKey(anyString(), anyString(), anyString())).then { }
        whenever(repositoryService.unsetOldStorageCredentialsKey(anyString(), anyString())).then { }
        whenever(archiveClient.get(any(), anyOrNull())).thenReturn(
            Response<ArchiveFile?>(
                data = null,
                code = 0
            )
        )
    }

    @Test
    fun testRunWithProjectConfig() {

        insertTestNode().also { assertNotNull(it.projectId) }

        val query = job.buildQuery()
        // 验证查询结果
        val found = mongoTemplate.findOne(query, IdleNodeArchiveJob.Node::class.java, collectionName(UT_PROJECT_ID))
        assertNotNull(found)

        // 执行测试
        assertEquals(SHARDING_COUNT, job.collectionNames().size)
        // 模拟配置
        properties.projectArchiveCredentialsKeys = mapOf(
            UT_PROJECT_ID_1 to "AKID_TEST",
            UT_PROJECT_ID_2 to "AKID_TEST2"
        )
        properties.storageClass = ArchiveStorageClass.DEEP_ARCHIVE
        properties.days = 15

        // 准备测试数据
        insertTestNode(UT_PROJECT_ID_1).also { assertNotNull(it.projectId) }
        insertTestNode(UT_PROJECT_ID_2).also { assertNotNull(it.projectId) }
        // 准备查询条件
        val queryByProject = job.buildQuery()

        // 验证查询结果
        val foundByProject = mongoTemplate.findOne(
            queryByProject,
            IdleNodeArchiveJob.Node::class.java,
            collectionName(UT_PROJECT_ID_1)
        )
        assertNotNull(foundByProject)

        // 执行测试
        assertEquals(2, job.collectionNames().size)

        job.start()

        Mockito.verify(archiveClient, Mockito.times(2)).archive(any())
    }

    @Test
    fun testSkipWhenProjectArchiveCredentialsKeysEmpty() {

        properties.projectArchiveCredentialsKeys = emptyMap()

        // 插入测试数据
        insertTestNode()

        // 执行任务
        job.start()

        // 验证没有执行归档操作
        Mockito.verify(archiveClient, Mockito.never()).archive(any())

        // 验证集合数量为默认分片数
        assertEquals(SHARDING_COUNT, job.collectionNames().size)
    }

    private fun insertTestNode(projectId: String = UT_PROJECT_ID): IdleNodeArchiveJob.Node {
        val node = mockArchiveNode(projectId)
        val collectionName = collectionName(projectId)

        // 直接插入原始文档数据，保留扩展字段
        mongoTemplate.insert(
            mapOf(
                "id" to node.id,
                "projectId" to node.projectId,
                "repoName" to node.repoName,
                "fullPath" to node.fullPath,
                "sha256" to node.sha256,
                "size" to node.size,
                "lastAccessDate" to node.lastAccessDate,
                "folder" to false,
                "deleted" to null,
                "archived" to null,
                "compressed" to null
            ),
            collectionName
        )
        return node
    }

    private fun mockArchiveNode(projectId: String): IdleNodeArchiveJob.Node {
        return IdleNodeArchiveJob.Node(
            id = "$projectId-test",
            projectId = projectId,
            repoName = UT_REPO_NAME,
            fullPath = "/$projectId/$UT_REPO_NAME/test/file.txt",
            sha256 = UT_SHA256 + projectId,
            size = 11 * 1024 * 1024, // 11MB > 10MB阈值
            lastAccessDate = LocalDateTime.now().minusDays(400),
        ).apply {}
    }

    companion object {
        const val UT_PROJECT_ID_1 = "test-project"
        const val UT_PROJECT_ID_2 = "test-project-2"

        private fun collectionName(projectId: String) =
            "node_${HashShardingUtils.shardingSequenceFor(projectId, SHARDING_COUNT)}"

    }
}
