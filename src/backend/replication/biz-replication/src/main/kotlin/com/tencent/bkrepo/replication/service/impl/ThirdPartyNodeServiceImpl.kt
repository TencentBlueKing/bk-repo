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
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeType
import com.tencent.bkrepo.replication.pojo.cluster.request.ClusterNodeCreateRequest
import com.tencent.bkrepo.replication.pojo.cluster.request.ClusterNodeUpdateRequest
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.pojo.task.request.ReplicaTaskCreateRequest
import com.tencent.bkrepo.replication.pojo.task.request.ReplicaTaskUpdateRequest
import com.tencent.bkrepo.replication.pojo.thirdparty.ThirdPartyInfo
import com.tencent.bkrepo.replication.pojo.thirdparty.request.ThirdPartyConfigCreateRequest
import com.tencent.bkrepo.replication.pojo.thirdparty.request.ThirdPartyConfigUpdateRequest
import com.tencent.bkrepo.replication.pojo.thirdparty.request.ThirdPartyCreateRequest
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import com.tencent.bkrepo.replication.service.ThirdPartyNodeService
import com.tencent.bkrepo.replication.util.HttpUtils.addProtocol
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ThirdPartyNodeServiceImpl(
    private val clusterNodeService: ClusterNodeService,
    private val localDataManager: LocalDataManager,
    private val replicaTaskService: ReplicaTaskService
) : ThirdPartyNodeService {

    override fun thirdPartyCreate(
        projectId: String,
        repoName: String,
        requests: ThirdPartyCreateRequest
    ): List<ClusterNodeInfo> {
        return requests.configs.map {
            val clusterInfo = clusterNodeService.create(SecurityUtils.getUserId(), buildClusterNodeCreateRequest(it))
            createTask(
                projectId = projectId,
                repoName = repoName,
                request = it,
                clusterInfo = clusterInfo
            )
            clusterInfo
        }
    }

    override fun thirdPartyUpdate(
        projectId: String,
        repoName: String,
        name: String,
        request: ThirdPartyConfigUpdateRequest
    ) {
        val clusterInfo = clusterNodeService.update(buildClusterNodeUpdateRequest(request, name))
        val task = replicaTaskService.getByTaskName(NAME.format(projectId, repoName, name))
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, name)
        updateTask(
            request = request,
            task = task,
            clusterInfo = clusterInfo,
            projectId = projectId,
            repoName = repoName
        )
    }

    override fun getByName(projectId: String, repoName: String, name: String?): List<ThirdPartyInfo> {
        localDataManager.findRepoByName(projectId, repoName)
        val replicaTaskDetails = if (name.isNullOrBlank()) {
            replicaTaskService.listTasks(projectId, repoName)
        } else {
            clusterNodeService.getByClusterName(name) ?: return emptyList()
            val task = replicaTaskService.getByTaskName(NAME.format(projectId, repoName, name)) ?: return emptyList()
            listOf(replicaTaskService.getDetailByTaskKey(task.key))
        }
        val result = mutableListOf<ThirdPartyInfo>()
        replicaTaskDetails.forEach { it ->
            getThirdPartyInfoByName(
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
        val clusterInfo = clusterNodeService.getByClusterName(name)
            ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_EXISTS, name)
        clusterNodeService.deleteById(clusterInfo.id)
        val task = replicaTaskService.getByTaskName(NAME.format(projectId, repoName, name))
            ?: throw ErrorCodeException(ReplicationMessageCode.REPLICA_TASK_NOT_FOUND, name)
        replicaTaskService.deleteByTaskKey(task.key)
    }

    /**
     * 当第三方集群创建后，创建对应的任务
     */
    private fun createTask(
        projectId: String,
        repoName: String,
        request: ThirdPartyConfigCreateRequest,
        clusterInfo: ClusterNodeInfo
    ): ReplicaTaskInfo {
        with(request) {
            val repositoryDetail = localDataManager.findRepoByName(projectId, repoName)
            val replicaTaskObjects = listOf(
                ReplicaObjectInfo(
                    localRepoName = repoName,
                    repoType = repositoryDetail.type,
                    remoteProjectId = null,
                    remoteRepoName = null,
                    packageConstraints = packageConstraints,
                    pathConstraints = pathConstraints
                )
            )
            val taskCreateRequest = ReplicaTaskCreateRequest(
                name = NAME.format(projectId, repoName, name),
                localProjectId = projectId,
                replicaObjectType = ReplicaObjectType.REPOSITORY,
                replicaTaskObjects = replicaTaskObjects,
                replicaType = replicaType,
                setting = setting,
                remoteClusterIds = setOf(clusterInfo.id),
                description = description,
                enabled = enable
            )
            return replicaTaskService.create(taskCreateRequest)
        }
    }

    /**
     * 更新task
     */
    private fun updateTask(
        request: ThirdPartyConfigUpdateRequest,
        task: ReplicaTaskInfo,
        clusterInfo: ClusterNodeInfo,
        projectId: String,
        repoName: String
    ) {
        with(request) {
            val repositoryDetail = localDataManager.findRepoByName(projectId, repoName)
            val replicaTaskObjects = listOf(
                ReplicaObjectInfo(
                    localRepoName = repoName,
                    repoType = repositoryDetail.type,
                    remoteProjectId = null,
                    remoteRepoName = null,
                    packageConstraints = packageConstraints,
                    pathConstraints = pathConstraints
                )
            )
            val taskUpdaterequest = ReplicaTaskUpdateRequest(
                key = task.key,
                name = task.name,
                localProjectId = projectId,
                replicaTaskObjects = replicaTaskObjects,
                replicaObjectType = ReplicaObjectType.REPOSITORY,
                setting = setting,
                remoteClusterIds = setOf(clusterInfo.id),
                description = description,
            )
            replicaTaskService.update(taskUpdaterequest)
        }
    }

    private fun getThirdPartyInfoByName(
        projectId: String,
        repoName: String,
        taskDetail: ReplicaTaskDetail
    ): ThirdPartyInfo? {
        with(taskDetail) {
            val clusterInfo = clusterNodeService.getByClusterId(task.remoteClusters.first().id) ?: return null
            if (clusterInfo.type != ClusterNodeType.THIRD_PARTY) return null
            return buildThirdPartyInfo(
                projectId = projectId,
                repoName = repoName,
                replicaTaskDetail = taskDetail,
                clusterNodeInfo = clusterInfo
            )
        }
    }

    private fun buildClusterNodeCreateRequest(request: ThirdPartyConfigCreateRequest): ClusterNodeCreateRequest {
        with(request) {
            return ClusterNodeCreateRequest(
                name = name,
                url = addProtocol(registry).toString(),
                certificate = certificate,
                username = username,
                password = password,
                type = ClusterNodeType.THIRD_PARTY
            )
        }
    }

    private fun buildThirdPartyInfo(
        projectId: String,
        repoName: String,
        clusterNodeInfo: ClusterNodeInfo,
        replicaTaskDetail: ReplicaTaskDetail
    ): ThirdPartyInfo {
        return ThirdPartyInfo(
            projectId = projectId,
            repoName = repoName,
            name = clusterNodeInfo.name,
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
        request: ThirdPartyConfigUpdateRequest,
        name: String
    ): ClusterNodeUpdateRequest {
        with(request) {
            return ClusterNodeUpdateRequest(
                name = name,
                url = addProtocol(registry).toString(),
                certificate = certificate,
                username = username,
                password = password,
                type = ClusterNodeType.THIRD_PARTY
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ThirdPartyNodeServiceImpl::class.java)
        const val NAME = "%s-%s-%s"
    }
}
