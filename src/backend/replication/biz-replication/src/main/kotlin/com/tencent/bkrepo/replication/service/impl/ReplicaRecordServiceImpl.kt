package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.replication.dao.ReplicaRecordDao
import com.tencent.bkrepo.replication.dao.ReplicaRecordDetailDao
import com.tencent.bkrepo.replication.message.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TReplicaRecord
import com.tencent.bkrepo.replication.model.TReplicaRecordDetail
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.record.ReplicaProgress
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordDetail
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordDetailListOption
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordInfo
import com.tencent.bkrepo.replication.pojo.record.request.RecordDetailInitialRequest
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.replication.util.TaskRecordQueryHelper
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ReplicaRecordServiceImpl(
    private val replicaRecordDao: ReplicaRecordDao,
    private val replicaRecordDetailDao: ReplicaRecordDetailDao
) : ReplicaRecordService {
    override fun initialRecord(taskKey: String): ReplicaRecordInfo {
        val record = TReplicaRecord(
            taskKey = taskKey,
            status = ExecutionStatus.RUNNING,
            startTime = LocalDateTime.now()
        )
        return try {
            replicaRecordDao.insert(record).let { convert(it)!! }
        } catch (exception: DuplicateKeyException) {
            logger.warn("init record [$taskKey] error: [${exception.message}]")
            throw exception
        }
    }

    override fun completeRecord(recordId: String, status: ExecutionStatus, errorReason: String?) {
        val replicaRecordInfo = getRecordById(recordId)
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, recordId)
        val record = with(replicaRecordInfo) {
            TReplicaRecord(
                id = id,
                taskKey = taskKey,
                status = status,
                startTime = startTime,
                endTime = LocalDateTime.now(),
                errorReason = errorReason
            )
        }
        replicaRecordDao.save(record)
    }

    override fun initialRecordDetail(request: RecordDetailInitialRequest): ReplicaRecordDetail {
        with(request) {
            val recordDetail = TReplicaRecordDetail(
                recordId = recordId,
                localCluster = localCluster,
                remoteCluster = remoteCluster,
                status = ExecutionStatus.RUNNING,
                progress = ReplicaProgress(),
                startTime = LocalDateTime.now()
            )
            return try {
                replicaRecordDetailDao.insert(recordDetail).let { convert(it)!! }
            } catch (exception: DuplicateKeyException) {
                logger.warn("init record detail [$recordId] error: [${exception.message}]")
                throw exception
            }
        }
    }

    override fun updateRecordDetailProgress(detailId: String, progress: ReplicaProgress) {
        val replicaRecordDetail = findRecordDetailById(detailId)
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, detailId)
        replicaRecordDetail.progress.blob = progress.blob
        replicaRecordDetail.progress.node = progress.node
        replicaRecordDetail.progress.version = progress.version
        replicaRecordDetail.progress.totalSize = progress.totalSize
        replicaRecordDetailDao.save(replicaRecordDetail)
        logger.info("Update record detail [$detailId] success.")
    }

    override fun completeRecordDetail(detailId: String, status: ExecutionStatus, errorReason: String?) {
        val replicaRecordDetail = getRecordDetailById(detailId)
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, detailId)
        val recordDetail = with(replicaRecordDetail) {
            TReplicaRecordDetail(
                id = detailId,
                recordId = recordId,
                localCluster = localCluster,
                remoteCluster = remoteCluster,
                status = status,
                progress = progress,
                startTime = startTime,
                endTime = LocalDateTime.now(),
                errorReason = errorReason
            )
        }
        replicaRecordDetailDao.save(recordDetail)
    }

    override fun listRecordsByTaskKey(key: String): List<ReplicaRecordInfo> {
        return replicaRecordDao.listByTaskKey(key).map { convert(it)!! }
    }

    override fun listDetailsByRecordId(recordId: String): List<ReplicaRecordDetail> {
        return replicaRecordDetailDao.listByRecordId(recordId).map { convert(it)!! }
    }

    override fun getRecordById(id: String): ReplicaRecordInfo? {
        return convert(replicaRecordDao.findById(id))
    }

    override fun getRecordDetailById(id: String): ReplicaRecordDetail? {
        return convert(findRecordDetailById(id))
    }

    private fun findRecordDetailById(id: String): TReplicaRecordDetail? {
        return replicaRecordDetailDao.findById(id)
    }

    override fun deleteByTaskKey(key: String) {
        replicaRecordDao.listByTaskKey(key).forEach {
            replicaRecordDetailDao.deleteByRecordId(it.id!!)
        }
        replicaRecordDao.deleteByTaskKey(key)
    }

    override fun listRecordDetailPage(
        recordId: String,
        option: ReplicaRecordDetailListOption
    ): Page<ReplicaRecordDetail> {
        val pageNumber = option.pageNumber
        val pageSize = option.pageSize
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val query = TaskRecordQueryHelper.detailListQuery(
            recordId, option.packageName, option.repoName, option.clusterName
        )
        val totalRecords = replicaRecordDetailDao.count(query)
        val records = replicaRecordDetailDao.find(query.with(pageRequest)).map { convert(it)!! }
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReplicaRecordServiceImpl::class.java)

        private fun convert(tReplicaRecord: TReplicaRecord?): ReplicaRecordInfo? {
            return tReplicaRecord?.let {
                ReplicaRecordInfo(
                    id = it.id!!,
                    taskKey = it.taskKey,
                    status = it.status,
                    startTime = it.startTime,
                    endTime = it.endTime,
                    errorReason = it.errorReason
                )
            }
        }

        private fun convert(tReplicaRecordDetail: TReplicaRecordDetail?): ReplicaRecordDetail? {
            return tReplicaRecordDetail?.let {
                ReplicaRecordDetail(
                    recordId = it.recordId,
                    localCluster = it.localCluster,
                    remoteCluster = it.remoteCluster,
                    status = it.status,
                    progress = it.progress,
                    startTime = it.startTime,
                    endTime = it.endTime,
                    errorReason = it.errorReason
                )
            }
        }
    }
}
