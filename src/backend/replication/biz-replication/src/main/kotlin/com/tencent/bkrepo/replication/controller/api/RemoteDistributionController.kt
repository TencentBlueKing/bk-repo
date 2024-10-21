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

package com.tencent.bkrepo.replication.controller.api

import com.tencent.bk.audit.annotations.ActionAuditRecord
import com.tencent.bk.audit.annotations.AuditAttribute
import com.tencent.bk.audit.annotations.AuditEntry
import com.tencent.bk.audit.annotations.AuditInstanceRecord
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.audit.ActionAuditContent
import com.tencent.bkrepo.common.artifact.audit.REPO_EDIT_ACTION
import com.tencent.bkrepo.common.artifact.audit.REPO_RESOURCE
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordInfo
import com.tencent.bkrepo.replication.pojo.remote.RemoteInfo
import com.tencent.bkrepo.replication.pojo.remote.request.RemoteConfigUpdateRequest
import com.tencent.bkrepo.replication.pojo.remote.request.RemoteCreateRequest
import com.tencent.bkrepo.replication.pojo.remote.request.RemoteRunOnceTaskCreateRequest
import com.tencent.bkrepo.replication.service.RemoteNodeService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Api("远端分发集群用户配置接口")
@RestController
@RequestMapping("/api/remote/distribution")
class RemoteDistributionController(
    private val remoteNodeService: RemoteNodeService
) {

    @ApiOperation("创建远端集群节点")
    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @PostMapping("/create/{projectId}/{repoName}")
    fun remoteClusterCreate(
        @ApiParam(value = "仓库ID")
        @PathVariable projectId: String,
        @ApiParam(value = "项目ID")
        @PathVariable repoName: String,
        @RequestBody requests: RemoteCreateRequest
    ): Response<List<ClusterNodeInfo>> {
        remoteNodeService.remoteClusterCreate(projectId, repoName, requests)
        return ResponseBuilder.success()
    }

    @ApiOperation("创建远端集群节点")
    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @PostMapping("/update/{projectId}/{repoName}/{name}")
    fun remoteClusterUpdate(
        @ApiParam(value = "仓库ID")
        @PathVariable projectId: String,
        @ApiParam(value = "项目ID")
        @PathVariable repoName: String,
        @PathVariable name: String,
        @RequestBody request: RemoteConfigUpdateRequest
    ): Response<Void> {
        remoteNodeService.remoteClusterUpdate(
            projectId = projectId,
            repoName = repoName,
            name = name,
            request = request
        )
        return ResponseBuilder.success()
    }

    @ApiOperation("根据name以及关联项目仓库信息查询远端集群详情")
    @Permission(ResourceType.REPO, PermissionAction.READ)
    @GetMapping("/info/{projectId}/{repoName}/{name}", "/info/{projectId}/{repoName}")
    fun getByName(
        @ApiParam(value = "仓库ID")
        @PathVariable projectId: String,
        @ApiParam(value = "项目ID")
        @PathVariable repoName: String,
        @PathVariable(required = false) name: String? = null
    ): Response<List<RemoteInfo>> {
        return ResponseBuilder.success(remoteNodeService.getByName(projectId, repoName, name))
    }

    @ApiOperation("任务启停状态切换")
    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @PostMapping("/toggle/status/{projectId}/{repoName}/{name}")
    fun toggleStatus(
        @ApiParam(value = "仓库ID")
        @PathVariable projectId: String,
        @ApiParam(value = "项目ID")
        @PathVariable repoName: String,
        @PathVariable name: String
    ): Response<Void> {
        remoteNodeService.toggleStatus(projectId, repoName, name)
        return ResponseBuilder.success()
    }

    @ApiOperation("根据name以及关联项目仓库信息删除远端集群详情")
    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @DeleteMapping("/delete/{projectId}/{repoName}/{name}")
    fun deleteClusterNode(
        @ApiParam(value = "仓库ID")
        @PathVariable projectId: String,
        @ApiParam(value = "项目ID")
        @PathVariable repoName: String,
        @PathVariable name: String
    ): Response<Void> {
        remoteNodeService.deleteByName(projectId, repoName, name)
        return ResponseBuilder.success()
    }

    /**
     * 手动调用同步指定版本的制品(主要用于内部补偿使用，针对非一次性任务，不对外)
     */
    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @PostMapping("/push/{projectId}/{repoName}/{name}")
    fun pushSpecialArtifact(
        @ApiParam(value = "仓库ID")
        @PathVariable projectId: String,
        @ApiParam(value = "项目ID")
        @PathVariable repoName: String,
        @RequestParam packageName: String,
        @RequestParam version: String,
        @PathVariable name: String,
    ): Response<Void> {
        remoteNodeService.pushSpecialArtifact(projectId, repoName, packageName, version, name)
        return ResponseBuilder.success()
    }


    /**
     * 创建一次性分发任务
     */
    @AuditEntry(
        actionId = REPO_EDIT_ACTION
    )
    @ActionAuditRecord(
        actionId = REPO_EDIT_ACTION,
        instance = AuditInstanceRecord(
            resourceType = REPO_RESOURCE,
            instanceIds = "#repoName",
            instanceNames = "#repoName"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#repoName"),
        ],
        scopeId = "#projectId",
        content = ActionAuditContent.REPO_REPLICATION_CREATE_CONTENT
    )
    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @PostMapping("/create/runOnceTask/{projectId}/{repoName}")
    fun createRunOnceTask(
        @ApiParam(value = "仓库ID")
        @PathVariable projectId: String,
        @ApiParam(value = "项目ID")
        @PathVariable repoName: String,
        @RequestBody requests: RemoteRunOnceTaskCreateRequest
    ): Response<Void> {
        remoteNodeService.createRunOnceTask(projectId, repoName, requests)
        return ResponseBuilder.success()
    }


    /**
     * 手动调用一次性执行任务
     */
    @AuditEntry(
        actionId = REPO_EDIT_ACTION
    )
    @ActionAuditRecord(
        actionId = REPO_EDIT_ACTION,
        instance = AuditInstanceRecord(
            resourceType = REPO_RESOURCE,
            instanceIds = "#repoName",
            instanceNames = "#repoName"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#repoName"),
            AuditAttribute(name = ActionAuditContent.NAME_TEMPLATE, value = "#name"),
        ],
        scopeId = "#projectId",
        content = ActionAuditContent.REPO_REPLICATION_EXECUTE_CONTENT
    )
    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @PostMapping("/execute/runOnceTask/{projectId}/{repoName}")
    fun executeRunOnceTask(
        @ApiParam(value = "仓库ID")
        @PathVariable projectId: String,
        @ApiParam(value = "项目ID")
        @PathVariable repoName: String,
        @RequestParam name: String,
    ): Response<Void> {
        remoteNodeService.executeRunOnceTask(projectId, repoName, name)
        return ResponseBuilder.success()
    }

    /**
     * 查询一次性任务的执行结果
     */
    @Permission(ResourceType.REPO, PermissionAction.READ)
    @GetMapping("/get/runOnceTaskStatus/{projectId}/{repoName}")
    fun getRunOnceTaskResult(
        @ApiParam(value = "仓库ID")
        @PathVariable projectId: String,
        @ApiParam(value = "项目ID")
        @PathVariable repoName: String,
        @RequestParam name: String,
    ): Response<ReplicaRecordInfo?> {
        return ResponseBuilder.success(remoteNodeService.getRunOnceTaskResult(projectId, repoName, name))
    }

    /**
     * 删除已执行完成的一次性任务
     */
    @Permission(ResourceType.REPO, PermissionAction.DELETE)
    @DeleteMapping("/delete/runOnceTask/{projectId}/{repoName}")
    fun deleteRunOnceTaskResult(
        @ApiParam(value = "仓库ID")
        @PathVariable projectId: String,
        @ApiParam(value = "项目ID")
        @PathVariable repoName: String,
        @RequestParam name: String,
    ): Response<Void> {
        remoteNodeService.deleteRunOnceTask(projectId, repoName, name)
        return ResponseBuilder.success()
    }
}
