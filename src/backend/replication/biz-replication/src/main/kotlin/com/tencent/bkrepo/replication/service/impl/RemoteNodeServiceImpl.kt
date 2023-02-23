/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.artifact.event.packages.VersionCreatedEvent
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.request.ClusterNodeCreateRequest
import com.tencent.bkrepo.replication.pojo.cluster.request.ClusterNodeUpdateRequest
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordInfo
import com.tencent.bkrepo.replication.pojo.remote.RemoteInfo
import com.tencent.bkrepo.replication.pojo.remote.request.RemoteConfigCreateRequest
import com.tencent.bkrepo.replication.pojo.remote.request.RemoteConfigUpdateRequest
import com.tencent.bkrepo.replication.pojo.remote.request.RemoteCreateRequest
import com.tencent.bkrepo.replication.pojo.remote.request.RemoteRunOnceTaskCreateRequest
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.ReplicaStatus
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.pojo.task.objects.PackageConstraint
import com.tencent.bkrepo.replication.pojo.task.objects.PathConstraint
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.pojo.task.request.ReplicaTaskCreateRequest
import com.tencent.bkrepo.replication.pojo.task.request.ReplicaTaskUpdateRequest
import com.tencent.bkrepo.replication.pojo.task.setting.ConflictStrategy
import com.tencent.bkrepo.replication.replica.base.executor.RunOnceThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.event.EventBasedReplicaJobExecutor
import com.tencent.bkrepo.replication.replica.manual.ManualReplicaJobExecutor
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.RemoteNodeService
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import com.tencent.bkrepo.replication.util.HttpUtils.addProtocol
import com.tencent.bkrepo.replication.util.ReplicationMetricsRecordUtil.convertToReplicationTaskMetricsRecord
import com.tencent.bkrepo.replication.util.ReplicationMetricsRecordUtil.toJson
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@Service
class RemoteNodeServiceImpl(
    private val clusterNodeService: ClusterNodeService,
    private val localDataManager: LocalDataManager,
    private val replicaTaskService: ReplicaTaskService,
    private val replicaRecordService: ReplicaRecordService,
    private val eventBasedReplicaJobExecutor: EventBasedReplicaJobExecutor,
    private val manualReplicaJobExecutor: ManualReplicaJobExecutor
) : RemoteNodeService {
    private val executors = RunOnceThreadPoolExecutor.instance
    override fun remoteClusterCreate(
        projectId: String,
        repoName: String,
        requests: RemoteCreateRequest
    ): List<ClusterNodeInfo> {
        return requests.configs.map {
            validateName(it.name)
            val realName = NAME.format(projectId, repoName, it.name)
            val tClusterNode = if (it.clusterId.isNullOrEmpty()) {
                val oldCluster = clusterNodeService.getByClusterName(realName)
                if (oldCluster == null) {
                    clusterNodeService.create(
                        SecurityUtils.getUserId(), buildClusterNodeCreateRequest(projectId, repoName, it)
                    )
                } else {
                    val updateRequest = convertCreateToUpdate(it)
                    clusterNodeService.update(
                        buildClusterNodeUpdateRequest(projectId, repoName, it.name, updateRequest)
                    )
                }
            } else {
                clusterNodeService.getByClusterId(it.clusterId!!)
                    ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, it.clusterId!!)
            }

            createOrUpdateTask(
                projectId = projectId,
                repoName = repoName,
                request = it,
                clusterInfo = tClusterNode
            )
            tClusterNode
        }
    }

    override fun remoteClusterUpdate(
        projectId: String,
        repoName: String,
        name: String,
        request: RemoteConfigUpdateRequest
    ) {
        with(request) {
            val clusterInfo = if (clusterId.isNullOrEmpty()) {
                clusterNodeService.update(buildClusterNodeUpdateRequest(projectId, repoName, name, request))
            } else {
                clusterNodeService.getByClusterId(clusterId!!)
                    ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, clusterId!!)
            }
            replicaTaskService.getByTaskName(NAME.format(projectId, repoName, name))
                ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, name)
            createOrUpdateTask(
                request = convertUpdateToCreate(name, request),
                clusterInfo = clusterInfo,
                projectId = projectId,
                repoName = repoName
            )
        }
    }

    override fun getByName(projectId: String, repoName: String, name: String?): List<RemoteInfo> {
        localDataManager.findRepoByName(projectId, repoName)
        val replicaTaskDetails = if (name.isNullOrBlank()) {
            replicaTaskService.listTasks(
                projectId = projectId,
                repoName = repoName,
                enable = null
            )
        } else {
            val realName = NAME.format(projectId, repoName, name)
            clusterNodeService.getByClusterName(realName) ?: return emptyList()
            val task = replicaTaskService.getByTaskName(realName) ?: return emptyList()
            listOf(replicaTaskService.getDetailByTaskKey(task.key))
        }
        val result = mutableListOf<RemoteInfo>()
        replicaTaskDetails.forEach { it ->
            getRemoteInfoByName(
                projectId = projectId,
                repoName = repoName,
                taskDetail = it
            )?.let {
                result.add(it)
            }
        }
        return result
    }

    override fun toggleStatus(projectId: String, repoName: String, name: String) {
        localDataManager.findRepoByName(projectId, repoName)
        val task = replicaTaskService.getByTaskName(NAME.format(projectId, repoName, name))
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, name)
        replicaTaskService.toggleStatus(task.key)
    }

    override fun deleteByName(projectId: String, repoName: String, name: String) {
        localDataManager.findRepoByName(projectId, repoName)
        val realName = NAME.format(projectId, repoName, name)
        clusterNodeService.getByClusterName(realName)?.let {
            clusterNodeService.deleteById(it.id!!)
        }
        val task = replicaTaskService.getByTaskName(realName)
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, name)
        replicaTaskService.deleteByTaskKey(task.key)
    }

    override fun pushSpecialArtifact(
        projectId: String,
        repoName: String,
        packageName: String,
        version: String,
        name: String
    ) {
        val repositoryDetail = localDataManager.findRepoByName(projectId, repoName)
        val taskDetail = getTaskDetail(projectId, repoName, name)
        val event = VersionCreatedEvent(
            projectId = projectId,
            repoName = repoName,
            packageKey = PackageKeys.ofName(repositoryDetail.type.name.toLowerCase(), packageName),
            packageVersion = version,
            userId = SecurityUtils.getUserId(),
            packageType = repositoryDetail.type.name,
            packageName = packageName,
            realIpAddress = null
        )
        eventBasedReplicaJobExecutor.execute(taskDetail, event)
    }

    override fun createRunOnceTask(projectId: String, repoName: String, request: RemoteRunOnceTaskCreateRequest) {
        val repo = localDataManager.findRepoByName(projectId, repoName)
        val taskRequest = convertRemoteConfigCreateRequest(request, repo.type)
        remoteClusterCreate(projectId, repoName, RemoteCreateRequest(listOf(taskRequest)))
    }

    override fun executeRunOnceTask(projectId: String, repoName: String, name: String) {
        val taskDetail = getTaskDetail(projectId, repoName, name)
        if (taskDetail.task.replicaType != ReplicaType.RUN_ONCE) {
            throw ErrorCodeException(CommonMessageCode.METHOD_NOT_ALLOWED, name)
        }
        executors.execute { manualReplicaJobExecutor.execute(taskDetail) }
    }

    override fun getRunOnceTaskResult(projectId: String, repoName: String, name: String): ReplicaRecordInfo? {
        val taskInfo = getReplicaTaskInfo(projectId, repoName, name)
        val record = replicaRecordService.findLatestRecord(taskInfo.key)
        record?.let {
            record.replicatedBytes = taskInfo.replicatedBytes
            record.totalBytes = taskInfo.totalBytes
            if (record.endTime == null) return record.copy(status = ExecutionStatus.RUNNING)
            val taskCreateTime = LocalDateTime.parse(taskInfo.lastModifiedDate, DateTimeFormatter.ISO_DATE_TIME)
            if (record.endTime!!.isBefore(taskCreateTime)) return record.copy(status = ExecutionStatus.RUNNING)
        }
        return record
    }

    override fun deleteRunOnceTask(projectId: String, repoName: String, name: String) {
        val taskName = NAME.format(projectId, repoName, name)
        deleteByTaskName(taskName)
    }

    override fun deleteRunOnceTaskByTaskName(taskName: String) {
        deleteByTaskName(taskName)
    }

    private fun deleteByTaskName(taskName: String) {
        logger.info("Task $taskName will be deleted!")
        replicaTaskService.getByTaskName(taskName)?.let {
            if (it.status!! != ReplicaStatus.COMPLETED && it.replicaType != ReplicaType.RUN_ONCE) {
                logger.warn("The name $taskName of runonce task is still running")
                throw ErrorCodeException(CommonMessageCode.REQUEST_DENIED, taskName)
            }
            clusterNodeService.getByClusterName(taskName)?.let { node ->
                clusterNodeService.deleteById(node.id!!)
            }
            replicaTaskService.deleteByTaskKey(it.key)
        }
    }

    private fun getTaskDetail(
        projectId: String,
        repoName: String,
        name: String
    ): ReplicaTaskDetail {
        val taskInfo = getReplicaTaskInfo(projectId, repoName, name)
        return replicaTaskService.getDetailByTaskKey(taskInfo.key)
    }

    private fun getReplicaTaskInfo(
        projectId: String,
        repoName: String,
        name: String
    ): ReplicaTaskInfo {
        val realName = NAME.format(projectId, repoName, name)
        return replicaTaskService.getByTaskName(realName)
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, name)
    }

    /**
     * 当远端集群创建后，创建/更新对应的任务
     */
    private fun createOrUpdateTask(
        projectId: String,
        repoName: String,
        request: RemoteConfigCreateRequest,
        clusterInfo: ClusterNodeInfo
    ): ReplicaTaskInfo {
        with(request) {
            val repositoryDetail = localDataManager.findRepoByName(projectId, repoName)
            if (pathConstraints.isNullOrEmpty() && packageConstraints.isNullOrEmpty()
                && replicaType == ReplicaType.RUN_ONCE) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "Package or path")
            }
            val replicaTaskObjects = listOf(
                ReplicaObjectInfo(
                    localRepoName = repoName,
                    repoType = repositoryDetail.type,
                    remoteProjectId = remoteProjectId,
                    remoteRepoName = remoteRepoName,
                    packageConstraints = packageConstraints,
                    pathConstraints = pathConstraints
                )
            )
            val replicaObjectType = if (replicaType == ReplicaType.RUN_ONCE) {
                if (!pathConstraints.isNullOrEmpty()) {
                    ReplicaObjectType.PATH
                } else {
                    ReplicaObjectType.PACKAGE
                }
            } else {
                ReplicaObjectType.REPOSITORY
            }
            var task = replicaTaskService.getByTaskName(NAME.format(projectId, repoName, name))
            if (task == null) {
                val taskCreateRequest = ReplicaTaskCreateRequest(
                    name = NAME.format(projectId, repoName, name),
                    localProjectId = projectId,
                    replicaObjectType = replicaObjectType,
                    replicaTaskObjects = replicaTaskObjects,
                    replicaType = replicaType,
                    setting = setting,
                    remoteClusterIds = setOf(clusterInfo.id!!),
                    description = description,
                    enabled = enable
                )
                task = replicaTaskService.create(taskCreateRequest)
            } else {
                val taskUpdateRequest = ReplicaTaskUpdateRequest(
                    key = task.key,
                    name = task.name,
                    localProjectId = projectId,
                    replicaTaskObjects = replicaTaskObjects,
                    replicaObjectType = replicaObjectType,
                    setting = setting,
                    remoteClusterIds = setOf(clusterInfo.id!!),
                    description = description,
                )
                replicaTaskService.update(taskUpdateRequest)?.let {
                    task = it.copy()
                }
            }
            logger.info(
                toJson(
                    convertToReplicationTaskMetricsRecord(
                        projectId = projectId,
                        repoName = repoName,
                        repoType = repositoryDetail.type.name,
                        request = request,
                        replicaTaskInfo = task!!
                    )
                )
            )
            return task!!
        }
    }

    private fun getRemoteInfoByName(
        projectId: String,
        repoName: String,
        taskDetail: ReplicaTaskDetail
    ): RemoteInfo? {
        with(taskDetail) {
            val clusterInfo = clusterNodeService.getByClusterId(task.remoteClusters.first().id) ?: return null
            if (clusterInfo.type != ClusterNodeType.REMOTE) return null
            return buildRemoteInfo(
                projectId = projectId,
                repoName = repoName,
                replicaTaskDetail = taskDetail,
                clusterNodeInfo = clusterInfo
            )
        }
    }

    private fun buildClusterNodeCreateRequest(
        projectId: String,
        repoName: String,
        request: RemoteConfigCreateRequest
    ): ClusterNodeCreateRequest {
        with(request) {
            validateParameter(registry, RemoteConfigCreateRequest::registry.name)
            return ClusterNodeCreateRequest(
                name = NAME.format(projectId, repoName, name),
                url = addProtocol(registry!!).toString(),
                certificate = certificate,
                username = username,
                password = password,
                type = ClusterNodeType.REMOTE
            )
        }
    }

    private fun buildRemoteInfo(
        projectId: String,
        repoName: String,
        clusterNodeInfo: ClusterNodeInfo,
        replicaTaskDetail: ReplicaTaskDetail
    ): RemoteInfo {
        val name = clusterNodeInfo.name.split("/").last()
        return RemoteInfo(
            projectId = projectId,
            repoName = repoName,
            name = name,
            registry = clusterNodeInfo.url,
            certificate = clusterNodeInfo.certificate,
            username = clusterNodeInfo.username,
            password = clusterNodeInfo.password,
            packageConstraints = replicaTaskDetail.objects[0].packageConstraints,
            pathConstraints = replicaTaskDetail.objects[0].pathConstraints,
            replicaType = replicaTaskDetail.task.replicaType,
            setting = replicaTaskDetail.task.setting,
            description = replicaTaskDetail.task.description,
            enable = replicaTaskDetail.task.enabled
        )
    }

    private fun buildClusterNodeUpdateRequest(
        projectId: String,
        repoName: String,
        name: String,
        request: RemoteConfigUpdateRequest
    ): ClusterNodeUpdateRequest {
        with(request) {
            validateParameter(registry, RemoteConfigCreateRequest::registry.name)
            return ClusterNodeUpdateRequest(
                name = NAME.format(projectId, repoName, name),
                url = addProtocol(registry!!).toString(),
                certificate = certificate,
                username = username,
                password = password,
                type = ClusterNodeType.REMOTE
            )
        }
    }

    private fun convertCreateToUpdate(request: RemoteConfigCreateRequest): RemoteConfigUpdateRequest {
        with(request) {
            return RemoteConfigUpdateRequest(
                clusterId = clusterId,
                registry = registry,
                certificate = certificate,
                username = username,
                password = password,
                remoteProjectId = remoteProjectId,
                remoteRepoName = remoteRepoName,
                packageConstraints = packageConstraints,
                pathConstraints = pathConstraints,
                replicaType = replicaType,
                setting = setting,
                description = description,
                enable = enable
            )
        }
    }

    private fun convertUpdateToCreate(name: String, request: RemoteConfigUpdateRequest): RemoteConfigCreateRequest {
        with(request) {
            return RemoteConfigCreateRequest(
                name = name,
                registry = registry,
                certificate = certificate,
                username = username,
                password = password,
                packageConstraints = packageConstraints,
                pathConstraints = pathConstraints,
                replicaType = replicaType,
                setting = setting,
                description = description,
                enable = enable
            )
        }
    }

    private fun convertRemoteConfigCreateRequest(
        request: RemoteRunOnceTaskCreateRequest,
        repoType: RepositoryType
    ): RemoteConfigCreateRequest {
        with(request) {
            val packageConstraints = createPackageConstraint(request, repoType)
            val pathConstraints = createPathConstraint(request)
            return RemoteConfigCreateRequest(
                name = name,
                clusterId = clusterId,
                registry = registry,
                username = username,
                password = password,
                remoteProjectId = remoteProjectId,
                remoteRepoName = remoteRepoName,
                packageConstraints = packageConstraints,
                pathConstraints = pathConstraints,
                replicaType = replicaType,
                setting = setting.copy(conflictStrategy = ConflictStrategy.OVERWRITE),
                description = description,
                enable = enable
            )
        }
    }

    private fun createPackageConstraint(
        request: RemoteRunOnceTaskCreateRequest,
        repoType: RepositoryType
    ): List<PackageConstraint>? {
        with(request) {
            if (packageName.isNullOrEmpty()) return null
            val packageKey = PackageKeys.ofName(repoType, packageName!!)
            validateParameter(versions, RemoteRunOnceTaskCreateRequest::versions.name)
            val targetVersion = if (clusterId.isNullOrEmpty()) {
                // 针对异构集群，推送目标版本,只有当包版本数量为1时才可以设置，仅针对镜像类型
                // 当包版本为多个时，只能以原有版本分发到远端，不能改变版本号进行分发
                if (versions!!.size == 1) {
                    targetVersions
                } else {
                    null
                }
            } else {
                // 当为同构集群时，不支持将源版本包推送为不同版本包到目标仓库
                versions
            }
            return listOf(
                PackageConstraint(
                    packageKey = packageKey,
                    versions = versions,
                    targetVersions = targetVersion
                )
            )
        }
    }

    private fun createPathConstraint(
        request: RemoteRunOnceTaskCreateRequest
    ): List<PathConstraint>? {
        if (request.pathConstraints.isNullOrEmpty()) return null
        return request.pathConstraints.orEmpty().map { PathConstraint(path = it) }
    }

    private fun validateName(name: String) {
        if (!Pattern.matches(REMOTE_NAME_PATTERN, name)) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, name)
        }
        if (name.isBlank() ||
            name.length < REMOTE_NAME_LENGTH_MIN ||
            name.length > REMOTE_NAME_LENGTH_MAX
        ) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, name)
        }
    }

    private fun validateParameter(param: String?, paramName: String) {
        if (param.isNullOrEmpty()) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, paramName)
        }
    }
    private fun validateParameter(param: List<String>?, paramName: String) {
        if (param.isNullOrEmpty()) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, paramName)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RemoteNodeServiceImpl::class.java)
        const val NAME = "%s/%s/%s"
        private const val REMOTE_NAME_PATTERN = "[a-zA-Z_][a-zA-Z0-9\\-_]{1,127}"
        private const val REMOTE_NAME_LENGTH_MIN = 2
        private const val REMOTE_NAME_LENGTH_MAX = 128
    }
}
