package com.tencent.bkrepo.replication.service.impl.failure

import com.tencent.bkrepo.replication.model.TReplicaFailureRecord
import com.tencent.bkrepo.replication.service.ReplicaRetryService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 失败记录重试执行器
 * 负责执行重试操作并处理结果
 */
@Component
class FailureRecordRetryExecutor(
    private val replicaRetryService: ReplicaRetryService,
    private val failureRecordRepository: FailureRecordRepository
) {

    /**
     * 执行重试操作
     */
    fun execute(record: TReplicaFailureRecord): Boolean {
        return try {
            // 调用重试服务
            val success = replicaRetryService.retryFailureRecord(record)

            if (success) {
                // 成功后删除记录
                failureRecordRepository.deleteById(record.id!!)
                logger.info("Successfully retried and deleted record[${record.id}]")
                true
            } else {
                logger.warn("Failed to retry record[${record.id}]")
                false
            }
        } catch (e: Exception) {
            logger.warn("Error retrying record[${record.id}]: ${e.message}", e)
            throw e
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FailureRecordRetryExecutor::class.java)
    }
}

