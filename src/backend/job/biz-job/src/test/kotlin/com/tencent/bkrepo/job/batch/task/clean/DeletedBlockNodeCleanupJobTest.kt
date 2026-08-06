package com.tencent.bkrepo.job.batch.task.clean

import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.metadata.properties.BlockNodeProperties
import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.UT_SHA256
import com.tencent.bkrepo.job.batch.JobBaseTest
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.MigrateRepoStorageJobProperties
import com.tencent.bkrepo.job.migrate.MigrateRepoStorageService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDateTime

@DisplayName("已删除BlockNode清理Job测试")
@DataMongoTest
@Import(BlockNodeProperties::class)
class DeletedBlockNodeCleanupJobTest @Autowired constructor(
    private val deletedBlockNodeCleanupJob: DeletedBlockNodeCleanupJob,
    private val migrateProperties: MigrateRepoStorageJobProperties,
    private val mongoTemplate: MongoTemplate,
) : JobBaseTest() {

    @MockitoBean
    private lateinit var fileReferenceService: FileReferenceService

    @MockitoBean
    private lateinit var migrateRepoStorageService: MigrateRepoStorageService

    @BeforeEach
    fun beforeEach() {
        migrateProperties.enabled = true
        mongoTemplate.remove(Query(), COLLECTION_NAME)
    }

    @AfterEach
    fun afterEach() {
        migrateProperties.enabled = false
    }

    @Test
    fun testSkipCleanupWhenRepoMigrating() {
        val blockNode = mongoTemplate.insert(buildBlockNode(), COLLECTION_NAME)
        whenever(migrateRepoStorageService.migrating(UT_PROJECT_ID, UT_REPO_NAME)).thenReturn(true)

        deletedBlockNodeCleanupJob.run(blockNode.toRow(), COLLECTION_NAME, JobContext())

        assertNotNull(
            mongoTemplate.findOne(Query.query(Criteria.where(ID).isEqualTo(blockNode.id)), COLLECTION_NAME)
        )
        verify(fileReferenceService, never()).decrement(eq(UT_SHA256), anyOrNull())
    }

    private fun TBlockNode.toRow() = DeletedBlockNodeCleanupJob.BlockNode(
        id = id!!,
        projectId = projectId,
        repoName = repoName,
        sha256 = sha256,
        deleted = deleted,
    )

    private fun buildBlockNode() = TBlockNode(
        createdBy = "system",
        createdDate = LocalDateTime.now().minusDays(2L),
        nodeFullPath = "/a/b/c.txt",
        startPos = 0L,
        sha256 = UT_SHA256,
        projectId = UT_PROJECT_ID,
        repoName = UT_REPO_NAME,
        size = 1024L,
        deleted = LocalDateTime.now().minusDays(1L),
    )

    companion object {
        private const val COLLECTION_NAME = "block_node_0"
    }
}
