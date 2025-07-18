/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.auth.api

import com.tencent.bkrepo.auth.constant.AUTH_SERVICE_PERMISSION_PREFIX
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.permission.ListPathResult
import com.tencent.bkrepo.common.api.constant.AUTH_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "SERVICE_PERMISSION", description = "服务-权限接口")
@Primary
@FeignClient(AUTH_SERVICE_NAME, contextId = "ServicePermissionResource")
@RequestMapping(AUTH_SERVICE_PERMISSION_PREFIX)
interface ServicePermissionClient {

    @Operation(summary = "list有权限仓库仓库")
    @GetMapping("/repo/list")
    fun listPermissionRepo(
        @Parameter(name = "项目ID")
        @RequestParam projectId: String,
        @Parameter(name = "用户ID")
        @RequestParam userId: String,
        @Parameter(name = "应用ID")
        @RequestParam appId: String?
    ): Response<List<String>>

    @Operation(summary = "list有权限项目")
    @GetMapping("/project/list")
    fun listPermissionProject(
        @Parameter(name = "用户ID")
        @RequestParam userId: String
    ): Response<List<String>>

    @Operation(summary = "list无权限路径")
    @GetMapping("/path/list")
    fun listPermissionPath(
        @Parameter(name = "用户ID")
        @RequestParam userId: String,
        @Parameter(name = "项目ID")
        @RequestParam projectId: String,
        @Parameter(name = "仓库名称")
        @RequestParam repoName: String
    ): Response<ListPathResult>

    @Operation(summary = "校验权限")
    @PostMapping("/check")
    fun checkPermission(
        @Parameter(name = "校验权限信息")
        @RequestBody request: CheckPermissionRequest,
    ): Response<Boolean>

}
