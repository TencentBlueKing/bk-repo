package com.tencent.bkrepo.replication.service.impl.failure

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.replication.dao.ReplicaFailureRecordDao
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TReplicaFailureRecord
import com.tencent.bkrepo.replication.pojo.failure.ReplicaFailureRecordDeleteRequest
import com.tencent.bkrepo.replication.pojo.failure.ReplicaFailureRecordListOption
import com.tencent.bkrepo.replication.pojo.failure.ReplicaFailureRecordRetryRequest
import com.tencent.bkrepo.replication.service.ReplicaFailureRecordService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 同步失败记录服务实现
 */
@Service
class ReplicaFailureRecordServiceImpl(
    private val replicaFailureRecordDao: ReplicaFailureRecordDao,
    private val failureRecordRetryExecutor: FailureRecordRetryExecutor,
    private val retryStateManager: FailureRecordRetryStateManager
) : ReplicaFailureRecordService {

    companion object {
        private val logger = LoggerFactory.getLogger(ReplicaFailureRecordServiceImpl::class.java)
    }

    override fun getRecordsForRetry(maxRetryTimes: Int): List<TReplicaFailureRecord> {
        return replicaFailureRecordDao.findByTriedTimesLessThanAndRetryingFalse(maxRetryTimes)
    }

    override fun updateRetryStatus(recordId: String, retrying: Boolean) {
        replicaFailureRecordDao.updateRetryStatus(recordId, retrying)
    }

    override fun incrementRetryCount(recordId: String, failureReason: String?) {
        replicaFailureRecordDao.incrementRetryCount(recordId, failureReason)
    }

    override fun deleteRecord(recordId: String) {
        replicaFailureRecordDao.deleteById(recordId)
    }

    override fun cleanExpiredRecords(maxRetryNum: Int, retentionDays: Long): Long {
        val expireBefore = LocalDateTime.now().minusDays(retentionDays)
        return replicaFailureRecordDao.deleteExpiredRecords(maxRetryNum, expireBefore)
    }

    override fun listPage(option: ReplicaFailureRecordListOption): Page<TReplicaFailureRecord> {
        val direction = option.sortDirection?.let {
            try {
                Sort.Direction.valueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                Sort.Direction.DESC
            }
        } ?: Sort.Direction.DESC

        val query = replicaFailureRecordDao.buildQuery(
            taskKey = option.taskKey,
            remoteClusterId = option.remoteClusterId,
            projectId = option.projectId,
            repoName = option.repoName,
            remoteProjectId = option.remoteProjectId,
            remoteRepoName = option.remoteRepoName,
            failureType = option.failureType,
            retrying = option.retrying,
            maxRetryCount = option.maxRetryCount,
            sortField = option.sortField,
            sortDirection = direction
        )

        val pageRequest = Pages.ofRequest(option.pageNumber, option.pageSize)
        val totalRecords = replicaFailureRecordDao.count(query)
        val records = replicaFailureRecordDao.find(query.with(pageRequest))
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    override fun findById(id: String): TReplicaFailureRecord? {
        return replicaFailureRecordDao.findById(id)
    }

    override fun deleteByConditions(request: ReplicaFailureRecordDeleteRequest): Long {
        // 安全检查：至少需要一个删除条件，避免误删所有记录
        if (request.ids.isNullOrEmpty() && request.maxRetryCount == null) {
            throw ErrorCodeException(
                CommonMessageCode.PARAMETER_MISSING,
                "At least one delete condition is required"
            )
        }

        return replicaFailureRecordDao.deleteByConditions(request.ids, request.maxRetryCount)
    }

    override fun retryRecord(request: ReplicaFailureRecordRetryRequest): Boolean {
        val record = replicaFailureRecordDao.findById(request.id)
            ?: throw ErrorCodeException(
                ReplicationMessageCode.REPLICA_FAILURE_RECORD_NOT_FOUND,
                request.id
            )

        return retryStateManager.executeWithRetryState(record) {
            failureRecordRetryExecutor.execute(record)
        }
    }
}
