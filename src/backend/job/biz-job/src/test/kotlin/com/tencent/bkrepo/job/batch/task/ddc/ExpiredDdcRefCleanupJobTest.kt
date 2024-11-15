package com.tencent.bkrepo.job.batch.task.ddc

import com.tencent.bkrepo.common.metadata.service.log.OperateLogService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.batch.JobBaseTest
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.task.ddc.DdcTestUtils.insertBlob
import com.tencent.bkrepo.job.batch.task.ddc.DdcTestUtils.insertBlobRef
import com.tencent.bkrepo.job.batch.task.ddc.ExpiredDdcRefCleanupJob.Companion.COLLECTION_NAME
import com.tencent.bkrepo.job.batch.task.ddc.ExpiredDdcRefCleanupJob.Companion.COLLECTION_NAME_BLOB_REF
import com.tencent.bkrepo.job.batch.task.ddc.ExpiredDdcRefCleanupJob.Companion.buildRef
import org.bson.types.Binary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo

@DisplayName("DDC过期Ref清理测试")
@DataMongoTest
class ExpiredDdcRefCleanupJobTest @Autowired constructor(
    private val expiredDdcRefCleanupJob: ExpiredDdcRefCleanupJob,
    private val mongoTemplate: MongoTemplate,
) : JobBaseTest() {

    @MockBean
    private lateinit var nodeService: NodeService

    @MockBean
    lateinit var operateLogService: OperateLogService

    @Test
    fun testDeletedRefNode() {
        val ref = ExpiredDdcRefCleanupJob.Ref(
            id = "test",
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            bucket = "test",
            key = "test",
            inlineBlob = null
        )
        expiredDdcRefCleanupJob.run(ref, COLLECTION_NAME, JobContext())
        verify(nodeService, times(1)).deleteNode(any())
    }

    @Test
    fun test() {
        val ref = generateData()

        expiredDdcRefCleanupJob.run(ref, COLLECTION_NAME, JobContext())
        // ref 删除成功
        assertFalse(mongoTemplate.exists(Query(Criteria.where(ID).isEqualTo(ref.id)), COLLECTION_NAME))
        assertFalse(mongoTemplate.exists(Query(Criteria.where(ID).isEqualTo(BLOB_ID2)), COLLECTION_NAME_BLOB_REF))
        assertFalse(mongoTemplate.exists(Query(Criteria.where(ID).isEqualTo(BLOB_ID3)), COLLECTION_NAME_BLOB_REF))

        listOf(BLOB_ID1, BLOB_ID2, BLOB_ID3).forEach {
            val blob = getBlob(it)
            assertEquals(0, blob.refCount)
            assertTrue(blob.references.isEmpty())
        }
    }

    private fun generateData(): ExpiredDdcRefCleanupJob.Ref {
        val ref = ExpiredDdcRefCleanupJob.Ref(
            id = "1224e1b32e00ff3cdf38ee05",
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            bucket = "legacytexture",
            key = "366f955a863296d36ce99868d015752ad0e29f81",
            inlineBlob = Binary("test".toByteArray())
        )
        val refBucketKey = buildRef(ref.bucket, ref.key)
        mongoTemplate.insert(ref, COLLECTION_NAME)

        // 增加refCount前的旧数据
        mongoTemplate.insertBlob(BLOB_ID1, "000026b4f68e19fc73af288aa2d5e16ac6b5c4e1", setOf(refBucketKey))

        // 新旧混合
        mongoTemplate.insertBlobRef(BLOB_ID2, "000026b4f68e19fc73af288aa2d5e16ac6b5c4e2", refBucketKey)
        mongoTemplate.insertBlob(BLOB_ID2, "000026b4f68e19fc73af288aa2d5e16ac6b5c4e2", setOf(refBucketKey), 1L)

        // 新数据
        mongoTemplate.insertBlobRef(BLOB_ID3, "000026b4f68e19fc73af288aa2d5e16ac6b5c4e3", refBucketKey)
        mongoTemplate.insertBlob(BLOB_ID3, "000026b4f68e19fc73af288aa2d5e16ac6b5c4e3", null, 1L)
        return ref
    }

    private fun getBlob(id: String): DdcBlobCleanupJob.Blob {
        return mongoTemplate.findOne(
            Query(Criteria.where(ID).isEqualTo(id)),
            DdcBlobCleanupJob.Blob::class.java,
            DdcBlobCleanupJob.COLLECTION_NAME
        )!!
    }

    companion object {
        private const val BLOB_ID1 = "11bb038690c9fab085e3ed91"
        private const val BLOB_ID2 = "11bb038690c9fab085e3ed92"
        private const val BLOB_ID3 = "11bb038690c9fab085e3ed93"
    }
}