package com.tencent.bkrepo.replication.service.impl.failure

import com.tencent.bkrepo.replication.model.TReplicaFailureRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 失败记录重试状态管理器
 * 负责管理失败记录的重试状态
 */
@Component
class FailureRecordRetryStateManager(
    private val failureRecordRepository: FailureRecordRepository
) {

    /**
     * 执行重试操作（带状态管理）
     */
    fun executeWithRetryState(
        record: TReplicaFailureRecord,
        block: () -> Boolean
    ): Boolean {
        var success = false
        return try {
            // 设置重试状态为 true
            try {
                failureRecordRepository.updateRetryStatus(record.id!!, true)
            } catch (e: Exception) {
                // 即使设置状态失败，也继续执行重试逻辑
                logger.error("Failed to set retry status for record[${record.id}]", e)
            }
            
            success = block()
            if (!success) {
                handleRetryFailure(record, "Retry failed")
            }
            success
        } catch (e: Exception) {
            val errorMessage = "${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}"
            handleRetryFailure(record, errorMessage)
            // 返回 false 而不是抛出异常，保持与原代码行为一致
            false
        } finally {
            // 只有在重试失败时才需要重置状态
            // 如果重试成功，记录已被删除，不需要重置状态
            if (!success) {
                failureRecordRepository.updateRetryStatus(record.id!!, false)
            }
        }
    }

    /**
     * 处理重试失败
     */
    private fun handleRetryFailure(record: TReplicaFailureRecord, errorMessage: String) {
        failureRecordRepository.incrementRetryCount(record.id!!, errorMessage)
        logger.warn("Failed to retry record[${record.id}]: $errorMessage")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FailureRecordRetryStateManager::class.java)
    }
}

