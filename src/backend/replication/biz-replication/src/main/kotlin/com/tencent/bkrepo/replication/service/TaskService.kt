package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.common.api.constant.StringPool.UNKNOWN
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.replication.api.DataReplicationService
import com.tencent.bkrepo.replication.config.FeignClientFactory
import com.tencent.bkrepo.replication.constant.ReplicationMessageCode
import com.tencent.bkrepo.replication.job.ReplicaJobContext
import com.tencent.bkrepo.replication.model.TReplicationTask
import com.tencent.bkrepo.replication.pojo.request.ReplicationTaskCreateRequest
import com.tencent.bkrepo.replication.pojo.setting.RemoteClusterInfo
import com.tencent.bkrepo.replication.pojo.task.ReplicationProgress
import com.tencent.bkrepo.replication.pojo.task.ReplicationStatus
import com.tencent.bkrepo.replication.pojo.task.ReplicationTaskInfo
import com.tencent.bkrepo.replication.pojo.task.ReplicationType
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

    fun testConnect(remoteClusterInfo: RemoteClusterInfo) {
        tryConnect(remoteClusterInfo)
    }

    fun create(userId: String, request: ReplicationTaskCreateRequest) {
        with(request) {
            validate(this)
            val task = TReplicationTask(
                createdBy = userId,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = userId,
                lastModifiedDate = LocalDateTime.now(),

                includeAllProject = includeAllProject,
                localProjectId = if (includeAllProject) null else localProjectId,
                localRepoName = if (includeAllProject) null else localRepoName,
                remoteProjectId = if (includeAllProject) null else remoteProjectId,
                remoteRepoName = if (includeAllProject) null else remoteRepoName,

                type = type,
                setting = setting,
                replicationProgress = ReplicationProgress(),
                status = ReplicationStatus.WAITING
            )
            taskRepository.insert(task)
            if (type == ReplicationType.FULL) {
                scheduleService.createJob(task)
            }
        }
        logger.info("Create replica task success.")
    }


    fun detail(id: String): ReplicationTaskInfo? {
        return taskRepository.findByIdOrNull(id)?.let { convert(it) }
    }

    fun list(): List<ReplicationTaskInfo> {
        return taskRepository.findAll().map { convert(it)!! }
    }

    fun pause(id: String) {
        val task = taskRepository.findByIdOrNull(id) ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, id)
        if (task.status == ReplicationStatus.REPLICATING) {
            if (task.type == ReplicationType.FULL) {
                scheduleService.pauseJob(task.id!!)
            }
            task.status = ReplicationStatus.PAUSED
            taskRepository.save(task)
        } else {
            throw ErrorCodeException(ReplicationMessageCode.TASK_STATUS_INVALID)
        }
    }

    fun resume(id: String) {
        val task = taskRepository.findByIdOrNull(id) ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, id)
        if (task.status == ReplicationStatus.PAUSED) {
            if (task.type == ReplicationType.FULL) {
                scheduleService.resumeJob(task.id!!)
            }
            task.status = ReplicationStatus.REPLICATING
            taskRepository.save(task)
        } else {
            throw ErrorCodeException(ReplicationMessageCode.TASK_STATUS_INVALID)
        }
    }

    fun delete(id: String) {
        val task = taskRepository.findByIdOrNull(id) ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, id)
        if (task.type == ReplicationType.FULL) {
            scheduleService.deleteJob(task.id!!)
        }
        taskRepository.delete(task)
    }

    private fun validate(request: ReplicationTaskCreateRequest) {
        with(request) {
            if (!includeAllProject && localProjectId == null) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, request::localProjectId.name)
            }
            tryConnect(setting.remoteClusterInfo)
        }
    }

    private fun tryConnect(remoteClusterInfo: RemoteClusterInfo) {
        with(remoteClusterInfo) {
            val replicationService = FeignClientFactory.create(DataReplicationService::class.java, this)
            try {
                val authToken = ReplicaJobContext.encodeAuthToken(username, password)
                replicationService.ping(authToken)
            } catch (exception: Exception) {
                logger.error("Connect remote cluster[${remoteClusterInfo.url}] failed.", exception)
                throw ErrorCodeException(ReplicationMessageCode.REMOTE_CLUSTER_CONNECT_ERROR, exception.message ?: UNKNOWN)
            }
        }

    }

    private fun convert(task: TReplicationTask?): ReplicationTaskInfo? {
        return task?.let {
            ReplicationTaskInfo(
                id = it.id!!,
                createdBy = it.createdBy,
                createdDate = it.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                lastModifiedBy = it.lastModifiedBy,
                lastModifiedDate = it.lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),

                includeAllProject = it.includeAllProject,
                localProjectId = it.localProjectId,
                localRepoName = it.localRepoName,
                remoteProjectId = it.remoteProjectId,
                remoteRepoName = it.remoteRepoName,

                type = it.type,
                setting = it.setting,
                status = it.status,
                replicationProgress = it.replicationProgress,
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
