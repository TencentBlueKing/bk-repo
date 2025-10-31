package com.tencent.bkrepo.replication.service.impl.failure

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TReplicaFailureRecord
import com.tencent.bkrepo.replication.pojo.failure.ReplicaFailureRecordDeleteRequest
import com.tencent.bkrepo.replication.pojo.failure.ReplicaFailureRecordListOption
import com.tencent.bkrepo.replication.pojo.failure.ReplicaFailureRecordRetryRequest
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.pojo.task.objects.PackageConstraint
import com.tencent.bkrepo.replication.pojo.task.objects.PathConstraint
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
    private val failureRecordRepository: FailureRecordRepository,
    private val failureRecordRetryExecutor: FailureRecordRetryExecutor,
    private val retryStateManager: FailureRecordRetryStateManager
) : ReplicaFailureRecordService {

    companion object {
        private val logger = LoggerFactory.getLogger(ReplicaFailureRecordServiceImpl::class.java)
    }

    override fun getRecordsForRetry(maxRetryTimes: Int): List<TReplicaFailureRecord> {
        return failureRecordRepository.findByTriedTimesLessThanAndRetryingFalse(maxRetryTimes)
    }

    override fun updateRetryStatus(recordId: String, retrying: Boolean) {
        failureRecordRepository.updateRetryStatus(recordId, retrying)
    }

    override fun incrementRetryCount(recordId: String, failureReason: String?) {
        failureRecordRepository.incrementRetryCount(recordId, failureReason)
    }

    override fun deleteRecord(recordId: String) {
        failureRecordRepository.deleteById(recordId)
    }

    override fun cleanExpiredRecords(maxRetryNum: Int, retentionDays: Long): Long {
        val expireBefore = LocalDateTime.now().minusDays(retentionDays)
        return failureRecordRepository.deleteExpiredRecords(maxRetryNum, expireBefore)
    }

    override fun listPage(option: ReplicaFailureRecordListOption): Page<TReplicaFailureRecord> {
        val direction = option.sortDirection?.let {
            try {
                Sort.Direction.valueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                Sort.Direction.DESC
            }
        } ?: Sort.Direction.DESC

        val query = failureRecordRepository.buildQuery(option, direction)

        val pageRequest = Pages.ofRequest(option.pageNumber, option.pageSize)
        val totalRecords = failureRecordRepository.count(query)
        val records = failureRecordRepository.find(query.with(pageRequest))
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    override fun findById(id: String): TReplicaFailureRecord? {
        return failureRecordRepository.findById(id)
    }

    override fun deleteByConditions(request: ReplicaFailureRecordDeleteRequest): Long {
        // 安全检查：至少需要一个删除条件，避免误删所有记录
        if (!failureRecordRepository.validateDeleteConditions(request)) {
            throw ErrorCodeException(
                CommonMessageCode.PARAMETER_MISSING,
                "At least one delete condition is required"
            )
        }

        return failureRecordRepository.deleteByConditions(request.ids, request.maxRetryCount)
    }

    override fun retryRecord(request: ReplicaFailureRecordRetryRequest): Boolean {
        val record = failureRecordRepository.findById(request.id)
            ?: throw ErrorCodeException(
                ReplicationMessageCode.REPLICA_FAILURE_RECORD_NOT_FOUND,
                request.id
            )

        return retryStateManager.executeWithRetryState(record) {
            failureRecordRetryExecutor.execute(record)
        }
    }
}
