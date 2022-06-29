/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.controller.service

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.event.packages.VersionCreatedEvent
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.api.ExternalClusterClient
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.request.ClusterNodeCreateRequest
import com.tencent.bkrepo.replication.pojo.cluster.request.ClusterNodeUpdateRequest
import com.tencent.bkrepo.replication.pojo.cluster.request.ExternalClusterNodeCreateRequest
import com.tencent.bkrepo.replication.pojo.cluster.request.ExternalClusterNodeUpdateRequest
import com.tencent.bkrepo.replication.pojo.deploy.request.PackageDeployRequest
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.pojo.task.request.ReplicaTaskCreateRequest
import com.tencent.bkrepo.replication.pojo.task.setting.ReplicaSetting
import com.tencent.bkrepo.replication.replica.event.EventBasedReplicaJobExecutor
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.RestController

/**
 * 外部集群相关功能
 */
@RestController
class ExternalClusterController(
    private val clusterNodeService: ClusterNodeService,
    private val replicaTaskService: ReplicaTaskService,
    private val eventBasedReplicaJobExecutor: EventBasedReplicaJobExecutor
) : ExternalClusterClient {
    /**
     * 创建外部集群信息
     */
    override fun createExternalCluster(request: ExternalClusterNodeCreateRequest): Response<Void> {
        val clusterInfo = clusterNodeService.create(SecurityUtils.getUserId(), buildClusterNodeCreateRequest(request))
        createTask(request, clusterInfo)
        return ResponseBuilder.success()
    }

    /**
     * 更新外部集群信息
     */
    override fun updateExternalCluster(request: ExternalClusterNodeUpdateRequest): Response<Void> {
        // TODO 更新需要考虑多种情况
//        val clusterInfo = clusterNodeService.update(SecurityUtils.getUserId(), buildClusterNodeUpdateRequest(request))
        return ResponseBuilder.success()
    }

    /**
     * 当外部集群创建后，创建对应的任务
     */
    fun createTask(request: ExternalClusterNodeCreateRequest, clusterInfo: ClusterNodeInfo): ReplicaTaskInfo {
        with(request) {
            val replicaTaskObjects = listOf(
                ReplicaObjectInfo(
                    localRepoName = repoName!!,
                    repoType = repositoryType!!,
                    remoteProjectId = null,
                    remoteRepoName = null,
                    packageConstraints = null,
                    pathConstraints = null
                )
            )
            val taskRequest = ReplicaTaskCreateRequest(
                name = name,
                localProjectId = projectId!!,
                replicaObjectType = ReplicaObjectType.REPOSITORY,
                replicaTaskObjects = replicaTaskObjects,
                replicaType = ReplicaType.REAL_TIME,
                setting = ReplicaSetting(),
                remoteClusterIds = setOf(clusterInfo.id),
                enabled = enable
            )
            return replicaTaskService.create(taskRequest)
        }
    }

    /**
     * 当外部集群更新后，更新对应的任务
     */
    fun updateTask(request: ExternalClusterNodeUpdateRequest, clusterInfo: ClusterNodeInfo) {
        // TODO 更新动作
    }

    /**
     * 推送对应package到配置的外部仓库
     */
    override fun deployPackage(request: PackageDeployRequest): Response<Void> {
        with(request) {
            val event = VersionCreatedEvent(
                projectId = projectId,
                repoName = repoName,
                packageKey = packageKey,
                packageVersion = packageVersion,
                userId = SecurityUtils.getUserId(),
                packageType = packageType,
                packageName = packageName,
                realIpAddress = null
            )
            // TODO 针对代理仓库创建时，会去拉取对应的package信息，针对这类消息需要过滤
            replicaTaskService.listRealTimeTasks(event.projectId, event.repoName).forEach {
                eventBasedReplicaJobExecutor.execute(it, event)
            }
            return ResponseBuilder.success()
        }
    }

    private fun buildClusterNodeCreateRequest(request: ExternalClusterNodeCreateRequest): ClusterNodeCreateRequest {
        with(request) {
            return ClusterNodeCreateRequest(
                name = name,
                url = url,
                certificate = certificate,
                username = username,
                password = password,
                type = type,
                extension = extension
            )
        }
    }

    private fun buildClusterNodeUpdateRequest(request: ExternalClusterNodeUpdateRequest): ClusterNodeUpdateRequest {
        with(request) {
            return ClusterNodeUpdateRequest(
                name = name,
                url = url,
                certificate = certificate,
                username = username,
                password = password,
                type = type,
                extension = extension
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ExternalClusterController::class.java)
        const val task_name = "external_%s"
    }
}
