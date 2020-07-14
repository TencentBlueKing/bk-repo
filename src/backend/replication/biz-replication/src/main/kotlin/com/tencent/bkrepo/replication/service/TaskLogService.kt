package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.replication.model.TReplicationTaskLog
import com.tencent.bkrepo.replication.pojo.log.ReplicationTaskLog
import com.tencent.bkrepo.replication.repository.TaskLogRepository
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter

@Service
class TaskLogService(
    private val taskLogRepository: TaskLogRepository
) {

    fun list(taskKey: String): List<ReplicationTaskLog> {
        return taskLogRepository.findByTaskKeyOrderByStartTimeDesc(taskKey).map { convert(it)!! }
    }

    fun latest(taskKey: String): ReplicationTaskLog? {
        return convert(taskLogRepository.findFirstByTaskKeyOrderByStartTimeDesc(taskKey))
    }

    companion object {
        private fun convert(log: TReplicationTaskLog?): ReplicationTaskLog? {
            return log?.let {
                ReplicationTaskLog(
                    taskKey = it.taskKey,
                    status = it.status,
                    replicationProgress = it.replicationProgress,
                    startTime = it.startTime.format(DateTimeFormatter.ISO_DATE_TIME),
                    endTime = it.endTime?.format(DateTimeFormatter.ISO_DATE_TIME),
                    errorReason = it.errorReason
                )
            }
        }
    }
}
