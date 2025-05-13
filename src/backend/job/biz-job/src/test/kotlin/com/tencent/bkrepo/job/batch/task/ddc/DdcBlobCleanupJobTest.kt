package com.tencent.bkrepo.job.batch.task.ddc

import com.tencent.bkrepo.common.metadata.service.log.OperateLogService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.job.batch.JobBaseTest
import com.tencent.bkrepo.job.batch.task.ddc.DdcTestUtils.insertBlob
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.test.context.bean.override.mockito.MockitoBean


@DisplayName("DDC Blob清理测试")
@DataMongoTest
class DdcBlobCleanupJobTest @Autowired constructor(
    private val blobCleanupJob: DdcBlobCleanupJob,
    private val mongoTemplate: MongoTemplate,
) : JobBaseTest() {

    @MockitoBean
    private lateinit var nodeService: NodeService

    @MockitoBean
    lateinit var operateLogService: OperateLogService

    @Test
    fun test() {
        generateData()
        blobCleanupJob.start()
        listOf(blobIds[0], blobIds[2], blobIds[6]).forEach { assertFalse(blobExists(it)) }
        listOf(blobIds[1], blobIds[3], blobIds[4], blobIds[5], blobIds[7]).forEach { assertTrue(blobExists(it)) }
        verify(nodeService, times(3)).deleteNode(any())
    }

    private fun blobExists(id: String): Boolean {
        return mongoTemplate.exists(Query(Criteria.where(ID).isEqualTo(id)), DdcBlobCleanupJob.COLLECTION_NAME)
    }

    private fun generateData() {
        val ref = "ref/legacytexture/366f955a863296d36ce99868d015752ad0e29f81"
        // 旧数据
        mongoTemplate.insertBlob(blobIds[0], "1", emptySet())
        mongoTemplate.insertBlob(blobIds[1], "2", setOf(ref))

        // 混合
        mongoTemplate.insertBlob(blobIds[2], "3", emptySet(), 0L)
        mongoTemplate.insertBlob(blobIds[3], "4", setOf(ref), 1L)
        mongoTemplate.insertBlob(blobIds[4], "7", setOf(ref), 0L)
        mongoTemplate.insertBlob(blobIds[5], "8", emptySet(), 1L)

        // 新数据
        mongoTemplate.insertBlob(blobIds[6], "5", null, 0L)
        mongoTemplate.insertBlob(blobIds[7], "6", null, 1L)
    }

    companion object {
        private val blobIds = Array(8) {
            "11bb03c690c9fab0c5e3ed9" + it
        }
    }
}
