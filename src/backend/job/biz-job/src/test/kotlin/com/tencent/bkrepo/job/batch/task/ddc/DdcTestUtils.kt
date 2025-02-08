package com.tencent.bkrepo.job.batch.task.ddc

import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.batch.task.ddc.ExpiredDdcRefCleanupJob.Companion.COLLECTION_NAME_BLOB_REF
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.LocalDateTime

object DdcTestUtils {
    fun MongoTemplate.insertBlob(id: String, blobId: String, ref: Set<String>?, refCount: Long? = null) {
        val blob = mutableMapOf<String, Any>(
            ID to ObjectId(id),
            "projectId" to UT_PROJECT_ID,
            "repoName" to UT_REPO_NAME,
            "blobId" to blobId,
            "lastModifiedDate" to LocalDateTime.now().minusHours(2L)
        )
        ref?.let { blob["references"] = it }
        refCount?.let { blob["refCount"] = it }
        insert(blob, DdcBlobCleanupJob.COLLECTION_NAME)
    }

    fun MongoTemplate.insertBlobRef(id: String, blobId: String, ref: String): ExpiredDdcRefCleanupJob.BlobRef {
        val blobRef = ExpiredDdcRefCleanupJob.BlobRef(
            id = id,
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            blobId = blobId,
            ref = ref,
        )
        insert(blobRef, COLLECTION_NAME_BLOB_REF)
        return blobRef
    }
}