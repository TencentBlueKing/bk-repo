package com.tencent.bkrepo.replication.service.impl

import com.mongodb.DuplicateKeyException
import com.tencent.bkrepo.common.api.constant.StringPool.uniqueId
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.replication.dao.ReplicaObjectDao
import com.tencent.bkrepo.replication.dao.ReplicaTaskDao
import com.tencent.bkrepo.replication.message.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TReplicaObject
import com.tencent.bkrepo.replication.model.TReplicaTask
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordInfo
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.pojo.task.ReplicationStatus
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.pojo.task.request.ReplicaTaskCreateRequest
import com.tencent.bkrepo.replication.pojo.task.request.TaskPageParam
import com.tencent.bkrepo.replication.repository.TaskRepository
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import com.tencent.bkrepo.replication.util.CronUtils
import com.tencent.bkrepo.replication.util.TaskQueryHelper.buildListQuery
import com.tencent.bkrepo.replication.util.TaskQueryHelper.undoTaskQuery
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class ReplicaTaskServiceImpl(
    private val replicaTaskDao: ReplicaTaskDao,
    private val replicaObjectDao: ReplicaObjectDao,
    private val replicaRecordService: ReplicaRecordService,
    private val clusterNodeService: ClusterNodeService,
    private val taskRepository: TaskRepository
) : ReplicaTaskService {
    override fun getByTaskKey(key: String): ReplicaTaskInfo {
        return replicaTaskDao.findByKey(key)?.let { convert(it)!! }
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, key)
    }

    override fun getDetailByTaskKey(key: String): ReplicaTaskDetail {
        val taskInfo = getByTaskKey(key)
        val taskObjectList = replicaObjectDao.findByTaskKey(key).map { convert(it)!! }
        return ReplicaTaskDetail(taskInfo, taskObjectList)
    }

    override fun listTasksPage(param: TaskPageParam): Page<ReplicaTaskInfo> {
        with(param) {
            val query = buildListQuery(name, lastExecutionStatus, enabled, sortType)
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val totalRecords = replicaTaskDao.count(query)
            val records = replicaTaskDao.find(query.with(pageRequest)).map { convert(it)!! }
            return Pages.ofResponse(pageRequest, totalRecords, records)
        }
    }

    override fun listUndoScheduledTasks(): List<ReplicaTaskInfo> {
        val query = undoTaskQuery()
        return replicaTaskDao.find(query).map { convert(it)!! }
    }

    override fun create(request: ReplicaTaskCreateRequest) {
        with(request) {
            validateRequest(this)
            val key = uniqueId()
            val userId = SecurityUtils.getUserId()
            // 查询集群节点信息
            val clusterNodeSet = remoteClusterIds.map {
                val clusterNodeName = clusterNodeService.getClusterNameById(it)
                // 验证连接可用
                clusterNodeService.tryConnect(clusterNodeName.name)
                clusterNodeName
            }.toSet()
            val task = TReplicaTask(
                key = key,
                name = name,
                projectId = localProjectId,
                replicaObjectType = replicaObjectType,
                replicaType = replicaType,
                setting = setting,
                remoteClusters = clusterNodeSet,
                status = ReplicationStatus.WAITING,
                description = description,
                lastExecutionStatus = null,
                lastExecutionTime = null,
                nextExecutionTime = null,
                executionTimes = 0L,
                enabled = enabled,
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
                    pathConstraints = it.pathConstraints
                )
            }
            try {
                replicaObjectDao.insert(replicaObjectList)
                replicaTaskDao.insert(task)
            } catch (exception: DuplicateKeyException) {
                logger.warn("Insert task[$name] error: [${exception.message}]")
            }
        }
    }

    private fun validateRequest(request: ReplicaTaskCreateRequest) {
        with(request) {
            // 验证任务是否存在
            taskRepository.findByName(name)?.let {
                throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, name)
            }
            Preconditions.checkNotBlank(name, this::name.name)
            Preconditions.checkNotBlank(localProjectId, this::name.name)
            Preconditions.checkNotBlank(replicaTaskObjects, this::replicaTaskObjects.name)
            Preconditions.checkNotBlank(remoteClusterIds, this::remoteClusterIds.name)
            // 校验计划名称长度
            if (name.length < TASK_NAME_LENGTH_MIN || name.length > TASK_NAME_LENGTH_MAX) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, request::name.name)
            }
            // 暂时只支持SCHEDULED,实时同步暂不支持
            Preconditions.checkArgument(replicaType == ReplicaType.SCHEDULED, this::replicaType.name)
            // 校验同步策略，按仓库同步可以选择多个仓库，按包或者节点同步只能在单个仓库下进行操作
            validateReplicaObject(this)
            // 执行计划验证
            validateExecutionPlan(this)
        }
    }

    private fun validateExecutionPlan(request: ReplicaTaskCreateRequest) {
        with(request) {
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
                    PathUtils.normalizeFullPath(pathConstraint.path)
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
        val task = taskRepository.findByKey(key)
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, key)
        task.enabled = !task.enabled
        task.lastModifiedBy = SecurityUtils.getUserId()
        task.lastModifiedDate = LocalDateTime.now()
        taskRepository.save(task)
    }

    override fun startNewRecord(key: String): ReplicaRecordInfo {
        val initialRecord = replicaRecordService.initialRecord(key)
        val tReplicaTask = replicaTaskDao.findByKey(key)
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, key)
        tReplicaTask.lastExecutionTime = LocalDateTime.now()
        if (isCronJob(tReplicaTask)) {
            tReplicaTask.nextExecutionTime =
                CronUtils.getNextTriggerTime(key, tReplicaTask.setting.executionPlan.cronExpression!!)
        }
        tReplicaTask.lastExecutionStatus = ExecutionStatus.RUNNING
        taskRepository.save(tReplicaTask)
        return initialRecord
    }

    private fun isCronJob(tReplicaTask: TReplicaTask): Boolean {
        return !tReplicaTask.setting.executionPlan.cronExpression.isNullOrBlank()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReplicaTaskServiceImpl::class.java)
        private const val TASK_NAME_LENGTH_MIN = 2
        private const val TASK_NAME_LENGTH_MAX = 32

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
                    lastExecutionStatus = it.lastExecutionStatus,
                    lastExecutionTime = it.lastExecutionTime,
                    nextExecutionTime = it.nextExecutionTime,
                    executionTimes = it.executionTimes,
                    enabled = it.enabled,
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
                    pathConstraints = it.pathConstraints
                )
            }
        }
    }
}
