package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.replication.dao.ReplicaRecordDao
import com.tencent.bkrepo.replication.dao.ReplicaRecordDetailDao
import com.tencent.bkrepo.replication.model.TReplicaRecord
import com.tencent.bkrepo.replication.model.TReplicaRecordDetail
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordDetail
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordInfo
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import org.springframework.stereotype.Service

@Service
class ReplicaRecordServiceImpl(
    private val replicaRecordDao: ReplicaRecordDao,
    private val replicaRecordDetailDao: ReplicaRecordDetailDao
) : ReplicaRecordService {
    override fun listRecordsByTaskKey(key: String): List<ReplicaRecordInfo> {
        return replicaRecordDao.listByTaskKey(key).map { convert(it)!! }
    }

    override fun listDetailsByRecordId(recordId: String): List<ReplicaRecordDetail> {
        return replicaRecordDetailDao.listByRecordId(recordId).map { convert(it)!! }
    }

    override fun deleteByTaskKey(key: String) {
        replicaRecordDao.listByTaskKey(key).forEach {
            replicaRecordDetailDao.deleteByRecordId(it.id!!)
        }
        replicaRecordDao.deleteByTaskKey(key)
    }

    companion object {
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
