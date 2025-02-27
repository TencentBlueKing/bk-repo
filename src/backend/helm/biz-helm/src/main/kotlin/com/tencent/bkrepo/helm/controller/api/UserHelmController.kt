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

package com.tencent.bkrepo.helm.controller.api

import com.tencent.bk.audit.annotations.ActionAuditRecord
import com.tencent.bk.audit.annotations.AuditAttribute
import com.tencent.bk.audit.annotations.AuditEntry
import com.tencent.bk.audit.annotations.AuditInstanceRecord
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.audit.ActionAuditContent
import com.tencent.bkrepo.common.artifact.audit.REPO_EDIT_ACTION
import com.tencent.bkrepo.common.artifact.audit.REPO_RESOURCE
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.metadata.pojo.log.OpLogListOption
import com.tencent.bkrepo.common.metadata.pojo.log.OperateLog
import com.tencent.bkrepo.common.metadata.service.log.OperateLogService
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.helm.api.HelmClient
import com.tencent.bkrepo.helm.pojo.HelmDomainInfo
import com.tencent.bkrepo.helm.pojo.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.pojo.artifact.HelmArtifactInfo.Companion.CHART_PACKAGE_DELETE_URL
import com.tencent.bkrepo.helm.pojo.artifact.HelmArtifactInfo.Companion.CHART_VERSION_DELETE_URL
import com.tencent.bkrepo.helm.pojo.artifact.HelmArtifactInfo.Companion.HELM_VERSION_DETAIL
import com.tencent.bkrepo.helm.pojo.artifact.HelmDeleteArtifactInfo
import com.tencent.bkrepo.helm.pojo.record.THelmRefreshLog
import com.tencent.bkrepo.helm.pojo.user.PackageVersionInfo
import com.tencent.bkrepo.helm.service.ChartManipulationService
import com.tencent.bkrepo.helm.service.ChartRepositoryService
import com.tencent.bkrepo.helm.service.HelmRefreshRecordService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@Suppress("MVCPathVariableInspection")
@Api("helm产品接口")
@RestController
@RequestMapping("/ext")
class UserHelmController(
    private val chartManipulationService: ChartManipulationService,
    private val chartRepositoryService: ChartRepositoryService,
    private val helmRefreshRecordService: HelmRefreshRecordService
) {

    @ApiOperation("查询包的版本详情")
    @GetMapping(HELM_VERSION_DETAIL)
    fun detailVersion(
        @RequestAttribute
        userId: String,
        @ArtifactPathVariable artifactInfo: HelmArtifactInfo,
        @ApiParam(value = "包唯一Key", required = true)
        @RequestParam packageKey: String,
        @ApiParam(value = "包版本", required = true)
        @RequestParam version: String
    ): Response<PackageVersionInfo> {
        return ResponseBuilder.success(chartRepositoryService.detailVersion(userId, artifactInfo, packageKey, version))
    }

    @AuditEntry(
        actionId = REPO_EDIT_ACTION
    )
    @ActionAuditRecord(
        actionId = REPO_EDIT_ACTION,
        instance = AuditInstanceRecord(
            resourceType = REPO_RESOURCE,
            instanceIds = "#artifactInfo?.repoName",
            instanceNames = "#artifactInfo?.repoName"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#artifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.NAME_TEMPLATE, value = "#packageKey")
        ],
        scopeId = "#artifactInfo?.projectId",
        content = ActionAuditContent.REPO_PACKAGE_DELETE_CONTENT
    )
    @ApiOperation("删除仓库下的包")
    @DeleteMapping(CHART_PACKAGE_DELETE_URL)
    fun deletePackage(
        @RequestAttribute userId: String,
        artifactInfo: HelmDeleteArtifactInfo,
        @ApiParam(value = "包唯一key", required = true)
        @RequestParam packageKey: String
    ): Response<Void> {
        chartManipulationService.deletePackage(userId, artifactInfo)
        return ResponseBuilder.success()
    }

    @AuditEntry(
        actionId = REPO_EDIT_ACTION
    )
    @ActionAuditRecord(
        actionId = REPO_EDIT_ACTION,
        instance = AuditInstanceRecord(
            resourceType = REPO_RESOURCE,
            instanceIds = "#artifactInfo?.repoName",
            instanceNames = "#artifactInfo?.repoName"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#artifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.NAME_TEMPLATE, value = "#packageKey"),
            AuditAttribute(name = ActionAuditContent.VERSION_TEMPLATE, value = "#version")

        ],
        scopeId = "#artifactInfo?.projectId",
        content = ActionAuditContent.REPO_PACKAGE_VERSION_DELETE_CONTENT
    )
    @ApiOperation("删除仓库下的包版本")
    @DeleteMapping(CHART_VERSION_DELETE_URL)
    fun deleteVersion(
        @RequestAttribute userId: String,
        artifactInfo: HelmDeleteArtifactInfo,
        @ApiParam(value = "包唯一key", required = true)
        @RequestParam packageKey: String,
        @ApiParam(value = "包版本", required = true)
        @RequestParam version: String
    ): Response<Void> {
        chartManipulationService.deleteVersion(userId, artifactInfo)
        return ResponseBuilder.success()
    }

    @ApiOperation("获取helm域名地址")
    @GetMapping("/address")
    fun getRegistryDomain(): Response<HelmDomainInfo> {
        return ResponseBuilder.success(chartRepositoryService.getRegistryDomain())
    }

    @ApiOperation("获取最近一次的同步时间和状态")
    @GetMapping("/getLatestSyncStatus/{projectId}/{repoName}")
    fun getLatestSyncStatus(
        @PathVariable projectId: String, @PathVariable repoName: String)
    : Response<List<THelmRefreshLog>> {
        val list = helmRefreshRecordService.getByProjectIdAndRepoName(projectId, repoName)
        return ResponseBuilder.success(list)
    }

    @ApiOperation("记录表同步配置，删除记录表中的过时数据")
    @GetMapping("/syncFreshRecord/{projectId}/{repoName}")
    fun syncConfigAndRecord(
        @PathVariable projectId: String, @PathVariable repoName: String)
            : Response<Void> {
        helmRefreshRecordService.syncRecord(projectId, repoName)
        return ResponseBuilder.success()
    }
}
