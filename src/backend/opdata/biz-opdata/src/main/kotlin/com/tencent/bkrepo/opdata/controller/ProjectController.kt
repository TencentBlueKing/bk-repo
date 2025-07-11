/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.opdata.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.metadata.pojo.project.ProjectUsageStatistics
import com.tencent.bkrepo.common.metadata.pojo.project.ProjectUsageStatisticsListOption
import com.tencent.bkrepo.common.metadata.service.project.ProjectUsageStatisticsService
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.opdata.model.TProjectMetrics
import com.tencent.bkrepo.opdata.pojo.ProjectBillStatementRequest
import com.tencent.bkrepo.opdata.pojo.ProjectMetrics
import com.tencent.bkrepo.opdata.pojo.ProjectMetricsOption
import com.tencent.bkrepo.opdata.pojo.ProjectMetricsRequest
import com.tencent.bkrepo.opdata.service.ProjectMetricsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/project/metrics")
class ProjectController(
    private val projectMetricsService: ProjectMetricsService,
    private val projectUsageStatisticsService: ProjectUsageStatisticsService,
    private val permissionManager: PermissionManager,
) {

    /**
     * 获取项目的统计数据
     */
    @GetMapping("/list")
    @Principal(PrincipalType.ADMIN)
    fun getProjectMetrics(
        option: ProjectMetricsOption
    ): Response<Page<TProjectMetrics>> {
        return ResponseBuilder.success(projectMetricsService.page(option))
    }

    @GetMapping("/list/usage")
    fun getUsageStatistics(
        options: ProjectUsageStatisticsListOption
    ): Response<Page<ProjectUsageStatistics>> {
        if (options.projectId == null) {
            permissionManager.checkPrincipal(SecurityUtils.getUserId(), PrincipalType.ADMIN)
        } else {
            permissionManager.checkProjectPermission(PermissionAction.MANAGE, options.projectId!!)
        }
        return ResponseBuilder.success(projectUsageStatisticsService.page(options))
    }

    /**
     * 获取项目的统计数据
     */
    @GetMapping("/list/project/capSize")
    @Principal(PrincipalType.ADMIN)
    fun getProjectCapSizeMetrics(
        metricsRequest: ProjectMetricsRequest
    ): Response<List<ProjectMetrics>> {
        return ResponseBuilder.success(projectMetricsService.list(metricsRequest))
    }

    /**
     * 获取项目的统计数据
     */
    @GetMapping("/list/project/capSize/download")
    @Principal(PrincipalType.ADMIN)
    fun downloadProjectCapSizeMetrics(metricsRequest: ProjectMetricsRequest) {
        projectMetricsService.download(metricsRequest)
    }

    /**
     * 获取项目账单明细
     */
    @GetMapping("/bill/statement/download")
    @Principal(PrincipalType.ADMIN)
    fun downloadProjectBillStatement(billStatementRequest: ProjectBillStatementRequest) {
        projectMetricsService.downloadBillStatement(billStatementRequest)
    }

    /**
     * 获取活跃项目列表
     */
    @GetMapping("/list/activeProjects")
    @Principal(PrincipalType.ADMIN)
    fun getActiveProjects(): Response<MutableSet<String>> {
        return ResponseBuilder.success(projectMetricsService.getActiveProjects())
    }

    /**
     * 获取下载活跃项目列表
     */
    @GetMapping("/list/downloadActiveProjects")
    @Principal(PrincipalType.ADMIN)
    fun getDownloadActiveProjects(): Response<MutableSet<String>> {
        return ResponseBuilder.success(projectMetricsService.getDownloadActiveProjects())
    }

    /**
     * 获取上传活跃项目列表
     */
    @GetMapping("/list/uploadActiveProjects")
    @Principal(PrincipalType.ADMIN)
    fun getUploadActiveProjects(): Response<MutableSet<String>> {
        return ResponseBuilder.success(projectMetricsService.getUploadActiveProjects())
    }
}
