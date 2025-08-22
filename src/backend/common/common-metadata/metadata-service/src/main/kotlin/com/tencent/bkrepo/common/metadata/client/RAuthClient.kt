/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.client

import com.tencent.bkrepo.auth.pojo.oauth.AuthorizationGrantType
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.permission.ListPathResult
import com.tencent.bkrepo.auth.pojo.user.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.user.CreateUserToProjectRequest
import com.tencent.bkrepo.auth.pojo.user.User
import com.tencent.bkrepo.common.api.constant.AUTH_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.v3.oas.annotations.Parameter
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import reactivefeign.spring.config.ReactiveFeignClient
import reactor.core.publisher.Mono

/**
 * ReactiveFeignClient qualifier不生效，每个服务的feign client需要都写在一个接口类内
 * https://github.com/PlaytikaOSS/feign-reactive/issues/611
 * 升级到4.0.3后可以拆分
 */
@ReactiveFeignClient(AUTH_SERVICE_NAME)
@RequestMapping("/service")
interface RAuthClient {

    @PostMapping("/user/token")
    fun checkToken(
        @RequestParam
        uid: String,
        @RequestParam
        token: String
    ): Mono<Response<Boolean>>

    @PostMapping("/permission/check")
    fun checkPermission(
        @RequestBody request: CheckPermissionRequest
    ): Mono<Response<Boolean>>

    @GetMapping("/permission/repo/list")
    fun listPermissionRepo(
        @Parameter(name = "项目ID")
        @RequestParam projectId: String,
        @Parameter(name = "用户ID")
        @RequestParam userId: String,
        @Parameter(name = "应用ID")
        @RequestParam appId: String?
    ): Mono<Response<List<String>>>

    @GetMapping("/user/detail/{uid}")
    fun detail(
        @PathVariable uid: String
    ): Mono<Response<User?>>

    @PostMapping("/user/create")
    fun create(
        @RequestBody request: CreateUserRequest
    ): Mono<Response<Boolean>>

    @PostMapping("/user/create/project")
    fun createUserToProject(
        @RequestBody request: CreateUserToProjectRequest
    ): Mono<Response<Boolean>>

    @PostMapping("/user/role/{uid}/{rid}")
    fun addUserRole(
        @Parameter(name = "用户id")
        @PathVariable uid: String,
        @Parameter(name = "用户角色id")
        @PathVariable rid: String
    ): Mono<Response<User?>>

    @PostMapping("/account/credential")
    fun checkAccountCredential(
        @RequestParam accesskey: String,
        @RequestParam secretkey: String,
        @RequestParam authorizationGrantType: AuthorizationGrantType? = null
    ): Mono<Response<String?>>

    @GetMapping("/permission/project/list")
    fun listPermissionProject(
        @Parameter(name = "用户ID")
        @RequestParam userId: String
    ): Mono<Response<List<String>>>

    @GetMapping("/permission/path/list")
    fun listPermissionPath(
        @Parameter(name = "用户ID")
        @RequestParam userId: String,
        @Parameter(name = "项目ID")
        @RequestParam projectId: String,
        @Parameter(name = "仓库名称")
        @RequestParam repoName: String
    ): Mono<Response<ListPathResult>>

    @PostMapping("/bkiamv3/rbac/group/check")
    fun getExistRbacDefaultGroupProjectIds(
        @Parameter(name = "项目ID列表")
        @RequestBody projectIdList: List<String> = emptyList()
    ): Mono<Response<Map<String, Boolean>>>

    @PostMapping("/bkiamv3/create/project/manage/{projectId}")
    fun createProjectManage(
        @Parameter(name = "用户id")
        @RequestParam userId: String,
        @Parameter(name = "项目名称")
        @PathVariable projectId: String
    ): Mono<Response<String?>>

    @PostMapping("/bkiamv3/create/repo/manage/{projectId}/{repoName}")
    fun createRepoManage(
        @Parameter(name = "用户id")
        @RequestParam userId: String,
        @Parameter(name = "项目名称")
        @PathVariable projectId: String,
        @Parameter(name = "仓库名称")
        @PathVariable repoName: String
    ): Mono<Response<String?>>

    @DeleteMapping("/bkiamv3/delete/repo/manage/{projectId}/{repoName}")
    fun deleteRepoManageGroup(
        @Parameter(name = "用户id")
        @RequestParam userId: String,
        @Parameter(name = "项目名称")
        @PathVariable projectId: String,
        @Parameter(name = "仓库名称")
        @PathVariable repoName: String
    ): Mono<Response<Boolean>>

    @PostMapping("/role/create/project/manage/{projectId}")
    fun createProjectManage(
        @Parameter(name = "仓库名称")
        @PathVariable projectId: String
    ): Mono<Response<String?>>

    @PostMapping("/role/create/repo/manage/{projectId}/{repoName}")
    fun createRepoManage(
        @Parameter(name = "仓库ID")
        @PathVariable projectId: String,
        @Parameter(name = "项目ID")
        @PathVariable repoName: String
    ): Mono<Response<String?>>
}
