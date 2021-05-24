/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.replication.api.ArtifactReplicaClient
import com.tencent.bkrepo.replication.config.FeignClientFactory
import com.tencent.bkrepo.replication.constant.DEFAULT_GROUP_ID
import com.tencent.bkrepo.replication.constant.TASK_ID
import com.tencent.bkrepo.replication.job.ReplicaContext
import com.tencent.bkrepo.replication.job.ScheduledReplicaJob
import com.tencent.bkrepo.replication.message.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TReplicaTask
import com.tencent.bkrepo.replication.pojo.cluster.RemoteClusterInfo
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.request.ReplicationInfo
import com.tencent.bkrepo.replication.pojo.request.ReplicationTaskUpdateRequest
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.pojo.task.ReplicationStatus
import com.tencent.bkrepo.replication.pojo.task.ReplicationType
import com.tencent.bkrepo.replication.pojo.task.request.ReplicaTaskCreateRequest
import com.tencent.bkrepo.replication.pojo.task.setting.ExecutionPlan
import com.tencent.bkrepo.replication.pojo.task.setting.ReplicaSetting
import com.tencent.bkrepo.replication.repository.TaskLogDetailRepository
import com.tencent.bkrepo.replication.repository.TaskLogRepository
import com.tencent.bkrepo.replication.repository.TaskRepository
import com.tencent.bkrepo.replication.schedule.ReplicaTaskScheduler
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.TaskService
import com.tencent.bkrepo.replication.util.CronUtils
import org.quartz.CronExpression
import org.quartz.JobBuilder
import org.quartz.JobKey
import org.quartz.TriggerBuilder
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 同步任务服务实现类
 */
@Service
class TaskServiceImpl(
    private val taskRepository: TaskRepository,
    private val taskLogRepository: TaskLogRepository,
    private val taskLogDetailRepository: TaskLogDetailRepository,
    private val mongoTemplate: MongoTemplate,
    private val clusterNodeService: ClusterNodeService,
    private val replicaTaskScheduler: ReplicaTaskScheduler,
    private val userResource: ServiceUserResource
) : TaskService {

    override fun create(request: ReplicaTaskCreateRequest): ReplicaTaskInfo {
        with(request) {

            validateRequest(request)
            val task = TReplicaTask(
                name = name,
                key = StringPool.uniqueId(),
                localProjectId = localProjectId,
                localRepoName = localRepoName,
                remoteProjectId =
                    replicationInfo = replicationInfo,
                type = type,
                setting = setting,
                status = ReplicationStatus.WAITING,
                enabled = enabled,
                createdBy = SecurityUtils.getUserId(),
                createdDate = LocalDateTime.now(),
                lastModifiedBy = SecurityUtils.getUserId(),
                lastModifiedDate = LocalDateTime.now(),
                description = description
            )
            taskRepository.insert(task)
            logger.info("Create replica task[$request] success.")
            return convert(task)!!
        }
    }

    /**
     * 验证
     */
    private fun validateRequest(request: ReplicaTaskCreateRequest) {
        with(request) {
            // 验证是否存在
            taskRepository.findByName(name)?.let {
                throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, name)
            }
            // 校验参数
            Preconditions.checkNotNull(name, this::name.name)
            Preconditions.checkNotNull(localProjectId, this::localProjectId.name)
            Preconditions.checkNotNull(localRepoName, this::localRepoName.name)
            // 暂时只支持全量
            Preconditions.checkArgument(replicaType == ReplicaType.INCREMENTAL, this::replicaType.name)
            // 远程集群不能为空
            remoteClusterSet.forEach { clusterNodeService.tryConnect(it) }
            // 包限制条件和路径限制条件只能选其一
            Preconditions.checkArgument(packageConstraints != null && pathConstraints != null, "Constraints")
            // 包限制条件
            packageConstraints?.forEach { pkg ->
                Preconditions.checkNotBlank(pkg.packageKey, this::packageConstraints.name)
                pkg.versionSet?.forEach { version -> Preconditions.checkNotBlank(version, "versionSet") }
            }
            // 路径限制条件
            pathConstraints?.forEach { PathUtils.normalizeFullPath(it) }
            // 执行计划验证
            if (!setting.executionPlan.executeImmediately) {
                setting.executionPlan.executeTime?.let {
                    Preconditions.checkArgument(it.isAfter(LocalDateTime.now()), "executeTime")
                } ?: run {
                    val cronExpression = setting.executionPlan.cronExpression
                    Preconditions.checkNotBlank(cronExpression, "cronExpression")
                    Preconditions.checkArgument(CronUtils.isValid(cronExpression.orEmpty()), "cronExpression")
                }
            }
        }
    }

    override fun detail(taskKey: String): ReplicaTaskInfo? {
        return taskRepository.findByKey(taskKey)?.let { convert(it) }
    }

    override fun listAllRemoteTask(type: ReplicationType): List<TReplicaTask> {
        val typeCriteria = TReplicaTask::type.isEqualTo(type)
        val statusCriteria = TReplicaTask::status.inValues(ReplicationStatus.WAITING, ReplicationStatus.REPLICATING)
        val criteria = Criteria().andOperator(typeCriteria, statusCriteria)

        return mongoTemplate.find(Query(criteria), TReplicaTask::class.java)
    }

    override fun listUndoFullTask(): List<TReplicaTask> {
        val criteria = Criteria.where(TReplicaTask::type.name).`is`(ReplicationType.FULL)
            .and(TReplicaTask::status.name).`in`(ReplicationStatus.UNDO_STATUS)
            .and(TReplicaTask::enabled.name).`is`(true)
        return mongoTemplate.find(Query(criteria), TReplicaTask::class.java)
    }

    override fun list(): List<ReplicaTaskInfo> {
        return taskRepository.findAll().map { convert(it)!! }
    }

    override fun listReplicationTaskInfoPage(
        userId: String,
        name: String?,
        enabled: Boolean?,
        pageNumber: Int,
        pageSize: Int
    ): Page<ReplicaTaskInfo> {
        val query = buildListQuery(userId, name, enabled)
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val totalRecords = mongoTemplate.count(query, TReplicaTask::class.java)
        val records = mongoTemplate.find(query.with(pageRequest), TReplicaTask::class.java)
            .map { convert(it)!! }
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    override fun buildListQuery(userId: String, name: String?, enabled: Boolean?): Query {
        val criteria = if (isAdminUser(userId)) {
            Criteria()
        } else {
            Criteria().and(TReplicaTask::createdBy.name).`is`(userId)
        }
        name?.takeIf { it.isNotBlank() }
            ?.apply { criteria.and(TReplicaTask::name.name).regex("^${PathUtils.escapeRegex(this)}") }
        enabled?.let { criteria.and(TReplicaTask::enabled.name).`is`(it) }
        return Query(criteria).with(Sort.by(Sort.Direction.DESC, TReplicaTask::createdDate.name))
    }

    override fun isAdminUser(userId: String): Boolean {
        return userResource.detail(userId).data?.admin == true
    }

    override fun interrupt(taskKey: String) {
        val task =
            taskRepository.findByKey(taskKey) ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, taskKey)
        if (!task.enabled) {
            throw ErrorCodeException(ReplicationMessageCode.TASK_ENABLED_FALSE)
        } else if (task.status == ReplicationStatus.REPLICATING) {
            task.status = ReplicationStatus.INTERRUPTED
            taskRepository.save(task)
        } else {
            throw ErrorCodeException(ReplicationMessageCode.TASK_STATUS_INVALID)
        }
    }

    override fun delete(taskKey: String) {
        val task = taskRepository.findByKey(taskKey)
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, taskKey)
        val taskLogList = taskLogRepository.findByTaskKeyOrderByStartTimeDesc(taskKey)
        taskRepository.delete(task)
        if (task.type == ReplicationType.FULL) {
            taskLogRepository.deleteByTaskKey(taskKey)
            taskLogList.forEach { taskLogDetailRepository.deleteByTaskLogKey(it.taskLogKey) }
        }
    }

    override fun toggleStatus(userId: String, taskKey: String) {
        val task = taskRepository.findByKey(taskKey)
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, taskKey)
        task.enabled = !task.enabled
        task.lastModifiedBy = userId
        task.lastModifiedDate = LocalDateTime.now()
        taskRepository.save(task)
    }

    override fun execute(taskKey: String) {
        val task =
            taskRepository.findByKey(taskKey) ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, taskKey)
        if (!task.enabled) {
            throw ErrorCodeException(ReplicationMessageCode.TASK_ENABLED_FALSE)
        } else if (task.status == ReplicationStatus.REPLICATING) {
            throw ErrorCodeException(ReplicationMessageCode.TASK_STATUS_INVALID)
        } else {
            // 如果是cronJob，则等待加载job后触发执行
            if (task.setting.executionPlan.cronExpression != null) {
                if (!replicaTaskScheduler.exist(task.id!!)) {
                    // 提示用户等待几秒, 这里如果创建任务加载进去可能会和后面扫描任务之后添加job产生冲突
                    throw ErrorCodeException(ReplicationMessageCode.SCHEDULED_JOB_LOADING, taskKey)
                }
                replicaTaskScheduler.triggerJob(JobKey.jobKey(task.id!!, DEFAULT_GROUP_ID))
            } else {
                // 如果任务不存在，并且状态不为waiting状态，则不会被reloadTask加载，将其添加进调度器
                if (task.status != ReplicationStatus.WAITING) {
                    val jobDetail = JobBuilder.newJob(ScheduledReplicaJob::class.java)
                        .withIdentity(task.id, DEFAULT_GROUP_ID)
                        .usingJobData(TASK_ID, task.id)
                        .requestRecovery()
                        .build()
                    val trigger = TriggerBuilder.newTrigger()
                        .withIdentity(task.id, DEFAULT_GROUP_ID)
                        .startNow()
                        .build()
                    replicaTaskScheduler.scheduleJob(jobDetail, trigger)
                } else {
                    // 提示用户该任务未执行
                    throw ErrorCodeException(ReplicationMessageCode.SCHEDULED_JOB_LOADING, taskKey)
                }
            }
        }
    }

    override fun canUpdated(taskKey: String): Boolean {
        val task =
            taskRepository.findByKey(taskKey) ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, taskKey)
        val taskLog = taskLogRepository.findFirstByTaskKeyOrderByStartTimeDesc(taskKey)
        return !(taskLog != null || task.status != ReplicationStatus.WAITING)
    }

    override fun update(userId: String, request: ReplicationTaskUpdateRequest): ReplicaTaskInfo {
        with(request) {
            val task =
                taskRepository.findByKey(taskKey) ?: throw ErrorCodeException(
                    CommonMessageCode.RESOURCE_NOT_FOUND,
                    taskKey
                )
            val taskLog = taskLogRepository.findFirstByTaskKeyOrderByStartTimeDesc(taskKey)
            if (taskLog != null || task.status != ReplicationStatus.WAITING) throw ErrorCodeException(
                ReplicationMessageCode.TASK_DISABLE_UPDATE,
                task.name
            )
            validate(setting, replicationInfo)
            task.name = name
            task.replicationInfo = replicationInfo
            task.setting = setting
            task.createdBy = userId
            task.lastModifiedBy = userId
            task.lastModifiedDate = LocalDateTime.now()
            task.description = description ?: task.description
            taskRepository.save(task)
            logger.info("Update replica task[$request] success.")
            return convert(task)!!
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun tryConnect(remoteClusterInfo: RemoteClusterInfo) {
        with(remoteClusterInfo) {
            try {
                val replicationService = FeignClientFactory.create(ArtifactReplicaClient::class.java, this)
                val authToken = ReplicaContext.encodeAuthToken(username, password)
                replicationService.ping(authToken)
            } catch (exception: RuntimeException) {
                val message = exception.message ?: StringPool.UNKNOWN
                logger.error("ping cluster [$name] failed, reason: $message")
                throw ErrorCodeException(ReplicationMessageCode.REMOTE_CLUSTER_CONNECT_ERROR, name)
            }
        }
    }

    override fun validate(setting: ReplicaSetting, replicationInfo: List<ReplicationInfo>) {
        if (!setting.executionPlan.executeImmediately && setting.executionPlan.executeTime == null) {
            val cronExpression = setting.executionPlan.cronExpression
                ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, ExecutionPlan::cronExpression.name)
            if (!CronExpression.isValidExpression(cronExpression)) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, cronExpression)
            }
        }
        replicationInfo.forEach { it ->
            val remoteClusterInfoList = clusterNodeService.listClusterNode(it.remoteClusterName)
            remoteClusterInfoList.forEach {
                tryConnect(it)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TaskService::class.java)

        private fun convert(task: TReplicaTask?): ReplicaTaskInfo? {
            return task?.let {
                val executionPlan = it.setting.executionPlan
                val lastRunTime =
                    if (!executionPlan.executeImmediately && executionPlan.executeTime == null && it.enabled) {
                        CronUtils.getLastTriggerTime(it.id!!, executionPlan.cronExpression!!)
                    } else null
                val nextRunTime =
                    if (!executionPlan.executeImmediately && executionPlan.executeTime == null && it.enabled) {
                        CronUtils.getNextTriggerTime(it.id!!, executionPlan.cronExpression!!)
                    } else null
                ReplicaTaskInfo(
                    id = it.id!!,
                    key = it.key,
                    name = it.name,
                    localProjectId = it.localProjectId,
                    replicationInfo = it.replicationInfo,
                    type = it.type,
                    setting = it.setting,
                    status = it.status,
                    enabled = it.enabled,

                    createdBy = it.createdBy,
                    createdDate = it.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                    lastModifiedBy = it.lastModifiedBy,
                    lastModifiedDate = it.lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME),
                    lastRunTime = lastRunTime,
                    nextRunTime = nextRunTime,
                    description = it.description
                )
            }
        }
    }
}
