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

package com.tencent.bkrepo.auth.controller.user

import com.tencent.bkrepo.auth.constant.AUTH_API_USER_PREFIX
import com.tencent.bkrepo.auth.constant.BKREPO_TICKET
import com.tencent.bkrepo.auth.controller.OpenResource
import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.pojo.enums.AuthPermissionType
import com.tencent.bkrepo.auth.pojo.token.Token
import com.tencent.bkrepo.auth.pojo.token.TokenResult
import com.tencent.bkrepo.auth.pojo.user.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.user.CreateUserToProjectRequest
import com.tencent.bkrepo.auth.pojo.user.CreateUserToRepoRequest
import com.tencent.bkrepo.auth.pojo.user.UpdateUserRequest
import com.tencent.bkrepo.auth.pojo.user.User
import com.tencent.bkrepo.auth.pojo.user.UserInfo
import com.tencent.bkrepo.auth.pojo.user.UserResult
import com.tencent.bkrepo.auth.service.PermissionService
import com.tencent.bkrepo.auth.service.RoleService
import com.tencent.bkrepo.auth.service.UserService
import com.tencent.bkrepo.auth.util.RequestUtil.buildProjectAdminRequest
import com.tencent.bkrepo.auth.util.RequestUtil.buildRepoAdminRequest
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.http.jwt.JwtAuthProperties
import com.tencent.bkrepo.common.security.util.JwtUtils
import com.tencent.bkrepo.common.security.util.RsaUtils
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.v3.oas.annotations.Operation
import org.bouncycastle.crypto.CryptoException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.Base64
import javax.servlet.http.Cookie

@RestController
@RequestMapping(AUTH_API_USER_PREFIX)
class UserController @Autowired constructor(
    private val userService: UserService,
    private val roleService: RoleService,
    private val jwtProperties: JwtAuthProperties,
    permissionService: PermissionService
) : OpenResource(permissionService) {

    private val signingKey = JwtUtils.createSigningKey(jwtProperties.secretKey)

    @Operation(summary = "创建用户")
    @PostMapping("/create")
    fun createUser(@RequestBody request: CreateUserRequest): Response<Boolean> {
        preCheckUserAdmin()
        // 限制创建为admin用户
        request.admin = false
        userService.createUser(request)
        return ResponseBuilder.success(true)
    }

    @Operation(summary = "创建项目用户")
    @PostMapping("/create/project")
    fun createUserToProject(@RequestBody request: CreateUserToProjectRequest): Response<Boolean> {
        // 限制创建为admin用户
        request.admin = false
        preCheckUserInProject(AuthPermissionType.PROJECT, request.projectId, null)
        userService.createUserToProject(request)
        val createRoleRequest = buildProjectAdminRequest(request.projectId)
        val roleId = roleService.createRole(createRoleRequest)
        userService.addUserToRole(request.userId, roleId!!)
        return ResponseBuilder.success(true)
    }

    @Operation(summary = "创建仓库用户")
    @PostMapping("/create/repo")
    fun createUserToRepo(@RequestBody request: CreateUserToRepoRequest): Response<Boolean> {
        // 限制创建为admin用户
        request.admin = false
        preCheckUserInProject(AuthPermissionType.PROJECT, request.projectId, null)
        userService.createUserToRepo(request)
        val createRoleRequest = buildRepoAdminRequest(request.projectId, request.repoName)
        val roleId = roleService.createRole(createRoleRequest)
        userService.addUserToRole(request.userId, roleId!!)
        return ResponseBuilder.success(true)
    }

    @Operation(summary = "用户列表")
    @GetMapping("/list")
    fun listUser(@RequestBody rids: List<String>?): Response<List<UserResult>> {
        if (rids != null && rids.isNotEmpty()) {
            preCheckUserAdmin()
        }
        val result = userService.listUser(rids.orEmpty()).map {
            UserResult(it.userId, it.name)
        }
        return ResponseBuilder.success(result)
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/delete/{uid}")
    fun deleteById(@PathVariable uid: String): Response<Boolean> {
        preCheckUserOrAssetUser(uid, userService.getRelatedUserById(SecurityUtils.getUserId()))
        userService.deleteById(uid)
        return ResponseBuilder.success(true)
    }

    @Operation(summary = "用户详情")
    @GetMapping("/detail/{uid}")
    fun detail(@PathVariable uid: String): Response<User?> {
        preCheckContextUser(uid)
        return ResponseBuilder.success(userService.getUserById(uid))
    }

    @Operation(summary = "更新用户信息")
    @PutMapping("/update/info/{uid}")
    fun updateUserInfoById(@PathVariable uid: String, @RequestBody request: UpdateUserRequest): Response<Boolean> {
        preCheckContextUser(uid)
        if (request.admin != null && request.admin) {
            preCheckUserAdmin()
            preCheckPlatformPermission()
        }
        userService.updateUserById(uid, request)
        return ResponseBuilder.success(true)
    }

    @Operation(summary = "新增用户所属角色")
    @PostMapping("/role/{uid}/{rid}")
    fun addUserRole(@PathVariable uid: String, @PathVariable rid: String): Response<User?> {
        preCheckContextUser(uid)
        val result = userService.addUserToRole(uid, rid)
        return ResponseBuilder.success(result)
    }

    @Operation(summary = "删除用户所属角色")
    @DeleteMapping("/role/{uid}/{rid}")
    fun removeUserRole(@PathVariable uid: String, @PathVariable rid: String): Response<User?> {
        preCheckContextUser(uid)
        val result = userService.removeUserFromRole(uid, rid)
        return ResponseBuilder.success(result)
    }

    @Operation(summary = "批量新增用户所属角色")
    @PatchMapping("/role/add/{rid}")
    fun addUserRoleBatch(@PathVariable rid: String, @RequestBody request: List<String>): Response<Boolean> {
        preCheckUserAdmin()
        userService.addUserToRoleBatch(request, rid)
        return ResponseBuilder.success(true)
    }

    @Operation(summary = "批量删除用户所属角色")
    @PatchMapping("/role/delete/{rid}")
    fun deleteUserRoleBatch(@PathVariable rid: String, @RequestBody request: List<String>): Response<Boolean> {
        preCheckUserAdmin()
        userService.removeUserFromRoleBatch(request, rid)
        return ResponseBuilder.success(true)
    }

    @Operation(summary = "新加用户token")
    @PostMapping("/token/{uid}/{name}")
    fun addUserToken(
        @PathVariable("uid") uid: String,
        @PathVariable("name") name: String,
        @RequestParam expiredAt: String?,
        @RequestParam projectId: String?
    ): Response<Token?> {
        preCheckUserOrAssetUser(uid, userService.getRelatedUserById(SecurityUtils.getUserId()))
        // add user token
        val result = userService.addUserToken(uid, name, expiredAt)
        return ResponseBuilder.success(result)
    }

    @Operation(summary = "查询用户token列表")
    @GetMapping("/list/token/{uid}")
    fun listUserToken(@PathVariable("uid") uid: String): Response<List<TokenResult>> {
        preCheckContextUser(uid)
        val result = userService.listUserToken(uid)
        return ResponseBuilder.success(result)
    }

    @Operation(summary = "删除用户token")
    @DeleteMapping("/token/{uid}/{name}")
    fun deleteToken(@PathVariable uid: String, @PathVariable name: String): Response<Boolean> {
        preCheckContextUser(uid)
        val result = userService.removeToken(uid, name)
        return ResponseBuilder.success(result)
    }

    @Operation(summary = "校验用户token")
    @PostMapping("/token")
    @Deprecated("no need work")
    fun checkToken(@RequestParam uid: String, @RequestParam token: String): Response<Boolean> {
        preCheckContextUser(uid)
        userService.findUserByUserToken(uid, token) ?: return ResponseBuilder.success(false)
        return ResponseBuilder.success(true)
    }

    @Operation(summary = "获取公钥")
    @GetMapping("/rsa")
    fun getPublicKey(): Response<String?> {
        return ResponseBuilder.success(RsaUtils.publicKey)
    }

    @Operation(summary = "校验登陆会话")
    @PostMapping("/login")
    fun loginUser(@RequestParam("uid") uid: String, @RequestParam("token") token: String): Response<Boolean> {
        val decryptToken: String?
        try {
            decryptToken = RsaUtils.decrypt(token)
        } catch (e: CryptoException) {
            logger.warn("token decrypt failed token [$uid]")
            throw AuthenticationException(messageCode = AuthMessageCode.AUTH_LOGIN_FAILED)
        }

        userService.findUserByUserToken(uid, decryptToken) ?: run {
            logger.info("user not match [$uid]")
            return ResponseBuilder.success(false)
        }
        val ticket = JwtUtils.generateToken(signingKey, jwtProperties.expiration, uid)
        val cookie = Cookie(BKREPO_TICKET, ticket)
        cookie.path = "/"
        cookie.maxAge = 60 * 60 * 24
        HttpContextHolder.getResponse().addCookie(cookie)
        return ResponseBuilder.success(true)
    }

    @Operation(summary = "独立部署模式下，获取用户信息")
    @GetMapping("/info")
    fun userInfo(
        @RequestHeader("x-bkrepo-uid") bkUserId: String?,
        @RequestHeader("x-bkrepo-display-name") displayName: String?,
        @RequestHeader("x-bkrepo-tenant-id") tenantId: String?,
    ): Response<Map<String, Any>> {
        val name = if (displayName == null) "" else String(Base64.getDecoder().decode(displayName))
        val result = mapOf(
            "userId" to bkUserId.orEmpty(),
            "displayName" to name,
            "tenantId" to tenantId.orEmpty()
        )
        return ResponseBuilder.success(result)
    }

    @Operation(summary = "校验用户ticket")
    @GetMapping("/verify")
    fun verify(@RequestParam(value = "bkrepo_ticket") bkrepoToken: String?): Response<Map<String, Any>> {
        try {
            bkrepoToken ?: run {
                throw IllegalArgumentException("ticket can not be null")
            }
            val userId = JwtUtils.validateToken(signingKey, bkrepoToken).body.subject
            val result = mapOf("user_id" to userId)
            return ResponseBuilder.success(result)
        } catch (ignored: Exception) {
            logger.warn("validate user token false [$bkrepoToken]")
            throw ErrorCodeException(AuthMessageCode.AUTH_LOGIN_TOKEN_CHECK_FAILED)
        }
    }

    @Operation(summary = "用户分页列表")
    @GetMapping("page/{pageNumber}/{pageSize}")
    fun userPage(
        @PathVariable pageNumber: Int,
        @PathVariable pageSize: Int,
        @RequestParam user: String? = null,
        @RequestParam admin: Boolean?,
        @RequestParam locked: Boolean?
    ): Response<Page<UserInfo>> {
        preCheckUserAdmin()
        val result = userService.userPage(pageNumber, pageSize, user, admin, locked)
        return ResponseBuilder.success(result)
    }

    @Operation(summary = "用户info")
    @GetMapping("/userinfo/{uid}")
    fun userInfoById(@PathVariable uid: String): Response<UserInfo?> {
        preCheckContextUser(uid)
        return ResponseBuilder.success(userService.getUserInfoById(uid))
    }

    @Operation(summary = "修改用户密码")
    @PutMapping("/update/password/{uid}")
    fun updatePassword(
        @PathVariable uid: String,
        @RequestParam oldPwd: String,
        @RequestParam newPwd: String
    ): Response<Boolean> {
        preCheckContextUser(uid)
        val decryptOldPwd = RsaUtils.decrypt(oldPwd)
        val decryptNewPwd = RsaUtils.decrypt(newPwd)
        return ResponseBuilder.success(userService.updatePassword(uid, decryptOldPwd, decryptNewPwd))
    }

    @Operation(summary = "用户info ")
    @PostMapping("/reset/{uid}")
    fun resetPassword(@PathVariable uid: String): Response<Boolean> {
        preCheckContextUser(uid)
        return ResponseBuilder.success(userService.resetPassword(uid))
    }

    @Operation(summary = "检验系统中是否存在同名userId ")
    @GetMapping("/repeat/{uid}")
    fun repeatUid(@PathVariable uid: String): Response<Boolean> {
        preCheckContextUser(uid)
        return ResponseBuilder.success(userService.repeatUid(uid))
    }

    @Operation(summary = "判断用户是否为项目管理员")
    @GetMapping("/admin/{projectId}")
    fun isProjectAdmin(@PathVariable projectId: String): Response<Boolean> {
        return ResponseBuilder.success(isContextUserProjectAdmin(projectId))
    }

    @Operation(summary = "检验实体用户是否存在此userid")
    @GetMapping("/validateEntityUser/{uid}")
    fun validateEntityUser(@PathVariable uid: String): Response<Boolean> {
        preCheckContextUser(uid)
        return ResponseBuilder.success(userService.validateEntityUser(uid))
    }

    @Operation(summary = "相关虚拟列表")
    @GetMapping("/group")
    fun userGroup(
        @RequestParam userName: String? = null,
        @RequestParam asstUser: String,
    ): Response<List<UserInfo>> {
        preCheckContextUser(asstUser)
        val result = userService.getRelatedUserById(asstUser)
        return ResponseBuilder.success(result)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserController::class.java)
    }
}
