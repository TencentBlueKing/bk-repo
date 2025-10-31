package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.replication.dao.ReplicaFailureRecordDao
import com.tencent.bkrepo.replication.model.TReplicaFailureRecord
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.service.ReplicaFailureRecordService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 同步失败记录服务实现
 */
@Service
class ReplicaFailureRecordServiceImpl(
    private val replicaFailureRecordDao: ReplicaFailureRecordDao,
) : ReplicaFailureRecordService {

    companion object {
        private val logger = LoggerFactory.getLogger(ReplicaFailureRecordServiceImpl::class.java)
    }

    override fun recordFailure(
        taskKey: String,
        remoteClusterId: String,
        projectId: String,
        localRepoName: String,
        remoteProjectId: String,
        remoteRepoName: String,
        failureType: ReplicaObjectType,
        packageKey: String?,
        packageVersion: String?,
        fullPath: String?,
        failureReason: String?
    ) {
        // 先查找是否存在相同失败记录
        val existingRecord = replicaFailureRecordDao.findExistingRecord(
            taskKey = taskKey,
            projectId = projectId,
            localRepoName = localRepoName,
            remoteProjectId = remoteProjectId,
            remoteRepoName = remoteRepoName,
            failureType = failureType,
            packageKey = packageKey,
            packageVersion = packageVersion,
            fullPath = fullPath,
            remoteClusterId = remoteClusterId
        )

        if (existingRecord != null) {
            // 如果存在相同记录，则更新失败原因和重试次数
            replicaFailureRecordDao.updateExistingRecord(
                recordId = existingRecord.id!!,
                failureReason = failureReason,
                incrementRetryCount = true
            )
            logger.info(
                "Updated existing failure record[${existingRecord.id}] for task[$taskKey], " +
                    "type[$failureType], reason[$failureReason], retry count: ${existingRecord.retryCount + 1}"
            )
        } else {
            // 如果不存在相同记录，则创建新记录
            val failureRecord = TReplicaFailureRecord(
                taskKey = taskKey,
                projectId = projectId,
                repoName = localRepoName,
                remoteProjectId = remoteProjectId,
                remoteRepoName = remoteRepoName,
                failureType = failureType,
                packageKey = packageKey,
                packageVersion = packageVersion,
                failureReason = failureReason,
                fullPath = fullPath,
                retryCount = 0,
                retrying = false,
                remoteClusterId = remoteClusterId
            )
            replicaFailureRecordDao.save(failureRecord)
            logger.info("Created new failure record for task[$taskKey], type[$failureType], reason[$failureReason]")
        }
    }

    override fun getRecordsForRetry(maxRetryTimes: Int): List<TReplicaFailureRecord> {
        return replicaFailureRecordDao.findByTriedTimesLessThanAndRetryingFalse(maxRetryTimes)
    }

    override fun updateRetryStatus(recordId: String, retrying: Boolean) {
        replicaFailureRecordDao.updateRetryStatus(recordId, retrying)
        logger.info("Updated retry status for record[$recordId] to $retrying")
    }

    override fun incrementRetryCount(recordId: String, failureReason: String?) {
        replicaFailureRecordDao.incrementRetryCount(recordId, failureReason)
        logger.info("Incremented retry count for record[$recordId]")
    }

    override fun deleteRecord(recordId: String) {
        replicaFailureRecordDao.deleteById(recordId)
        logger.info("Deleted failure record[$recordId]")
    }

    override fun cleanExpiredRecords(maxRetryNum: Int, retentionDays: Long): Long {
        val expireBefore = LocalDateTime.now().minusDays(retentionDays)
        return replicaFailureRecordDao.deleteExpiredRecords(maxRetryNum, expireBefore)
    }
}
