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
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.job.migrate.MigrateFailedNodeService
import com.tencent.bkrepo.job.migrate.MigrateRepoStorageService
import com.tencent.bkrepo.job.migrate.pojo.CreateMigrateRepoStorageTaskRequest
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTask
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/job/migrate")
@Principal(type = PrincipalType.ADMIN)
class UserMigrateRepoStorageController(
    private val migrateRepoStorageService: MigrateRepoStorageService,
    private val migrateFailedNodeService: MigrateFailedNodeService,
) {
    @PostMapping
    fun migrate(
        @RequestBody request: CreateMigrateRepoStorageTaskRequest
    ): Response<MigrateRepoStorageTask> {
        val task = migrateRepoStorageService.createTask(request.copy(operator = SecurityUtils.getUserId()))
        return ResponseBuilder.success(task)
    }

    @GetMapping("/tasks")
    fun tasks(
        @RequestParam(required = false) state: String? = null,
        @RequestParam(required = false, defaultValue = "$DEFAULT_PAGE_NUMBER") pageNumber: Int = DEFAULT_PAGE_NUMBER,
        @RequestParam(required = false, defaultValue = "$DEFAULT_PAGE_SIZE") pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Response<Page<MigrateRepoStorageTask>> {
        val page = migrateRepoStorageService.findTask(state, Pages.ofRequest(pageNumber, pageSize))
        return ResponseBuilder.success(page)
    }

    @DeleteMapping("/failed/node")
    fun removeFailedNode(
        @RequestParam projectId: String,
        @RequestParam repoName: String,
        @RequestParam(required = false) fullPath: String? = null
    ) {
        migrateFailedNodeService.removeFailedNode(projectId, repoName, fullPath)
    }

    @PostMapping("/failed/node/reset")
    fun resetFailedNodeRetryCount(
        @RequestParam projectId: String,
        @RequestParam repoName: String,
        @RequestParam(required = false) fullPath: String? = null
    ) {
        migrateFailedNodeService.resetRetryCount(projectId, repoName, fullPath)
    }

    @PostMapping("/failed/node/autofix")
    fun autoFix(
        @RequestParam(required = false) projectId: String? = null,
        @RequestParam(required = false) repoName: String? = null,
    ) {
        if (projectId.isNullOrEmpty() && repoName.isNullOrEmpty()) {
            migrateFailedNodeService.autoFix()
        } else if (!projectId.isNullOrEmpty() && !repoName.isNullOrEmpty()) {
            migrateFailedNodeService.autoFix(projectId, repoName)
        } else {
            throw BadRequestException(CommonMessageCode.PARAMETER_INVALID, "miss projectId or repoName")
        }
    }
}
