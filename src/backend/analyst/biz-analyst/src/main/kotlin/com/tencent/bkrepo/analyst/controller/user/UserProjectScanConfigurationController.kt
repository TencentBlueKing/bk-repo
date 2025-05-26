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

import com.tencent.bkrepo.analyst.pojo.ProjectScanConfiguration
import com.tencent.bkrepo.analyst.pojo.request.ProjectScanConfigurationPageRequest
import com.tencent.bkrepo.analyst.service.ProjectScanConfigurationService
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.annotation.LogOperate
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "项目扫描配置接口")
@RestController
@RequestMapping("/api/scan/configurations")
@Principal(PrincipalType.ADMIN)
class UserProjectScanConfigurationController(
    private val projectScanConfigurationService: ProjectScanConfigurationService
) {

    @Operation(summary = "创建项目扫描配置")
    @PostMapping
    @LogOperate(type = "PROJECT_SCAN_CONFIG_CREATE")
    fun create(@RequestBody request: ProjectScanConfiguration): Response<ProjectScanConfiguration> {
        val configuration = projectScanConfigurationService.create(request)
        return ResponseBuilder.success(configuration)
    }

    @Operation(summary = "删除项目扫描配置")
    @DeleteMapping("/{projectId}")
    @LogOperate(type = "PROJECT_SCAN_CONFIG_DELETE")
    fun delete(@PathVariable projectId: String): Response<Void> {
        projectScanConfigurationService.delete(projectId)
        return ResponseBuilder.success()
    }

    @Operation(summary = "更新项目扫描配置")
    @PutMapping
    @LogOperate(type = "PROJECT_SCAN_CONFIG_UPDATE")
    fun update(@RequestBody request: ProjectScanConfiguration): Response<ProjectScanConfiguration> {
        val configuration = projectScanConfigurationService.update(request)
        return ResponseBuilder.success(configuration)
    }

    @Operation(summary = "分页获取项目扫描配置")
    @GetMapping
    @LogOperate(type = "PROJECT_SCAN_CONFIG_LIST")
    fun page(request: ProjectScanConfigurationPageRequest): Response<Page<ProjectScanConfiguration>> {
        val page = projectScanConfigurationService.page(request)
        return ResponseBuilder.success(page)
    }

    @Operation(summary = "获取项目扫描配置")
    @GetMapping("/{projectId}")
    fun get(@PathVariable projectId: String): Response<ProjectScanConfiguration> {
        return ResponseBuilder.success(projectScanConfigurationService.get(projectId))
    }
}
