/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.metadata.pojo.log.OpLogListOption
import com.tencent.bkrepo.common.metadata.pojo.log.OperateLog
import com.tencent.bkrepo.common.metadata.pojo.log.OperateLogResponse
import com.tencent.bkrepo.common.metadata.service.log.OperateLogService
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "操作日志用户接口")
@RestController
@RequestMapping("/api/log")
class UserOperateLogController(
    private val operateLogService: OperateLogService,
    private val permissionManager: PermissionManager
) {

    @PostMapping("/list")
    fun list(
        @RequestBody option: OpLogListOption
    ): Response<Page<OperateLog>> {
        checkPermission(option.projectId)
        return ResponseBuilder.success(operateLogService.listPage(option))
    }

    @Operation(summary = "审计日志查询接口")
    @GetMapping("/page")
    fun page(
        @Parameter(name = "资源类型", required = false)
        @RequestParam type: String?,
        @Parameter(name = "项目名", required = false)
        @RequestParam projectId: String?,
        @Parameter(name = "仓库名", required = false)
        @RequestParam repoName: String?,
        @Parameter(name = "操作人", required = false)
        @RequestParam operator: String?,
        @Parameter(name = "开始时间", required = false)
        @RequestParam startTime: String?,
        @Parameter(name = "结束时间", required = false)
        @RequestParam endTime: String?,
        @Parameter(name = "页数", required = false)
        @RequestParam pageNumber: Int?,
        @Parameter(name = "每页数量", required = false)
        @RequestParam pageSize: Int?
    ): Response<Page<OperateLogResponse?>> {
        checkPermission(projectId)
        val page = operateLogService.page(
            type, projectId, repoName,
            operator, startTime, endTime, pageNumber ?: 1, pageSize ?: 20
        )
        return ResponseBuilder.success(page)
    }

    private fun checkPermission(projectId: String?) {
        try {
            permissionManager.checkPrincipal(SecurityUtils.getUserId(), PrincipalType.ADMIN)
        } catch (e: PermissionException) {
            projectId?.let { permissionManager.checkProjectPermission(PermissionAction.MANAGE, it) }
                ?: throw e
        }
    }
}
