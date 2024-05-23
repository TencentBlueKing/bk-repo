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


package com.tencent.bkrepo.auth.controller.user

import com.tencent.bkrepo.auth.model.TRootDirectoryPermission
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.rootdirectorypermission.QueryRootDirectoryPermissionRequest
import com.tencent.bkrepo.auth.pojo.rootdirectorypermission.CreateRootDirectoryPermissionRequest
import com.tencent.bkrepo.auth.pojo.rootdirectorypermission.UpdateRootDirectoryPermissionRequest
import com.tencent.bkrepo.auth.service.RootDirectoryPermissionService
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestAttribute
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/rootPermission")
class RootDirectoryPermissionController(
    private val rootDirectoryPermissionService: RootDirectoryPermissionService,
    private val permissionManager: PermissionManager
) {

    @PostMapping("/query")
    fun getPermission(
        @RequestBody queryRequest: QueryRootDirectoryPermissionRequest
    ): Response<List<TRootDirectoryPermission>> {
        queryRequest.repoConfig?.let {
            permissionManager.checkRepoPermission(PermissionAction.MANAGE, it.projectId, it.repoName)
            val result = rootDirectoryPermissionService.getPermissionByRepoConfig(it.projectId, it.repoName)
            return ResponseBuilder.success(result)
        }
        queryRequest.status?.let {
            val result = rootDirectoryPermissionService.getPermissionByStatus(it)
            return ResponseBuilder.success(result)
        }
        return ResponseBuilder.fail(HttpStatus.BAD_REQUEST.value, "param repoConfig or status must have one")
    }

    @PostMapping("/upsert")
    fun creatPermission(
        @RequestAttribute userId: String,
        @RequestBody updateRequest: CreateRootDirectoryPermissionRequest
    ): Response<TRootDirectoryPermission> {
        updateRequest.let {
            permissionManager.checkRepoPermission(PermissionAction.MANAGE, it.projectId, it.repoName)
            val request = UpdateRootDirectoryPermissionRequest (
                userId = userId,
                projectId = it.projectId,
                repoName = it.repoName,
                status = it.status,
                updateAt = LocalDateTime.now()
            )
            rootDirectoryPermissionService.createOrUpdatePermission(request)
            return ResponseBuilder.success(
                rootDirectoryPermissionService.getPermissionByRepoConfig(it.projectId,it.repoName)[0])
        }
    }

}