package com.tencent.bkrepo.job.batch.task.ddc

import com.tencent.bkrepo.common.metadata.service.log.OperateLogService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.job.batch.JobBaseTest
import com.tencent.bkrepo.job.batch.task.ddc.DdcBlobCleanupJob.Companion.COLLECTION_NAME
import com.tencent.bkrepo.job.batch.task.ddc.DdcTestUtils.insertBlob
import com.tencent.bkrepo.job.batch.task.ddc.DdcTestUtils.insertBlobRef
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.migrate.MigrateRepoStorageService
import com.tencent.bkrepo.job.separation.service.SeparationTaskService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.test.context.bean.override.mockito.MockitoBean

@DisplayName("DDC Blob引用数量校正测试")
@DataMongoTest
class DdcBlobRefCountCorrectJobTest @Autowired constructor(
    private val blobRefCountCorrectJob: DdcBlobRefCountCorrectJob,
    private val mongoTemplate: MongoTemplate,
) : JobBaseTest() {
    @MockitoBean
    lateinit var operateLogService: OperateLogService

    @MockitoBean
    private lateinit var nodeService: NodeService

    @MockitoBean
    private lateinit var migrateService: MigrateRepoStorageService

    @MockitoBean
    private lateinit var separateTaskService: SeparationTaskService

    @Autowired
    lateinit var nodeCommonUtils: NodeCommonUtils

    @Test
    fun test() {
        generateData()
        blobRefCountCorrectJob.start()
        assertRefCount("0", 0)
        assertRefCount("1", 0)
        assertRefCount("2", 1)
    }

    private fun assertRefCount(blobId: String, expectedRefCount: Long) {
        val criteria = DdcBlobCleanupJob.Blob::blobId.isEqualTo(blobId)
        val blob = mongoTemplate.findOne<DdcBlobCleanupJob.Blob>(Query(criteria), COLLECTION_NAME)!!
        assertEquals(expectedRefCount, blob.refCount)
    }

    private fun generateData() {
        val ref = "ref/legacytexture/366f955a863296d36ce99868d015752ad0e29f81"
        mongoTemplate.insertBlob(generateObjectId(0), "0", setOf(ref), 0L)
        mongoTemplate.insertBlob(generateObjectId(1), "1", emptySet(), 1L)
        mongoTemplate.insertBlob(generateObjectId(2), "2", emptySet(), 1L)
        mongoTemplate.insertBlobRef(generateObjectId(2), "2", "2")
    }

    private fun generateObjectId(index: Int) = "11bb03c690c9fab0c5e3ed9$index"
}
