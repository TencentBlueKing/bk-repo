package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.replication.api.PingResource
import com.tencent.bkrepo.replication.config.FeignClientFactory
import com.tencent.bkrepo.replication.constant.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TReplicaTask
import com.tencent.bkrepo.replication.pojo.ReplicaProgress
import com.tencent.bkrepo.replication.pojo.ReplicaTaskCreateRequest
import com.tencent.bkrepo.replication.pojo.ReplicaTaskInfo
import com.tencent.bkrepo.replication.pojo.ReplicationStatus
import com.tencent.bkrepo.replication.pojo.ReplicationType
import com.tencent.bkrepo.replication.repository.TaskRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class TaskService @Autowired constructor(
    private val taskRepository: TaskRepository,
    private val scheduleService: ScheduleService
) {
    fun create(userId: String, request: ReplicaTaskCreateRequest) {
        with(request) {
            val task = TReplicaTask(
                createdBy = userId,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = userId,
                lastModifiedDate = LocalDateTime.now(),
                type = type,
                setting = setting,
                replicaProgress = ReplicaProgress(),
                status = ReplicationStatus.WAITING
            )
            validate(request)
            taskRepository.insert(task)
            scheduleService.createJob(task)
            logger.info("Create replica task success!")
        }
    }

    fun detail(id: String): ReplicaTaskInfo? {
        return taskRepository.findByIdOrNull(id)?.let { convert(it) }
    }

    fun list(): List<ReplicaTaskInfo> {
        return taskRepository.findAll().map { convert(it)!! }
    }

    fun pause(id: String) {
        val task = taskRepository.findByIdOrNull(id) ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, id)
        if (task.type == ReplicationType.FULL) {
            if (task.status == ReplicationStatus.REPLICATING) {
                scheduleService.pauseJob(task.id!!)
                task.status = ReplicationStatus.PAUSED
                taskRepository.save(task)
            } else {
                throw ErrorCodeException(ReplicationMessageCode.TASK_STATUS_INVALID)
            }
        }
    }

    fun resume(id: String) {
        val task = taskRepository.findByIdOrNull(id) ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, id)
        if (task.type == ReplicationType.FULL) {
            if (task.status == ReplicationStatus.PAUSED) {
                scheduleService.resumeJob(task.id!!)
                task.status = ReplicationStatus.REPLICATING
                taskRepository.save(task)
            } else {
                throw ErrorCodeException(ReplicationMessageCode.TASK_STATUS_INVALID)
            }
        }
    }

    fun delete(id: String) {
        val task = taskRepository.findByIdOrNull(id) ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, id)
        if (task.type == ReplicationType.FULL) {
            scheduleService.deleteJob(task.id!!)
            taskRepository.delete(task)
        }
    }

    private fun validate(request: ReplicaTaskCreateRequest) {
        with(request.setting) {
            try {
                val pingResource = FeignClientFactory.create(PingResource::class.java, remoteClusterInfo)
                pingResource.ping()
            } catch (exception: Exception) {
                logger.error("connect remote cluster[${remoteClusterInfo.url}] failed: $exception")
                throw ErrorCodeException(ReplicationMessageCode.REMOTE_CLUSTER_CONNECT_ERROR)
            }
        }
    }

    private fun convert(task: TReplicaTask?): ReplicaTaskInfo? {
        return task?.let {
            ReplicaTaskInfo(
                id = it.id!!,
                createdBy = it.createdBy,
                createdDate = it.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                lastModifiedBy = it.lastModifiedBy,
                lastModifiedDate = it.lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),
                type = it.type,
                setting = it.setting,
                status = it.status,
                replicaProgress = it.replicaProgress,
                startTime = it.startTime?.format(DateTimeFormatter.ISO_DATE_TIME),
                endTime = it.endTime?.format(DateTimeFormatter.ISO_DATE_TIME),
                errorReason = it.errorReason
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TaskService::class.java)
    }
}
