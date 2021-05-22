package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.replication.dao.ReplicaObjectDao
import com.tencent.bkrepo.replication.dao.ReplicaTaskDao
import com.tencent.bkrepo.replication.message.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TReplicaObject
import com.tencent.bkrepo.replication.model.TReplicaTask
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.pojo.task.`object`.ReplicaObjectInfo
import com.tencent.bkrepo.replication.pojo.task.request.ReplicaTaskCreateRequest
import com.tencent.bkrepo.replication.pojo.task.request.TaskPageParam
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import com.tencent.bkrepo.replication.util.TaskQueryHelper.buildListQuery
import com.tencent.bkrepo.replication.util.TaskQueryHelper.undoTaskQuery
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ReplicaTaskServiceImpl(
    private val replicaTaskDao: ReplicaTaskDao,
    private val replicaObjectDao: ReplicaObjectDao,
    private val replicaRecordService: ReplicaRecordService
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
        TODO("Not yet implemented")
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
    }
}
