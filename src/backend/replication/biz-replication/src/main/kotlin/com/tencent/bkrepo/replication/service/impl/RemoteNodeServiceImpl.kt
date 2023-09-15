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
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter.addProtocol
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.otel.util.AsyncUtils.trace
import com.tencent.bkrepo.replication.api.ReplicaTaskOperationClient
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.request.ClusterNodeCreateRequest
import com.tencent.bkrepo.replication.pojo.cluster.request.ClusterNodeUpdateRequest
import com.tencent.bkrepo.replication.pojo.cluster.request.DetectType
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
import com.tencent.bkrepo.replication.replica.executor.RunOnceThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.type.event.EventBasedReplicaJobExecutor
import com.tencent.bkrepo.replication.replica.type.manual.ManualReplicaJobExecutor
import com.tencent.bkrepo.replication.service.ClusterNodePermissionService
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.RemoteNodeService
import com.tencent.bkrepo.replication.service.ReplicaNodeDispatchService
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import com.tencent.bkrepo.replication.util.ReplicationMetricsRecordUtil.convertToReplicationTaskMetricsRecord
import com.tencent.bkrepo.replication.util.ReplicationMetricsRecordUtil.toJson
import org.slf4j.LoggerFactory
import org.springframework.cloud.context.config.annotation.RefreshScope
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@RefreshScope
@Service
class RemoteNodeServiceImpl(
    private val clusterNodePermissionService: ClusterNodePermissionService,
    private val clusterNodeService: ClusterNodeService,
    private val localDataManager: LocalDataManager,
    private val replicaTaskService: ReplicaTaskService,
    private val replicaRecordService: ReplicaRecordService,
    private val eventBasedReplicaJobExecutor: EventBasedReplicaJobExecutor,
    private val manualReplicaJobExecutor: ManualReplicaJobExecutor,
    private val replicaNodeDispatchService: ReplicaNodeDispatchService
) : RemoteNodeService {
    private val executors = RunOnceThreadPoolExecutor.instance
    override fun remoteClusterCreate(
        projectId: String,
        repoName: String,
        requests: RemoteCreateRequest
    ): List<ClusterNodeInfo> {
        return requests.configs.map {
            validateName(it.name)
            val tClusterNode = createClusterInfo(projectId, repoName, it)
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

    // dispatch 默认为TRUE, fegin调用时为FALSE，避免feign调用时再次进行配置判断
    override fun createRunOnceTask(
        projectId: String, repoName: String, request: RemoteRunOnceTaskCreateRequest, dispatch: Boolean
    ) {
        if (dispatch && createWithRemoteClient(projectId, repoName, request)) {
            return
        }
        val repo = localDataManager.findRepoByName(projectId, repoName)
        val taskRequest = convertRemoteConfigCreateRequest(request, repo.type)
        remoteClusterCreate(projectId, repoName, RemoteCreateRequest(listOf(taskRequest)))
    }



    // dispatch 默认为TRUE, fegin调用时为FALSE，避免feign调用时再次进行配置判断
    override fun executeRunOnceTask(projectId: String, repoName: String, name: String, dispatch: Boolean) {
        val taskDetail = getTaskDetail(projectId, repoName, name)
        if (taskDetail.task.replicaType != ReplicaType.RUN_ONCE) {
            throw ErrorCodeException(CommonMessageCode.METHOD_NOT_ALLOWED, name)
        }
        if (dispatch && executeWithRemoteClient(projectId, repoName, name, taskDetail)) {
            return
        }
        executors.execute(Runnable { manualReplicaJobExecutor.execute(taskDetail) }.trace())
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

    /**
     * 根据分发任务内容创建对应的集群信息
     */
    private fun createClusterInfo(
        projectId: String,
        repoName: String,
        request: RemoteConfigCreateRequest
    ): ClusterNodeInfo {
        val realName = NAME.format(projectId, repoName, request.name)
        // clusterId为空的情况默认为remote集群
        return if (request.clusterId.isNullOrEmpty()) {
            val oldCluster = clusterNodeService.getByClusterName(realName)
            if (oldCluster == null) {
                clusterNodeService.create(
                    SecurityUtils.getUserId(), buildClusterNodeCreateRequest(projectId, repoName, request)
                )
            } else {
                val updateRequest = convertCreateToUpdate(request)
                clusterNodeService.update(
                    buildClusterNodeUpdateRequest(projectId, repoName, request.name, updateRequest)
                )
            }
        } else {
            // clusterId不为空则任务是通过集群，同构集群都是在任务创建前已经创建
            clusterNodeService.getByClusterId(request.clusterId!!)
                ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, request.clusterId!!)
        }
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
            // TODO 临时处理，在创建一次性任务时进行鉴权，后续需要修改为在执行一次性任务时进行鉴权
            clusterNodePermissionService.checkRepoPermission(
                clusterInfo, remoteUserUsername, remoteUserPassword, remoteProjectId, remoteRepoName
            )

            val repositoryDetail = localDataManager.findRepoByName(projectId, repoName)
            // 只有在同构集群内部才可以通过一次性分发任务进行仓库同步
            validateReplicaObject(request, clusterInfo)
            val replicaObjectType = getReplicaObjectType(request)
            val replicaTaskObjects = buildReplicaTaskObjects(
                repoName, repositoryDetail.type, replicaObjectType, request
            )
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

    /**
     * 校验分发内容
     */
    private fun validateReplicaObject(
        request: RemoteConfigCreateRequest,
        clusterInfo: ClusterNodeInfo
    ) {
        with(request) {
            // 兼容历史数据，replicaObjectType为空的情况
            if (pathConstraints.isNullOrEmpty() && packageConstraints.isNullOrEmpty() &&
                clusterInfo.type == ClusterNodeType.REMOTE) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "Package or path")
            }

            if (pathConstraints.isNullOrEmpty() && packageConstraints.isNullOrEmpty() &&
                replicaObjectType != ReplicaObjectType.REPOSITORY) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "Package or path")
            }
        }
    }


    /**
     * 获取对应分发任务的内容类型
     */
    private fun getReplicaObjectType(request: RemoteConfigCreateRequest): ReplicaObjectType {
        with(request) {
            if (replicaObjectType != null) return replicaObjectType!!
            return if (replicaType == ReplicaType.RUN_ONCE) {
                if (!pathConstraints.isNullOrEmpty()) {
                    ReplicaObjectType.PATH
                } else {
                    ReplicaObjectType.PACKAGE
                }
            } else {
                ReplicaObjectType.REPOSITORY
            }
        }
    }

    private fun buildReplicaTaskObjects(
        repoName: String,
        repoType: RepositoryType,
        replicaObjectType: ReplicaObjectType,
        request: RemoteConfigCreateRequest
    ): List<ReplicaObjectInfo> {
        with(request) {
            val taskObjects = mutableListOf<ReplicaObjectInfo>()
            val (packageConstraints, pathConstraints) = if (replicaObjectType == ReplicaObjectType.REPOSITORY) {
                Pair(null, null)
            } else {
                Pair(packageConstraints, pathConstraints)
            }
            taskObjects.add(ReplicaObjectInfo(
                localRepoName = repoName,
                repoType = repoType,
                remoteProjectId = remoteProjectId,
                remoteRepoName = remoteRepoName,
                packageConstraints = packageConstraints,
                pathConstraints = pathConstraints
            ))
            return taskObjects
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

    private fun getTaskDetail(
        projectId: String,
        repoName: String,
        name: String
    ): ReplicaTaskDetail {
        val taskInfo = getReplicaTaskInfo(projectId, repoName, name)
        return replicaTaskService.getDetailByTaskKey(taskInfo.key)
    }



    private fun createWithRemoteClient(
        projectId: String, repoName: String,
        request: RemoteRunOnceTaskCreateRequest
    ): Boolean {
        val host = if (!request.clusterId.isNullOrEmpty()) {
            val clusterInfo = clusterNodeService.getByClusterId(request.clusterId!!)
                ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, request.clusterId!!)
            clusterInfo.url
        } else {
            addProtocol(request.registry!!).toString()
        }
        buildExecuteClientWithHost(host)?.let {
            try{
                it.createRunOnceTask(projectId, repoName, request)
                return true
            } catch (e: Exception) {
                // fegin连不上时需要降级为本地执行
                logger.warn("Cloud not run task on remote node, will run with current node")
            }
        }
        return false
    }

    private fun buildExecuteClientWithHost(host: String) : ReplicaTaskOperationClient? {
        return replicaNodeDispatchService.findReplicaClientByHost(
            host, ReplicaTaskOperationClient::class.java
        )
    }


    /**
     * 执行成功返回true，fegin连不上时需要降级为本地执行，返回false
     */
    private fun executeWithRemoteClient(
        projectId: String, repoName: String, name: String,
        taskDetail: ReplicaTaskDetail
    ): Boolean {
        buildExecuteClient(taskDetail)?.let {
            try{
                it.executeRunOnceTask(projectId, repoName, name)
                return true
            } catch (e: Exception) {
                // fegin连不上时需要降级为本地执行
                logger.warn("Cloud not run task on remote node, will run with current node")
            }
        }
        return false
    }


    private fun buildExecuteClient(
        taskDetail: ReplicaTaskDetail
    ) : ReplicaTaskOperationClient? {
        return replicaNodeDispatchService.findReplicaClient(
                taskDetail, ReplicaTaskOperationClient::class.java
            )
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
                type = ClusterNodeType.REMOTE,
                detectType = DetectType.PING
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
                remoteUserUsername = remoteUserUsername,
                remoteUserPassword = remoteUserPassword,
                remoteProjectId = remoteProjectId,
                remoteRepoName = remoteRepoName,
                replicaObjectType = replicaObjectType,
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
                remoteUserUsername = remoteUserUsername,
                remoteUserPassword = remoteUserPassword,
                packageConstraints = packageConstraints,
                replicaObjectType = replicaObjectType,
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
                remoteUserUsername = remoteUserUsername,
                remoteUserPassword = remoteUserPassword,
                remoteProjectId = remoteProjectId,
                remoteRepoName = remoteRepoName,
                replicaObjectType = replicaObjectType,
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
