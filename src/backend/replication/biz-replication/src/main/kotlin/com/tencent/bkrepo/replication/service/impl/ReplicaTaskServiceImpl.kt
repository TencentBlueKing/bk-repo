package com.tencent.bkrepo.replication.service.impl

import com.mongodb.DuplicateKeyException
import com.tencent.bkrepo.common.api.constant.StringPool.uniqueId
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.replication.dao.ReplicaObjectDao
import com.tencent.bkrepo.replication.dao.ReplicaTaskDao
import com.tencent.bkrepo.replication.message.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TReplicaObject
import com.tencent.bkrepo.replication.model.TReplicaTask
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeName
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.pojo.task.ReplicationStatus
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.pojo.task.request.ReplicaTaskCreateRequest
import com.tencent.bkrepo.replication.pojo.task.request.TaskPageParam
import com.tencent.bkrepo.replication.pojo.task.setting.ExecutionPlan
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import com.tencent.bkrepo.replication.util.TaskQueryHelper.buildListQuery
import com.tencent.bkrepo.replication.util.TaskQueryHelper.undoTaskQuery
import org.quartz.CronExpression
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ReplicaTaskServiceImpl(
    private val replicaTaskDao: ReplicaTaskDao,
    private val replicaObjectDao: ReplicaObjectDao,
    private val replicaRecordService: ReplicaRecordService,
    private val clusterNodeService: ClusterNodeService
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
            val query = buildListQuery(name, lastExecutionStatus, enabled)
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
            validateParameter(this)
            val key = uniqueId()
            // 查询集群节点信息
            val clusterNodeSet = remoteClusterIds.map {
                convert(clusterNodeService.getByClusterId(it))
                    ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_EXISTS, it)
            }.toSet()
            val task = TReplicaTask(
                key = key,
                name = name,
                projectId = projectId,
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
                createdBy = operator,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = operator,
                lastModifiedDate = LocalDateTime.now()
            )
            // 创建replicaObject
            val replicaObjectList = repoInfo.map {
                TReplicaObject(
                    taskKey = key,
                    localProjectId = projectId,
                    localRepoName = it.repoName,
                    remoteProjectId = remoteProjectId,
                    remoteRepoName = it.remoteRepoName,
                    repoType = it.repoType,
                    packageConstraints = packageConstraints?.toList(),
                    pathConstraints = pathConstraints?.toList()
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

    private fun validateParameter(request: ReplicaTaskCreateRequest) {
        with(request) {
            Preconditions.checkNotBlank(name, this::name.name)
            Preconditions.checkNotBlank(projectId, this::projectId.name)
            Preconditions.checkNotBlank(remoteProjectId, this::remoteProjectId.name)
            Preconditions.checkNotBlank(repoInfo, this::repoInfo.name)
            Preconditions.checkNotBlank(remoteClusterIds, this::remoteClusterIds.name)
            // 校验计划名称长度
            if (name.length < TASK_NAME_LENGTH_MIN || name.length > TASK_NAME_LENGTH_MAX) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, request::name.name)
            }
            // 校验调度策略
            if (!setting.executionPlan.executeImmediately && setting.executionPlan.executeTime == null) {
                val cronExpression = setting.executionPlan.cronExpression
                    ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, ExecutionPlan::cronExpression.name)
                if (!CronExpression.isValidExpression(cronExpression)) {
                    throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, cronExpression)
                }
            }
            // 校验同步策略，按仓库同步可以选择多个仓库，按包或者节点同步只能在某个仓库下进行操作
            if (repoInfo.size > 1 && (!packageConstraints.isNullOrEmpty() || !packageConstraints.isNullOrEmpty())) {
                throw ErrorCodeException(CommonMessageCode.REQUEST_CONTENT_INVALID)
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
                    replicaType = it.replicaType,
                    setting = it.setting,
                    remoteClusters = it.remoteClusters,
                    description = it.description,
                    lastExecutionStatus = it.lastExecutionStatus,
                    lastExecutionTime = it.lastExecutionTime,
                    nextExecutionTime = it.nextExecutionTime,
                    executionTimes = it.executionTimes,
                    enabled = it.enabled
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

        private fun convert(nodeInfo: ClusterNodeInfo?): ClusterNodeName? {
            return nodeInfo?.let {
                ClusterNodeName(
                    id = it.id,
                    name = it.name
                )
            }
        }
    }
}
