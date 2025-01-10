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
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.mongo.util.Pages
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.job.backup.pojo.task.BackupTask
import com.tencent.bkrepo.job.backup.pojo.task.BackupTaskRequest
import com.tencent.bkrepo.job.backup.service.DataBackupService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/job/backup")
@Principal(type = PrincipalType.ADMIN)
class UserBackupController(
    private val dataBackupService: DataBackupService,
) {
    @PostMapping
    fun createTask(@RequestBody request: BackupTaskRequest): Response<String> {
        return ResponseBuilder.success(dataBackupService.createTask(request))
    }

    @PostMapping("/execute/{taskId}")
    fun executeTask(@PathVariable("taskId") taskId: String): Response<Void> {
        dataBackupService.executeTask(taskId)
        return ResponseBuilder.success()
    }

    @GetMapping("/tasks")
    fun tasks(
        @RequestParam(required = false) state: String? = null,
        @RequestParam(required = false, defaultValue = "$DEFAULT_PAGE_NUMBER") pageNumber: Int = DEFAULT_PAGE_NUMBER,
        @RequestParam(required = false, defaultValue = "$DEFAULT_PAGE_SIZE") pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Response<Page<BackupTask>> {
        val page = dataBackupService.findTasks(state, Pages.ofRequest(pageNumber, pageSize))
        return ResponseBuilder.success(page)
    }
}
