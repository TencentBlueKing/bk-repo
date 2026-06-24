package com.tencent.bkrepo.job.batch.task.sync

import com.tencent.bkrepo.common.metadata.model.TFileReference
import com.tencent.bkrepo.job.COLLECTION_NAME_FILE_REFERENCE
import com.tencent.bkrepo.job.SHARDING_COUNT
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * G-09：历史 file_reference 记录回填 [TFileReference.lastRefCountUpdate]。
 */
@Component
@ConditionalOnProperty(
    prefix = "job.file-ref-backfill",
    name = ["enabled"],
    havingValue = "true",
)
class FileRefCountBackfillJob(
    private val mongoTemplate: MongoTemplate,
) {

    @Scheduled(fixedDelayString = "\${job.file-ref-backfill.interval-ms:3600000}")
    fun backfill() {
        var modified = 0L
        val now = LocalDateTime.now()
        for (seq in 0 until SHARDING_COUNT) {
            val collection = "${COLLECTION_NAME_FILE_REFERENCE}_$seq"
            val query = Query(Criteria.where(TFileReference::lastRefCountUpdate.name).`is`(null))
            val update = Update().set(TFileReference::lastRefCountUpdate.name, now)
            modified += mongoTemplate.updateMulti(query, update, collection).modifiedCount
        }
        if (modified > 0) {
            logger.info("FileRefCountBackfillJob updated lastRefCountUpdate on $modified documents")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileRefCountBackfillJob::class.java)
    }
}
