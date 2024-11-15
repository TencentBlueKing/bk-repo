package com.tencent.bkrepo.job.batch.task.ddc

import com.tencent.bkrepo.common.metadata.service.log.OperateLogService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.batch.JobBaseTest
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.task.ddc.DdcBlobCleanupJob.Companion.BLOB_REF_COLLECTION_NAME
import com.tencent.bkrepo.job.batch.task.ddc.ExpiredDdcRefCleanupJob.Companion.COLLECTION_NAME
import com.tencent.bkrepo.job.batch.task.ddc.ExpiredDdcRefCleanupJob.Companion.buildRef
import org.bson.types.Binary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
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
    fun test() {
        val ref = generateData()

        expiredDdcRefCleanupJob.run(ref, COLLECTION_NAME, JobContext())
        // ref 删除成功
        assertFalse(mongoTemplate.exists(Query(Criteria.where(ID).isEqualTo(ref.id)), COLLECTION_NAME))
        assertFalse(mongoTemplate.exists(Query(Criteria.where(ID).isEqualTo(BLOB_ID2)), BLOB_REF_COLLECTION_NAME))
        assertFalse(mongoTemplate.exists(Query(Criteria.where(ID).isEqualTo(BLOB_ID3)), BLOB_REF_COLLECTION_NAME))

        listOf(BLOB_ID1, BLOB_ID2, BLOB_ID3).forEach {
            val blob = getBlob(it)
            assertEquals(0, blob.refCount)
            assertTrue(blob.references.isEmpty())
        }
    }

    private fun generateData(): ExpiredDdcRefCleanupJob.Ref {
        val bucket = "legacytexture"
        val refKey = "366f955a863296d36ce99868d015752ad0e29f81"
        val ref = ExpiredDdcRefCleanupJob.Ref(
            id = REF_ID,
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            bucket = bucket,
            key = refKey,
            inlineBlob = Binary("test".toByteArray())
        )
        mongoTemplate.insert(ref, COLLECTION_NAME)

        // 增加refCount前的旧数据
        mongoTemplate.insert(
            mapOf(
                ID to BLOB_ID1,
                "projectId" to UT_PROJECT_ID,
                "repoName" to UT_REPO_NAME,
                "blobId" to "000026b4f68e19fc73af288aa2d5e16ac6b5c4e1",
                "references" to setOf(buildRef(bucket, refKey))
            ), DdcBlobCleanupJob.COLLECTION_NAME
        )

        // 新旧混合
        mongoTemplate.insert(
            DdcBlobCleanupJob.BlobRef(
                id = BLOB_ID2,
                projectId = UT_PROJECT_ID,
                repoName = UT_REPO_NAME,
                blobId = "000026b4f68e19fc73af288aa2d5e16ac6b5c4e2",
                ref = buildRef(bucket, refKey),
            ), BLOB_REF_COLLECTION_NAME
        )
        mongoTemplate.insert(
            DdcBlobCleanupJob.Blob(
                id = BLOB_ID2,
                projectId = UT_PROJECT_ID,
                repoName = UT_REPO_NAME,
                blobId = "000026b4f68e19fc73af288aa2d5e16ac6b5c4e2",
                references = setOf(buildRef(bucket, refKey)),
                refCount = 1L
            ), DdcBlobCleanupJob.COLLECTION_NAME
        )

        // 新数据
        mongoTemplate.insert(
            DdcBlobCleanupJob.BlobRef(
                id = BLOB_ID3,
                projectId = UT_PROJECT_ID,
                repoName = UT_REPO_NAME,
                blobId = "000026b4f68e19fc73af288aa2d5e16ac6b5c4e3",
                ref = buildRef(bucket, refKey),
            ), BLOB_REF_COLLECTION_NAME
        )
        mongoTemplate.insert(
            mapOf(
                ID to BLOB_ID3,
                "projectId" to UT_PROJECT_ID,
                "repoName" to UT_REPO_NAME,
                "blobId" to "000026b4f68e19fc73af288aa2d5e16ac6b5c4e3",
                "refCount" to 1L
            ), DdcBlobCleanupJob.COLLECTION_NAME
        )
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
        private const val REF_ID = "1224e1b32e00ff3cdf38ee05"
        private const val BLOB_ID1 = "1"
        private const val BLOB_ID2 = "2"
        private const val BLOB_ID3 = "3"
    }
}