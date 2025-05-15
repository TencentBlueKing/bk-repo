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

package com.tencent.bkrepo.analyst.controller.user

import com.tencent.bkrepo.analyst.pojo.request.ArtifactVulnerabilityRequest
import com.tencent.bkrepo.analyst.pojo.request.FileScanResultDetailRequest
import com.tencent.bkrepo.analyst.pojo.request.FileScanResultOverviewRequest
import com.tencent.bkrepo.analyst.pojo.request.scancodetoolkit.ArtifactLicensesDetailRequest
import com.tencent.bkrepo.analyst.pojo.response.ArtifactVulnerabilityInfo
import com.tencent.bkrepo.analyst.pojo.response.FileLicensesResultDetail
import com.tencent.bkrepo.analyst.pojo.response.FileLicensesResultOverview
import com.tencent.bkrepo.analyst.pojo.response.FileScanResultDetail
import com.tencent.bkrepo.analyst.pojo.response.FileScanResultOverview
import com.tencent.bkrepo.analyst.pojo.response.SubtaskResultOverview
import com.tencent.bkrepo.analyst.service.ScanTaskService
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.analysis.pojo.scanner.utils.normalizedLevel
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "扫描报告")
@RestController
@RequestMapping("/api/scan")
class UserScanReportController(private val scanTaskService: ScanTaskService) {

    @Operation(summary = "获取文件扫描报告详情")
    @Permission(type = ResourceType.NODE, action = PermissionAction.READ)
    @PostMapping("/reports/detail${DefaultArtifactInfo.DEFAULT_MAPPING_URI}")
    fun artifactReport(
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @RequestBody request: FileScanResultDetailRequest
    ): Response<FileScanResultDetail> {
        request.artifactInfo = artifactInfo
        return ResponseBuilder.success(scanTaskService.resultDetail(request))
    }

    @Operation(summary = "制品详情--漏洞数据")
    @GetMapping("/reports/{subScanTaskId}")
    fun artifactReport(
        @Parameter(name = "扫描记录id") @PathVariable subScanTaskId: String,
        request: ArtifactVulnerabilityRequest
    ): Response<Page<ArtifactVulnerabilityInfo>> {
        request.subScanTaskId = subScanTaskId
        request.leakType = request.leakType?.let { normalizedLevel(it) }
        return ResponseBuilder.success(scanTaskService.archiveSubtaskResultDetail(request))
    }

    @Operation(summary = "文件扫描结果预览")
    @Principal(PrincipalType.ADMIN)
    @PostMapping("/reports/overview")
    fun artifactReports(
        @RequestBody request: FileScanResultOverviewRequest
    ): Response<List<FileScanResultOverview>> {
        return ResponseBuilder.success(scanTaskService.resultOverview(request))
    }

    @Operation(summary = "制品详情--漏洞数据")
    @GetMapping("/artifact/leak/{projectId}/{subScanTaskId}")
    fun artifactLeak(
        @Parameter(name = "projectId") @PathVariable projectId: String,
        @Parameter(name = "扫描记录id") @PathVariable subScanTaskId: String,
        request: ArtifactVulnerabilityRequest
    ): Response<Page<ArtifactVulnerabilityInfo>> {
        request.projectId = projectId
        request.subScanTaskId = subScanTaskId
        request.leakType = request.leakType?.let { normalizedLevel(it) }
        return ResponseBuilder.success(scanTaskService.resultDetail(request))
    }

    @Operation(summary = "制品详情--漏洞数据--导出")
    @GetMapping("/export/artifact/leak/{projectId}/{subScanTaskId}")
    fun exportLeak(
        @Parameter(name = "projectId") @PathVariable projectId: String,
        @Parameter(name = "扫描记录id") @PathVariable subScanTaskId: String,
        request: ArtifactVulnerabilityRequest
    ) {
        request.projectId = projectId
        request.subScanTaskId = subScanTaskId
        request.leakType = request.leakType?.let { normalizedLevel(it) }
        scanTaskService.exportLeakDetail(request)
    }

    @Operation(summary = "制品详情--漏洞数据")
    @GetMapping("/artifact/count/{projectId}/{subScanTaskId}")
    fun artifactCount(
        @Parameter(name = "projectId") @PathVariable projectId: String,
        @Parameter(name = "扫描记录id") @PathVariable subScanTaskId: String
    ): Response<SubtaskResultOverview> {
        return ResponseBuilder.success(scanTaskService.planArtifactSubtaskOverview(subScanTaskId))
    }

    @Operation(summary = "制品详情--许可数据")
    @GetMapping("/artifact/license/leak/{projectId}/{subScanTaskId}")
    fun artifactLicenseLeak(
        @Parameter(name = "projectId", required = true) @PathVariable projectId: String,
        @Parameter(name = "扫描记录id", required = true) @PathVariable subScanTaskId: String,
        request: ArtifactLicensesDetailRequest
    ): Response<Page<FileLicensesResultDetail>> {
        request.projectId = projectId
        request.subScanTaskId = subScanTaskId
        return ResponseBuilder.success(scanTaskService.resultDetail(request))
    }

    @Operation(summary = "制品详情--许可数据")
    @GetMapping("/artifact/license/count/{projectId}/{subScanTaskId}")
    fun artifactLicenseCount(
        @Parameter(name = "projectId", required = true) @PathVariable projectId: String,
        @Parameter(name = "扫描记录id", required = true) @PathVariable subScanTaskId: String
    ): Response<FileLicensesResultOverview> {
        return ResponseBuilder.success(scanTaskService.planLicensesArtifact(projectId, subScanTaskId))
    }

    @Operation(summary = "制品详情--许可数据")
    @GetMapping("/license/reports/{subScanTaskId}")
    fun artifactLicenseReport(
        @Parameter(name = "扫描记录id") @PathVariable subScanTaskId: String,
        request: ArtifactLicensesDetailRequest
    ): Response<Page<FileLicensesResultDetail>> {
        request.subScanTaskId = subScanTaskId
        return ResponseBuilder.success(scanTaskService.archiveSubtaskResultDetail(request))
    }
}
