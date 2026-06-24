package com.tencent.bkrepo.common.metadata.service.file.impl

import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.dao.file.FileRefCompensationDao
import com.tencent.bkrepo.common.metadata.model.TFileRefCompensation
import com.tencent.bkrepo.common.metadata.model.TFileRefCompensation.Companion.MAX_RETRY
import com.tencent.bkrepo.common.metadata.model.TFileRefCompensation.Companion.STATUS_DONE
import com.tencent.bkrepo.common.metadata.model.TFileRefCompensation.Companion.STATUS_FAILED
import com.tencent.bkrepo.common.metadata.model.TFileRefCompensation.Companion.STATUS_PENDING
import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 分库场景下 node 插入成功后异步补写 file_reference 引用计数的补偿服务。
 */
@Component
@Conditional(SyncCondition::class)
class FileRefCompensationService(
    private val dao: FileRefCompensationDao,
    private val fileReferenceService: FileReferenceService,
) {
    fun enqueue(sha256: String, credentialsKey: String?) {
        dao.save(TFileRefCompensation(sha256 = sha256, credentialsKey = credentialsKey))
    }

    @Scheduled(
        fixedDelayString = "\${spring.data.mongodb.multi-instance.compensation.file-ref.consume-interval-ms:500}",
        initialDelayString = "\${spring.data.mongodb.multi-instance.compensation.file-ref.consume-interval-ms:500}",
    )
    fun consume() {
        val tasks = dao.findPending(BATCH_SIZE)
        if (tasks.isEmpty()) return
        logger.debug("Processing {} file_reference compensation tasks", tasks.size)
        for (task in tasks) {
            processSingle(task)
        }
    }

    private fun processSingle(task: TFileRefCompensation) {
        val id = task.id ?: return
        try {
            fileReferenceService.increment(task.sha256, task.credentialsKey)
            dao.updateFirst(
                Query(Criteria.where("_id").`is`(id)),
                Update().set(TFileRefCompensation::status.name, STATUS_DONE),
            )
        } catch (e: Exception) {
            val newRetry = task.retryCount + 1
            if (newRetry >= MAX_RETRY) {
                logger.error(
                    "file_reference compensation failed after $MAX_RETRY retries, " +
                        "sha256=${task.sha256} credentialsKey=${task.credentialsKey}",
                    e,
                )
                dao.updateFirst(
                    Query(Criteria.where("_id").`is`(id)),
                    Update().set(TFileRefCompensation::status.name, STATUS_FAILED)
                        .set(TFileRefCompensation::retryCount.name, newRetry),
                )
            } else {
                logger.warn(
                    "file_reference compensation retry $newRetry/${MAX_RETRY - 1}, " +
                        "sha256=${task.sha256}: ${e.message}",
                )
                dao.updateFirst(
                    Query(Criteria.where("_id").`is`(id)),
                    Update().set(TFileRefCompensation::status.name, STATUS_PENDING)
                        .set(TFileRefCompensation::retryCount.name, newRetry),
                )
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileRefCompensationService::class.java)
        private const val BATCH_SIZE = 200
    }
}
