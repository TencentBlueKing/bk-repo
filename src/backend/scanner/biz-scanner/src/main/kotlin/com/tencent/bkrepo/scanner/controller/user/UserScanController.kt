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

package com.tencent.bkrepo.scanner.controller.user

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.scanner.pojo.ArtifactScanReport
import com.tencent.bkrepo.scanner.pojo.ArtifactScanStatus
import com.tencent.bkrepo.scanner.pojo.request.ScanRequest
import com.tencent.bkrepo.scanner.pojo.ScanTask
import com.tencent.bkrepo.scanner.pojo.ScanTriggerType
import com.tencent.bkrepo.scanner.service.ScanService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Api("扫描接口")
@RestController
@RequestMapping("/api/scan")
class UserScanController @Autowired constructor(
    private val scanService: ScanService
) {
    @ApiOperation("创建扫描任务")
    @Principal(PrincipalType.ADMIN)
    @PostMapping
    fun scan(@RequestBody scanRequest: ScanRequest): Response<ScanTask> {
        return ResponseBuilder.success(scanService.scan(scanRequest, ScanTriggerType.MANUAL))
    }

    @ApiOperation("获取扫描任务信息")
    @GetMapping("/tasks/{taskId}")
    @Principal(PrincipalType.ADMIN)
    fun task(@PathVariable("taskId") taskId: String): Response<ScanTask> {
        return ResponseBuilder.success(scanService.task(taskId))
    }

    @ApiOperation("获取文件扫描报告")
    @Permission(type = ResourceType.NODE, action = PermissionAction.READ)
    @GetMapping("/reports${DefaultArtifactInfo.DEFAULT_MAPPING_URI}")
    fun artifactReport(@ArtifactPathVariable artifactInfo: ArtifactInfo): Response<ArtifactScanReport> {
        TODO()
    }

    @ApiOperation("获取文件扫描状态")
    @Permission(type = ResourceType.NODE, action = PermissionAction.READ)
    @GetMapping("/status${DefaultArtifactInfo.DEFAULT_MAPPING_URI}")
    fun artifactScanStatus(@ArtifactPathVariable artifactInfo: ArtifactInfo): Response<ArtifactScanStatus> {
        TODO()
    }
}
