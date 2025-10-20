package com.tencent.bkrepo.job.batch.task.clean

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.auth.api.ServiceBkiamV3ResourceClient
import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.common.artifact.hash.sha256
import com.tencent.bkrepo.common.metadata.service.log.OperateLogService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.stream.event.supplier.MessageSupplier
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.UT_SHA256
import com.tencent.bkrepo.job.batch.JobBaseTest
import com.tencent.bkrepo.job.batch.context.DeletedNodeCleanupJobContext
import com.tencent.bkrepo.job.migrate.MigrateRepoStorageService
import com.tencent.bkrepo.job.separation.service.SeparationTaskService
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDateTime

@DisplayName("已删除Node清理Job测试")
@DataMongoTest
class DeletedNodeCleanupJobTest @Autowired constructor(
    private val deletedNodeCleanupJob: DeletedNodeCleanupJob,
    private val mongoTemplate: MongoTemplate,
) : JobBaseTest() {

    @MockitoBean
    private lateinit var messageSupplier: MessageSupplier

    @MockitoBean
    private lateinit var servicePermissionClient: ServicePermissionClient

    @MockitoBean
    private lateinit var serviceBkiamV3ResourceClient: ServiceBkiamV3ResourceClient

    @MockitoBean
    private lateinit var archiveClient: ArchiveClient

    @MockitoBean
    private lateinit var migrateRepoStorageService: MigrateRepoStorageService

    @MockitoBean
    private lateinit var storageCredentialService: StorageCredentialService

    @MockitoBean
    lateinit var separationTaskService: SeparationTaskService

    @MockitoBean
    lateinit var operateLogService: OperateLogService

    @BeforeAll
    fun beforeAll() {
        createRepo()
    }

    @BeforeEach
    fun beforeEach() {
        whenever(storageCredentialService.list(anyOrNull())).thenReturn(
            emptyList()
        )
        whenever(storageCredentialService.findByKey(anyOrNull())).thenReturn(
            InnerCosCredentials()
        )
        whenever(separationTaskService.repoSeparationCheck(anyString(), anyString()))
            .thenReturn(false)
    }

    @Test
    fun testRefNotExists() {
        // mock data
        val node = buildNode()
        val nodeShouldKeep = mongoTemplate.insert(node, COLLECTION_NAME)
        val nodeShouldNotKeep = mongoTemplate.insert(
            node.copy(
                id = ObjectId.get().toHexString(),
                deleted = LocalDateTime.now().minusDays(40L),
                sha256 = node.sha256!!.sha256()
            )
        )

        // test repo of node was deleted
        assertNotNull(
            mongoTemplate.findOne(Query.query(Criteria.where(ID).isEqualTo(nodeShouldKeep.id)), COLLECTION_NAME)
        )
        mongoTemplate.remove(Query(), DeletedNodeCleanupJob.Repository::class.java)
        deletedNodeCleanupJob.run(nodeShouldKeep, COLLECTION_NAME, DeletedNodeCleanupJobContext())
        // 未补偿创建ref
        assertNull(findRef(nodeShouldKeep.sha256!!))
        // node被删除
        assertNull(mongoTemplate.findOne(Query.query(Criteria.where(ID).isEqualTo(nodeShouldKeep.id)), COLLECTION_NAME))

        // 恢复数据
        createRepo()
        mongoTemplate.insert(nodeShouldKeep, COLLECTION_NAME)

        // test nodeShouldKeep
        assertNull(findRef(nodeShouldKeep.sha256!!))
        deletedNodeCleanupJob.run(nodeShouldKeep, COLLECTION_NAME, DeletedNodeCleanupJobContext())
        // 未补偿创建ref
        assertNull(findRef(nodeShouldKeep.sha256!!))
        // node未被删除
        assertNotNull(
            mongoTemplate.findOne(Query.query(Criteria.where(ID).isEqualTo(nodeShouldKeep.id)), COLLECTION_NAME)
        )

        // test nodeShouldNotKeep
        assertNull(findRef(nodeShouldNotKeep.sha256!!))
        deletedNodeCleanupJob.run(nodeShouldNotKeep, COLLECTION_NAME, DeletedNodeCleanupJobContext())
        // 成功补偿创建ref
        assertEquals(0, findRef(nodeShouldNotKeep.sha256!!)!!.count.toInt())
        // node被删除
        assertNull(
            mongoTemplate.findOne(Query.query(Criteria.where(ID).isEqualTo(nodeShouldNotKeep.id)), COLLECTION_NAME)
        )
    }

    private fun findRef(sha256: String, credentialsKey: String? = null): DeletedNodeCleanupJob.FileReference? {
        val criteria = Criteria
            .where(DeletedNodeCleanupJob.FileReference::sha256.name).isEqualTo(sha256)
            .and(DeletedNodeCleanupJob.FileReference::credentialsKey.name).isEqualTo(credentialsKey)
        val collectionName = "file_reference_${HashShardingUtils.shardingSequenceFor(sha256, SHARDING_COUNT)}"
        return mongoTemplate.findOne(Query(criteria), DeletedNodeCleanupJob.FileReference::class.java, collectionName)
    }

    private fun buildNode() = DeletedNodeCleanupJob.Node(
        id = ObjectId.get().toHexString(),
        projectId = UT_PROJECT_ID,
        repoName = UT_REPO_NAME,
        folder = false,
        sha256 = UT_SHA256,
        deleted = LocalDateTime.now().minusDays(1L),
        clusterNames = null
    )

    private fun createRepo() {
        mongoTemplate.insert(
            DeletedNodeCleanupJob.Repository(
                id = ObjectId.get().toHexString(),
                projectId = UT_PROJECT_ID,
                name = UT_REPO_NAME,
                credentialsKey = null
            )
        )
    }

    companion object {
        private val COLLECTION_NAME = "node_${HashShardingUtils.shardingSequenceFor(UT_PROJECT_ID, SHARDING_COUNT)}"
    }
}
