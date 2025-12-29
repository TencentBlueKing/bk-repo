package com.tencent.bkrepo.replication.service.impl.tracking

import com.tencent.bkrepo.replication.dao.FederationMetadataTrackingDao
import com.tencent.bkrepo.replication.model.TFederationMetadataTracking
import org.slf4j.LoggerFactory

/**
 * 重试状态管理器
 * 负责管理跟踪记录的重试状态和重试信息
 */
class RetryStateManager(
    private val federationMetadataTrackingDao: FederationMetadataTrackingDao
) {

    /**
     * 设置重试状态
     */
    fun setRetrying(recordId: String, retrying: Boolean, incrementRetryCount: Boolean = false) {
        federationMetadataTrackingDao.setRetrying(
            id = recordId,
            retrying = retrying,
            incrementRetryCount = incrementRetryCount
        )
    }

    /**
     * 更新重试信息
     */
    fun updateRetryInfo(recordId: String, failureReason: String?) {
        federationMetadataTrackingDao.updateRetryInfo(recordId, failureReason)
    }

    /**
     * 处理重试失败
     */
    fun handleRetryFailure(record: TFederationMetadataTracking, errorMessage: String) {
        val newRetryCount = record.retryCount + 1
        updateRetryInfo(record.id!!, errorMessage)
        logger.warn(
            "File transfer failed for node ${record.nodePath}, " +
                "retry count: $newRetryCount, error: $errorMessage"
        )
    }

    /**
     * 执行重试操作（带状态管理）
     */
    fun executeWithRetryState(
        record: TFederationMetadataTracking,
        incrementRetryCount: Boolean = false,
        block: () -> Boolean
    ): Boolean {
        // 设置重试状态为 true
        setRetrying(record.id!!, true, incrementRetryCount = incrementRetryCount)

        return try {
            val success = block()
            if (!success) {
                handleRetryFailure(record, "File transfer failed")
            }
            success
        } catch (e: Exception) {
            val errorMessage = "${e.javaClass.simpleName}: ${e.message}"
            handleRetryFailure(record, errorMessage)
            false
        } finally {
            // 确保重试状态被重置
            setRetrying(record.id!!, false)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RetryStateManager::class.java)
    }
}

