/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.auth.controller.service

import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.auth.pojo.user.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.user.CreateUserToProjectRequest
import com.tencent.bkrepo.auth.pojo.user.User
import com.tencent.bkrepo.auth.pojo.user.UserInfo
import com.tencent.bkrepo.auth.service.RoleService
import com.tencent.bkrepo.auth.service.UserService
import com.tencent.bkrepo.auth.util.RequestUtil
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ServiceUserController @Autowired constructor(
    private val userService: UserService,
    private val roleService: RoleService
) : ServiceUserClient {

    override fun createUser(request: CreateUserRequest): Response<Boolean> {
        userService.createUser(request)
        return ResponseBuilder.success(true)
    }

    @ApiOperation("创建项目用户")
    @PostMapping("/create/project")
    override fun createUserToProject(request: CreateUserToProjectRequest): Response<Boolean> {
        userService.createUserToProject(request)
        val createRoleRequest = RequestUtil.buildProjectAdminRequest(request.projectId)
        val roleId = roleService.createRole(createRoleRequest)
        userService.addUserToRole(request.userId, roleId!!)
        return ResponseBuilder.success(true)
    }

    @Deprecated("仅用于兼容旧接口", ReplaceWith("userInfoById"))
    override fun detail(uid: String): Response<User?> {
        return ResponseBuilder.success(userService.getUserById(uid))
    }

    override fun addUserRole(uid: String, rid: String): Response<User?> {
        val result = userService.addUserToRole(uid, rid)
        return ResponseBuilder.success(result)
    }

    override fun checkToken(uid: String, token: String): Response<Boolean> {
        userService.findUserByUserToken(uid, token) ?: return ResponseBuilder.success(false)
        return ResponseBuilder.success(true)
    }

    override fun userInfoById(uid: String): Response<UserInfo?> {
        return ResponseBuilder.success(userService.getUserInfoById(uid))
    }

    override fun userPwdById(uid: String): Response<String?> {
        return ResponseBuilder.success(userService.getUserPwdById(uid))
    }

    override fun userTokenById(uid: String): Response<List<String>> {
        return ResponseBuilder.success(userService.listValidToken(uid).map { it.id })
    }
}
