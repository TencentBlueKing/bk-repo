/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.controller.user

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.mongo.util.Pages
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.job.separation.pojo.task.SeparationTask
import com.tencent.bkrepo.job.separation.pojo.task.SeparationTaskRequest
import com.tencent.bkrepo.job.separation.service.SeparationDataService
import com.tencent.bkrepo.job.separation.service.SeparationTaskService
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.packages.PackageListOption
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.VersionListOption
import io.swagger.v3.oas.annotations.Operation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@RestController
@RequestMapping("/api/job/separation")
@Principal(type = PrincipalType.ADMIN)
class UserSeparationController(
    private val separationTaskService: SeparationTaskService,
    private val separationDataService: SeparationDataService
) {
    @PostMapping
    fun createTask(@RequestBody request: SeparationTaskRequest): Response<Void> {
        separationTaskService.createSeparationTask(request)
        return ResponseBuilder.success()
    }

    @GetMapping("/tasks")
    fun tasks(
        @RequestParam(required = false) state: String? = null,
        @RequestParam(required = false) projectId: String? = null,
        @RequestParam(required = false) repoName: String? = null,
        @RequestParam(required = false) taskType: String? = null,
        @RequestParam(required = false, defaultValue = "$DEFAULT_PAGE_NUMBER") pageNumber: Int = DEFAULT_PAGE_NUMBER,
        @RequestParam(required = false, defaultValue = "$DEFAULT_PAGE_SIZE") pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Response<Page<SeparationTask>> {
        val page = separationTaskService.findTasks(
            state, projectId, repoName, taskType ,Pages.ofRequest(pageNumber, pageSize))
        return ResponseBuilder.success(page)
    }

    @PostMapping("/update/{taskId}/state")
    fun updateTaskState(@PathVariable("taskId") taskId: String): Response<Void> {
        separationTaskService.reInitTaskState(taskId)
        return ResponseBuilder.success()
    }

    @Operation(summary = "查询冷表中节点信息")
    @GetMapping("/node/{projectId}/{repoName}")
    fun getNodeInfo(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam fullPath: String,
    ): Response<NodeInfo?> {
        return ResponseBuilder.success(separationDataService.findNodeInfo(projectId, repoName, fullPath))
    }

    @Operation(summary = "查询冷表中版本信息")
    @GetMapping("/version/{projectId}/{repoName}")
    fun getVersionInfo(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam packageKey: String,
        @RequestParam version: String,
    ): Response<PackageVersion?> {
        return ResponseBuilder.success(
            separationDataService.findPackageVersion(projectId, repoName, packageKey, version)
        )
    }

    @Operation(summary = "分页查询包")
    @GetMapping("/package/page/{projectId}/{repoName}")
    fun listPackagePage(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        option: PackageListOption
    ): Response<Page<PackageSummary>> {
        val pageResult = separationDataService.listPackagePage(projectId, repoName, option)
        return ResponseBuilder.success(pageResult)
    }

    @Operation(summary = "分页查询版本")
    @GetMapping("/version/page/{projectId}/{repoName}")
    fun listVersionPage(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam packageKey: String,
        @RequestParam separationDate: String,
        option: VersionListOption
    ): Response<Page<PackageVersion>> {
        val sDate = convert(separationDate)
        val pageResult = separationDataService.listVersionPage(projectId, repoName, packageKey, option, sDate)
        return ResponseBuilder.success(pageResult)
    }


    @Operation(summary = "分页查询节点")
    @GetMapping("/node/page/{projectId}/{repoName}")
    fun listPageNode(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam fullPath: String,
        @RequestParam separationDate: String,
        nodeListOption: NodeListOption
    ): Response<Page<NodeInfo>> {
        val sDate = convert(separationDate)
        val nodePage = separationDataService.listNodePage(projectId, repoName, fullPath, nodeListOption, sDate)
        return ResponseBuilder.success(nodePage)
    }


    private fun convert(separationDate: String): LocalDateTime {
        val separateDate = try {
            LocalDateTime.parse(separationDate, DateTimeFormatter.ISO_DATE_TIME)
        } catch (e: DateTimeParseException) {
            throw BadRequestException(
                CommonMessageCode.PARAMETER_INVALID,
                "separationDate"
            )
        }
        return separateDate
    }

}
