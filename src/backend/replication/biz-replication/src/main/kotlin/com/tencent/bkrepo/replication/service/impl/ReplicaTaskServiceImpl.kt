/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.replication.service.impl

import com.mongodb.DuplicateKeyException
import com.tencent.bkrepo.common.api.constant.StringPool.uniqueId
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.replication.dao.ReplicaObjectDao
import com.tencent.bkrepo.replication.dao.ReplicaTaskDao
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.model.TReplicaObject
import com.tencent.bkrepo.replication.model.TReplicaTask
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeName
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.ReplicaStatus
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.pojo.task.request.ReplicaTaskCopyRequest
import com.tencent.bkrepo.replication.pojo.task.request.ReplicaTaskCreateRequest
import com.tencent.bkrepo.replication.pojo.task.request.ReplicaTaskUpdateRequest
import com.tencent.bkrepo.replication.pojo.task.request.TaskPageParam
import com.tencent.bkrepo.replication.pojo.task.setting.ExecutionStrategy
import com.tencent.bkrepo.replication.replica.type.edge.EdgePullReplicaExecutor
import com.tencent.bkrepo.replication.replica.type.schedule.ReplicaTaskScheduler
import com.tencent.bkrepo.replication.replica.type.schedule.ReplicaTaskScheduler.Companion.JOB_DATA_TASK_KEY
import com.tencent.bkrepo.replication.replica.type.schedule.ReplicaTaskScheduler.Companion.REPLICA_JOB_GROUP
import com.tencent.bkrepo.replication.replica.type.schedule.ScheduledReplicaJob
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import com.tencent.bkrepo.replication.util.CronUtils
import com.tencent.bkrepo.replication.util.TaskQueryHelper.buildListQuery
import com.tencent.bkrepo.replication.util.TaskQueryHelper.taskObjectQuery
import com.tencent.bkrepo.replication.util.TaskQueryHelper.taskQueryByType
import com.tencent.bkrepo.replication.util.TaskQueryHelper.undoScheduledTaskQuery
import org.bson.types.ObjectId
import org.quartz.JobBuilder
import org.quartz.JobKey
import org.quartz.TriggerBuilder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class ReplicaTaskServiceImpl(
    private val replicaTaskDao: ReplicaTaskDao,
    private val replicaObjectDao: ReplicaObjectDao,
    private val replicaRecordService: ReplicaRecordService,
    private val clusterNodeService: ClusterNodeService,
    private val replicaTaskScheduler: ReplicaTaskScheduler,
    private val localDataManager: LocalDataManager,
    private val edgePullReplicaExecutor: ObjectProvider<EdgePullReplicaExecutor>
) : ReplicaTaskService {
    override fun getByTaskId(taskId: String): ReplicaTaskInfo? {
        return replicaTaskDao.findById(taskId)?.let { convert(it) }
    }

    override fun getByTaskName(name: String): ReplicaTaskInfo? {
        return replicaTaskDao.findByName(name)?.let { convert(it) }
    }

    override fun getByTaskKey(key: String): ReplicaTaskInfo {
        return replicaTaskDao.findByKey(key)?.let { convert(it) }
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, key)
    }

    override fun getDetailByTaskKey(key: String): ReplicaTaskDetail {
        val taskInfo = getByTaskKey(key)
        val taskObjectList = replicaObjectDao.findByTaskKey(key).map { convert(it)!! }
        return ReplicaTaskDetail(taskInfo, taskObjectList)
    }

    override fun listTasksPage(projectId: String, param: TaskPageParam): Page<ReplicaTaskInfo> {
        with(param) {
            val direction = sortDirection?.let {
                Preconditions.checkArgument(
                    it == Sort.Direction.DESC.name || it == Sort.Direction.ASC.name,
                    TaskPageParam::sortDirection.name
                )
                Sort.Direction.valueOf(it)
            }
            val query = buildListQuery(projectId, name, lastExecutionStatus, enabled, sortType, direction)
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val totalRecords = replicaTaskDao.count(query)
            val records = replicaTaskDao.find(query.with(pageRequest)).map { convert(it)!! }
            return Pages.ofResponse(pageRequest, totalRecords, records)
        }
    }

    override fun listUndoScheduledTasks(): List<ReplicaTaskInfo> {
        val query = undoScheduledTaskQuery()
        return replicaTaskDao.find(query).map { convert(it)!! }
    }

    override fun listTasks(
        projectId: String,
        repoName: String,
        type: ReplicaType?,
        enable: Boolean?
    ): List<ReplicaTaskDetail> {
        val objectQuery = taskObjectQuery(projectId, repoName)
        val replicaObjectList = replicaObjectDao.find(objectQuery)
        if (replicaObjectList.isEmpty()) return emptyList()
        val taskKeyList = replicaObjectList.map { it.taskKey }
        val query = taskQueryByType(taskKeyList, type, enable)
        val replicaTaskInfoList = replicaTaskDao.find(query).map { convert(it)!! }
        return replicaTaskInfoList.map { info ->
            val detailList = replicaObjectList.filter { it.taskKey == info.key }.map { convert(it)!! }
            ReplicaTaskDetail(info, detailList)
        }
    }

    override fun listTaskByType(
        type: ReplicaType,
        lastId: String,
        size: Int,
        status: ReplicaStatus?
    ): List<ReplicaTaskInfo> {
        val criteria = where(TReplicaTask::replicaType).isEqualTo(type).and(TReplicaTask::id).gte(ObjectId(lastId))
            .apply { status?.let { and(TReplicaTask::status).isEqualTo(status) } }
        val query = Query(criteria).with(Sort.by(Sort.Direction.ASC, TReplicaTask::id.name)).limit(size)
        return replicaTaskDao.find(query).map { convert(it)!! }
    }

    override fun listTaskObject(key: String): List<ReplicaObjectInfo> {
        return replicaObjectDao.findByTaskKey(key).map { convert(it)!! }
    }

    override fun listRealTimeTasks(projectId: String, repoName: String): List<ReplicaTaskDetail> {
        return listTasks(projectId, repoName, ReplicaType.REAL_TIME, true)
    }

    override fun create(request: ReplicaTaskCreateRequest): ReplicaTaskInfo {
        with(request) {
            validateRequest(this)
            val key = uniqueId()
            val userId = SecurityUtils.getUserId()
            // 查询集群节点信息
            val clusterNodeSet = getClusterNodeSet(this)
            val task = TReplicaTask(
                key = key,
                name = name,
                projectId = localProjectId,
                replicaObjectType = replicaObjectType,
                replicaType = replicaType,
                setting = setting,
                remoteClusters = clusterNodeSet,
                status = when (replicaType) {
                    ReplicaType.SCHEDULED, ReplicaType.EDGE_PULL -> ReplicaStatus.WAITING
                    else -> ReplicaStatus.REPLICATING
                },
                totalBytes = computeSize(
                    localProjectId = localProjectId,
                    replicaType = replicaType,
                    replicaObjectType = replicaObjectType,
                    replicaTaskObjects = replicaTaskObjects
                ),
                description = description,
                lastExecutionStatus = when (replicaType) {
                    ReplicaType.REAL_TIME -> ExecutionStatus.RUNNING
                    else -> null
                },
                lastExecutionTime = null,
                nextExecutionTime = null,
                executionTimes = 0L,
                enabled = enabled,
                record = record,
                recordReserveDays = recordReserveDays,
                createdBy = userId,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = userId,
                lastModifiedDate = LocalDateTime.now()
            )
            // 创建replicaObject
            val replicaObjectList = replicaTaskObjects.map {
                TReplicaObject(
                    taskKey = key,
                    localProjectId = localProjectId,
                    remoteProjectId = it.remoteProjectId,
                    localRepoName = it.localRepoName,
                    remoteRepoName = it.remoteRepoName,
                    repoType = it.repoType,
                    packageConstraints = it.packageConstraints,
                    pathConstraints = it.pathConstraints,
                    sourceFilter = it.sourceFilter
                )
            }
            return try {
                replicaObjectDao.insert(replicaObjectList)
                replicaTaskDao.insert(task)
                convert(task)!!
            } catch (exception: DuplicateKeyException) {
                logger.warn("Insert task[$name] error: [${exception.message}]")
                getByTaskKey(key)
            }
        }
    }

    private fun getClusterNodeSet(request: ReplicaTaskCreateRequest): Set<ClusterNodeName> {
        val clusterIds = if (request.replicaType == ReplicaType.EDGE_PULL && request.remoteClusterIds.isEmpty()) {
            clusterNodeService.listEdgeNodes().map { it.id!! }
        } else {
            request.remoteClusterIds
        }
        return clusterIds.map {
            val clusterNodeName = clusterNodeService.getClusterNameById(it)
            // 验证连接可用
            if (request.replicaType != ReplicaType.EDGE_PULL) {
                clusterNodeService.tryConnect(clusterNodeName.name)
            }
            clusterNodeName
        }.toSet()
    }

    private fun computeSize(
        localProjectId: String,
        replicaType: ReplicaType,
        replicaObjectType: ReplicaObjectType,
        replicaTaskObjects: List<ReplicaObjectInfo>
    ): Long {
        if (replicaType != ReplicaType.RUN_ONCE) {
            return -1
        }
        return when (replicaObjectType) {
            ReplicaObjectType.PACKAGE -> computePackageSize(localProjectId, replicaTaskObjects)
            ReplicaObjectType.PATH -> computeNodeSize(localProjectId, replicaTaskObjects)
            ReplicaObjectType.REPOSITORY -> computeRepositorySize(localProjectId, replicaTaskObjects)
            else -> -1
        }
    }

    private fun computePackageSize(localProjectId: String, replicaTaskObjects: List<ReplicaObjectInfo>): Long {
        val taskObject = replicaTaskObjects.first()
        return taskObject.packageConstraints!!.sumOf {
            it.versions!!.sumOf { version ->
                localDataManager.findPackageVersion(
                    localProjectId,
                    taskObject.localRepoName,
                    it.packageKey!!,
                    version
                ).size
            }
        }
    }

    private fun computeNodeSize(localProjectId: String, replicaTaskObjects: List<ReplicaObjectInfo>): Long {
        require(replicaTaskObjects.isNotEmpty()) { "replicaTaskObjects cannot be empty" }
        
        val taskObject = replicaTaskObjects.first()
        val pathConstraints = taskObject.pathConstraints
            ?: return 0L // 如果没有路径约束，返回0
        
        return pathConstraints.sumOf { pathConstraint ->
            computePathSize(localProjectId, taskObject.localRepoName, pathConstraint.path!!)
        }
    }


    private fun computePathSize(projectId: String, repoName: String, path: String): Long {
        return try {
            val node = localDataManager.findNode(projectId, repoName, path)
            when {
                node == null -> {
                    // 节点不存在，传递的path可能是正则匹配，使用正则路径匹配计算大小
                    localDataManager.computeRegexPathNodesSize(projectId, repoName, path)
                }
                node.folder && node.size == 0L -> {
                    // 文件夹且大小为0，需要计算子节点大小
                    localDataManager.listNode(projectId, repoName, path).sumOf { it.size }
                }
                else -> {
                    // 直接返回节点大小
                    node.size
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to compute node size for [$projectId/$repoName$path]: ${e.message}", e)
            0L
        }
    }

    private fun computeRepositorySize(localProjectId: String, replicaTaskObjects: List<ReplicaObjectInfo>): Long {
        val taskObject = replicaTaskObjects.first()
        return localDataManager.getRepoMetricInfo(localProjectId, taskObject.localRepoName)
    }

    private fun validateRequest(request: ReplicaTaskCreateRequest) {
        with(request) {
            // 验证任务是否存在
            replicaTaskDao.findByName(name)?.let {
                throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, name)
            }
            Preconditions.checkNotBlank(name, this::name.name)
            Preconditions.checkNotBlank(localProjectId, this::localProjectId.name)
            Preconditions.checkNotBlank(replicaTaskObjects, this::replicaTaskObjects.name)
            if (replicaType != ReplicaType.EDGE_PULL) {
                Preconditions.checkNotBlank(remoteClusterIds, this::remoteClusterIds.name)
            }
            Preconditions.checkArgument(
                recordReserveDays in RECORD_RESERVE_DAYS_MIN..RECORD_RESERVE_DAYS_MAX,
                "recordReserveDays"
            )
            // 校验计划名称长度
            if (name.length < TASK_NAME_LENGTH_MIN || name.length > TASK_NAME_LENGTH_MAX) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, request::name.name)
            }
            // 校验同步策略，按仓库同步可以选择多个仓库，按包或者节点同步只能在单个仓库下进行操作
            validateReplicaObject(this)
            // 执行计划验证
            validateExecutionPlan(this)
        }
    }

    private fun validateExecutionPlan(request: ReplicaTaskCreateRequest) {
        with(request) {
            when (setting.executionStrategy) {
                ExecutionStrategy.IMMEDIATELY -> {
                    Preconditions.checkArgument(setting.executionPlan.executeImmediately, "executeImmediately")
                }

                ExecutionStrategy.SPECIFIED_TIME -> {
                    val executeTime = setting.executionPlan.executeTime
                    Preconditions.checkNotBlank(executeTime, "executeTime")
                    executeTime?.let {
                        Preconditions.checkArgument(it.isAfter(LocalDateTime.now()), "executeTime")
                    }
                }

                ExecutionStrategy.CRON_EXPRESSION -> {
                    val cronExpression = setting.executionPlan.cronExpression
                    Preconditions.checkNotBlank(cronExpression, "cronExpression")
                    Preconditions.checkArgument(CronUtils.isValid(cronExpression.orEmpty()), "cronExpression")
                }
            }
        }
    }

    private fun validateReplicaObject(request: ReplicaTaskCreateRequest) {
        when (request.replicaObjectType) {
            ReplicaObjectType.REPOSITORY -> {
                request.replicaTaskObjects.forEach {
                    if (!it.packageConstraints.isNullOrEmpty() || !it.pathConstraints.isNullOrEmpty()) {
                        throw ErrorCodeException(CommonMessageCode.REQUEST_CONTENT_INVALID)
                    }
                }
            }

            ReplicaObjectType.PACKAGE -> {
                if (request.replicaTaskObjects.size != 1) {
                    throw ErrorCodeException(CommonMessageCode.REQUEST_CONTENT_INVALID)
                }
                val packageConstraints = request.replicaTaskObjects.first().packageConstraints
                Preconditions.checkNotBlank(packageConstraints, "packageConstraints")
                packageConstraints?.forEach { pkg ->
                    Preconditions.checkNotBlank(pkg.packageKey, pkg::packageKey.name)
                    pkg.versions?.forEach { version -> Preconditions.checkNotBlank(version, "versions") }
                }
            }

            ReplicaObjectType.PATH -> {
                if (request.replicaTaskObjects.size != 1) {
                    throw ErrorCodeException(CommonMessageCode.REQUEST_CONTENT_INVALID)
                }
                val pathConstraints = request.replicaTaskObjects.first().pathConstraints
                Preconditions.checkNotBlank(pathConstraints, "pathConstraints")
                pathConstraints?.forEach { pathConstraint ->
                    PathUtils.normalizeFullPath(pathConstraint.path!!)
                }
            }
        }
    }

    override fun deleteByTaskKey(key: String) {
        // 删除replicaObject
        replicaObjectDao.findByTaskKey(key).forEach { replicaObjectDao.removeById(it.id!!) }
        // 删除日志
        replicaRecordService.deleteByTaskKey(key)
        // 删除任务
        replicaTaskDao.deleteByKey(key)
        logger.info("delete task [$key] success.")
    }

    override fun toggleStatus(key: String) {
        val task = replicaTaskDao.findByKey(key)
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, key)
        if (task.replicaType == ReplicaType.REAL_TIME) {
            task.status = if (task.enabled) ReplicaStatus.WAITING else ReplicaStatus.REPLICATING
        }
        task.enabled = !task.enabled
        task.lastModifiedBy = SecurityUtils.getUserId()
        task.lastModifiedDate = LocalDateTime.now()
        replicaTaskDao.save(task)
    }

    override fun copy(request: ReplicaTaskCopyRequest) {
        with(request) {
            // 校验同名任务冲突
            replicaTaskDao.findByName(name)?.let {
                throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, name)
            }
            // 获取任务
            val tReplicaTask = replicaTaskDao.findByKey(key)
                ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, key)
            // 初始化任务状态设置
            val copyKey = uniqueId()
            val userId = SecurityUtils.getUserId()
            val copiedReplicaTask = tReplicaTask.copy(
                id = null,
                key = copyKey,
                name = name,
                status = ReplicaStatus.WAITING,
                lastExecutionStatus = null,
                lastExecutionTime = null,
                nextExecutionTime = null,
                executionTimes = 0L,
                lastModifiedBy = userId,
                lastModifiedDate = LocalDateTime.now(),
                enabled = false,
                description = description
            )
            val replicaObjectList = replicaObjectDao.findByTaskKey(key)
            val copiedObjectList = replicaObjectList.map { it.copy(taskKey = copyKey, id = null) }
            try {
                replicaObjectDao.insert(copiedObjectList)
                replicaTaskDao.insert(copiedReplicaTask)
            } catch (exception: DuplicateKeyException) {
                logger.warn("copy task[$name] error: [${exception.message}]")
            }
        }
    }

    override fun update(request: ReplicaTaskUpdateRequest): ReplicaTaskInfo? {
        with(request) {
            // 获取任务
            val tReplicaTask = replicaTaskDao.findByKey(key)
                ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, key)
            // 针对ClusterNodeType 为THIRD_PARTY的更新，可以绕过下面那个任务状态限制
            val remoteNode = clusterNodeService.getByClusterId(remoteClusterIds.first())
            if (remoteNode!!.type != ClusterNodeType.REMOTE
                && tReplicaTask.replicaType != ReplicaType.RUN_ONCE
                && tReplicaTask.replicaType != ReplicaType.FEDERATION
            ) {
                // 检查任务状态，执行过的任务不让修改
                if (tReplicaTask.status != ReplicaStatus.WAITING ||
                    tReplicaTask.lastExecutionStatus != null
                ) {
                    throw ErrorCodeException(ReplicationMessageCode.TASK_DISABLE_UPDATE, key)
                }
            }
            Preconditions.checkArgument(
                recordReserveDays in RECORD_RESERVE_DAYS_MIN..RECORD_RESERVE_DAYS_MAX,
                "recordReserveDays"
            )
            // 更新任务
            val userId = SecurityUtils.getUserId()
            // 查询集群节点信息
            val clusterNodeSet = remoteClusterIds.map {
                val clusterNodeName = clusterNodeService.getClusterNameById(it)
                // 验证连接可用
                clusterNodeService.tryConnect(clusterNodeName.name)
                clusterNodeName
            }.toSet()
            val task = tReplicaTask.copy(
                id = null,
                name = name,
                replicaObjectType = replicaObjectType,
                setting = setting,
                remoteClusters = clusterNodeSet,
                status = when (tReplicaTask.replicaType) {
                    ReplicaType.SCHEDULED -> ReplicaStatus.WAITING
                    else -> ReplicaStatus.REPLICATING
                },
                replicatedBytes = 0,
                totalBytes = computeSize(
                    localProjectId = tReplicaTask.projectId,
                    replicaType = tReplicaTask.replicaType,
                    replicaObjectType = replicaObjectType,
                    replicaTaskObjects = replicaTaskObjects
                ),
                description = description,
                lastModifiedBy = userId,
                lastModifiedDate = LocalDateTime.now(),
                record = record,
                recordReserveDays = recordReserveDays
            )
            // 创建replicaObject
            val replicaObjectList = replicaTaskObjects.map {
                TReplicaObject(
                    taskKey = key,
                    localProjectId = localProjectId,
                    remoteProjectId = it.remoteProjectId,
                    localRepoName = it.localRepoName,
                    remoteRepoName = it.remoteRepoName,
                    repoType = it.repoType,
                    packageConstraints = it.packageConstraints,
                    pathConstraints = it.pathConstraints,
                    sourceFilter = it.sourceFilter
                )
            }
            return try {
                // 移除所有object对象，重新插入
                replicaObjectDao.remove(key)
                replicaObjectDao.insert(replicaObjectList)
                // 任务更新后删除旧的数据，插入新的task，保持key不变
                replicaTaskDao.deleteByKey(tReplicaTask.key)
                replicaTaskDao.insert(task)
                convert(task)
            } catch (exception: DuplicateKeyException) {
                logger.warn("update task[$name] error: [${exception.message}]")
                getByTaskKey(key)
            }
        }
    }

    override fun execute(key: String) {
        // 获取任务
        val tReplicaTask = replicaTaskDao.findByKey(key)
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, key)
        if (!tReplicaTask.enabled) {
            throw ErrorCodeException(ReplicationMessageCode.TASK_ENABLED_FALSE)
        } else if (tReplicaTask.status == ReplicaStatus.REPLICATING) {
            throw ErrorCodeException(ReplicationMessageCode.TASK_STATUS_INVALID)
        } else {
            // 如果是cronJob，则等待加载job后触发执行
            if (ExecutionStrategy.CRON_EXPRESSION == tReplicaTask.setting.executionStrategy) {
                if (!replicaTaskScheduler.exist(tReplicaTask.id!!)) {
                    // 提示用户等待几秒, 这里如果创建任务加载进去可能会和后面扫描任务之后添加job产生冲突
                    throw ErrorCodeException(ReplicationMessageCode.SCHEDULED_JOB_LOADING, key)
                }
                replicaTaskScheduler.triggerJob(JobKey.jobKey(tReplicaTask.id!!, REPLICA_JOB_GROUP))
            } else if (tReplicaTask.replicaType == ReplicaType.EDGE_PULL) {
                edgePullReplicaExecutor.ifAvailable?.pullReplica(tReplicaTask.id!!)
                    ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, TReplicaTask::replicaType.name)
            } else {
                // 如果任务不存在，并且状态不为waiting状态，则不会被reloadTask加载，将其添加进调度器
                if (tReplicaTask.status != ReplicaStatus.WAITING) {
                    val jobDetail = JobBuilder.newJob(ScheduledReplicaJob::class.java)
                        .withIdentity(tReplicaTask.id, REPLICA_JOB_GROUP)
                        .usingJobData(JOB_DATA_TASK_KEY, tReplicaTask.key)
                        .requestRecovery()
                        .build()
                    val trigger = TriggerBuilder.newTrigger()
                        .withIdentity(tReplicaTask.id, REPLICA_JOB_GROUP)
                        .startNow()
                        .build()
                    replicaTaskScheduler.scheduleJob(jobDetail, trigger)
                    tReplicaTask.status = ReplicaStatus.WAITING
                    replicaTaskDao.save(tReplicaTask)
                } else {
                    // 提示用户该任务未执行
                    throw ErrorCodeException(ReplicationMessageCode.SCHEDULED_JOB_LOADING, key)
                }
            }
        }
    }

    override fun listFederationTasks(projectId: String, repoName: String): List<ReplicaTaskDetail> {
        return listTasks(projectId, repoName, ReplicaType.FEDERATION, true)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReplicaTaskServiceImpl::class.java)
        private const val TASK_NAME_LENGTH_MIN = 2
        private const val TASK_NAME_LENGTH_MAX = 256
        private const val RECORD_RESERVE_DAYS_MIN = 1
        private const val RECORD_RESERVE_DAYS_MAX = 60

        private fun convert(tReplicaTask: TReplicaTask?): ReplicaTaskInfo? {
            return tReplicaTask?.let {
                ReplicaTaskInfo(
                    id = it.id!!,
                    key = it.key,
                    name = it.name,
                    projectId = it.projectId,
                    replicaObjectType = it.replicaObjectType,
                    replicaType = it.replicaType,
                    setting = it.setting,
                    remoteClusters = it.remoteClusters,
                    description = it.description,
                    status = it.status,
                    replicatedBytes = it.replicatedBytes,
                    totalBytes = it.totalBytes,
                    lastExecutionStatus = it.lastExecutionStatus,
                    lastExecutionTime = it.lastExecutionTime,
                    nextExecutionTime = it.nextExecutionTime,
                    executionTimes = it.executionTimes,
                    enabled = it.enabled,
                    record = it.record,
                    recordReserveDays = it.recordReserveDays,
                    createdBy = it.createdBy,
                    createdDate = it.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                    lastModifiedBy = it.lastModifiedBy,
                    lastModifiedDate = it.lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME)
                )
            }
        }

        private fun convert(tReplicaObject: TReplicaObject?): ReplicaObjectInfo? {
            return tReplicaObject?.let {
                ReplicaObjectInfo(
                    localRepoName = it.localRepoName,
                    remoteProjectId = it.remoteProjectId,
                    remoteRepoName = it.remoteRepoName,
                    repoType = it.repoType,
                    packageConstraints = it.packageConstraints,
                    pathConstraints = it.pathConstraints,
                    sourceFilter = it.sourceFilter
                )
            }
        }
    }
}
