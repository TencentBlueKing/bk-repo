package com.tencent.bkrepo.ddc.repository

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.ddc.model.TDdcBlobRef
import com.tencent.bkrepo.ddc.utils.DdcUtils.buildRef
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

@Repository
class BlobRefRepository : SimpleMongoDao<TDdcBlobRef>() {
    fun addRefToBlob(projectId: String, repoName: String, bucket: String, refKey: String, blobIds: Set<String>) {
        if (blobIds.size > DEFAULT_BLOB_SIZE_LIMIT) {
            val ref = "$projectId/$repoName/${buildRef(bucket, refKey)}"
            logger.error("blobs of ref[$ref] exceed size limit, size[${blobIds.size}]]")
        }
        blobIds.forEach {
            try {
                insert(
                    TDdcBlobRef(
                        id = null,
                        projectId = projectId,
                        repoName = repoName,
                        blobId = it,
                        ref = buildRef(bucket, refKey)
                    )
                )
            } catch (ignore: DuplicateKeyException) {
            }
        }
    }

    fun removeRefFromBlob(projectId: String, repoName: String, bucket: String, refKey: String): List<TDdcBlobRef> {
        val criteria = Criteria
            .where(TDdcBlobRef::projectId.name).isEqualTo(projectId)
            .and(TDdcBlobRef::repoName.name).isEqualTo(repoName)
            .and(TDdcBlobRef::ref.name).isEqualTo(buildRef(bucket, refKey))
        return determineMongoTemplate().findAllAndRemove(Query(criteria), TDdcBlobRef::class.java)
    }

    companion object {
        private const val DEFAULT_BLOB_SIZE_LIMIT = 20
    }
}
