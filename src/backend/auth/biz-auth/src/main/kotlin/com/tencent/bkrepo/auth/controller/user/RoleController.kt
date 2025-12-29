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

package com.tencent.bkrepo.auth.controller.user

import com.tencent.bkrepo.auth.constant.AUTH_API_ROLE_PREFIX
import com.tencent.bkrepo.auth.controller.OpenResource
import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.pojo.role.CreateRoleRequest
import com.tencent.bkrepo.auth.pojo.role.Role
import com.tencent.bkrepo.auth.pojo.role.UpdateRoleRequest
import com.tencent.bkrepo.auth.pojo.user.UserResult
import com.tencent.bkrepo.auth.service.PermissionService
import com.tencent.bkrepo.auth.service.RoleService
import com.tencent.bkrepo.auth.util.RequestUtil.buildProjectAdminRequest
import com.tencent.bkrepo.auth.util.RequestUtil.buildRepoAdminRequest
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.v3.oas.annotations.Operation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(AUTH_API_ROLE_PREFIX)
class RoleController @Autowired constructor(
    private val roleService: RoleService,
    permissionService: PermissionService
) : OpenResource(permissionService) {

    @Operation(summary = "创建角色")
    @PostMapping("/create")
    fun createRole(@RequestBody request: CreateRoleRequest): Response<String?> {
        validateParams(request)
        checkRolePermission(request.projectId)
        val id = roleService.createRole(request)
        return ResponseBuilder.success(id)
    }

    @Operation(summary = "创建项目管理员")
    @PostMapping("/create/project/manage/{projectId}")
    fun createProjectManage(@PathVariable projectId: String): Response<String?> {
        preCheckProjectAdmin(projectId)
        val request = buildProjectAdminRequest(projectId)
        val id = roleService.createRole(request)
        return ResponseBuilder.success(id)
    }

    @Operation(summary = "创建仓库管理员")
    @PostMapping("/create/repo/manage/{projectId}/{repoName}")
    fun createRepoManage(@PathVariable projectId: String, @PathVariable repoName: String): Response<String?> {
        preCheckProjectAdmin(projectId)
        val request = buildRepoAdminRequest(projectId, repoName)
        val id = roleService.createRole(request)
        return ResponseBuilder.success(id)
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/delete/{id}")
    fun deleteRole(@PathVariable id: String): Response<Boolean> {
        val role = roleService.detail(id) ?: return ResponseBuilder.success(false)
        checkRolePermission(role.projectId)
        roleService.deleteRoleById(id)
        return ResponseBuilder.success(true)
    }

    @Operation(summary = "根据主键id查询角色详情")
    @GetMapping("/detail/{id}")
    fun detail(@PathVariable id: String): Response<Role?> {
        val role = roleService.detail(id) ?: return ResponseBuilder.success(null)
        checkRolePermission(role.projectId)
        return ResponseBuilder.success(role)
    }

    @Operation(summary = "根据角色ID与项目Id查询角色")
    @GetMapping("/detail/{rid}/{projectId}")
    fun detailByProject(@PathVariable rid: String, @PathVariable projectId: String): Response<Role?> {
        preCheckProjectAdmin(projectId)
        val result = roleService.detail(rid, projectId)
        return ResponseBuilder.success(result)
    }

    @Operation(summary = "根据角色ID与项目Id,仓库名查询角色")
    @GetMapping("/detail/{rid}/{projectId}/{repoName}")
    fun detailByProjectAndRepo(
        @PathVariable rid: String,
        @PathVariable projectId: String,
        @PathVariable repoName: String
    ): Response<Role?> {
        preCheckProjectAdmin(projectId)
        val result = roleService.detail(rid, projectId, repoName)
        return ResponseBuilder.success(result)
    }

    @Operation(summary = "查询用户组下用户列表")
    @GetMapping("/users/{id}")
    fun listUserByRole(@PathVariable id: String): Response<Set<UserResult>> {
        val role = roleService.detail(id) ?: return ResponseBuilder.success(emptySet())
        checkRolePermission(role.projectId)
        return ResponseBuilder.success(roleService.listUserByRoleId(id))
    }

    @Operation(summary = "编辑用户组信息")
    @PutMapping("/update/info/{id}")
    @Transactional(rollbackFor = [Exception::class])
    fun updateRoleInfo(
        @PathVariable id: String,
        @RequestBody updateRoleRequest: UpdateRoleRequest
    ): Response<Boolean> {
        val role = roleService.detail(id) ?: return ResponseBuilder.success(false)
        checkRolePermission(role.projectId)
        return ResponseBuilder.success(roleService.updateRoleInfo(id, updateRoleRequest))
    }

    @GetMapping("/list/{projectId}")
    fun allRole(
        @PathVariable projectId: String
    ): Response<List<Role>> {
        preCheckProjectAdmin(projectId)
        return ResponseBuilder.success(roleService.listRoleByProject(projectId))
    }

    private fun validateParams(request: CreateRoleRequest) {
        // 验证projectId与角色类型的关系
        if (request.projectId == null) {
            // projectId为null时，只能创建非项目管理员的SERVICE角色
            if (request.type != RoleType.SERVICE || request.admin) {
                throw ErrorCodeException(AuthMessageCode.AUTH_CREATE_ROLE_INVALID_WITHOUT_PROJECT)
            }
        } else {
            // projectId不为null时，不能创建SERVICE角色
            if (request.type == RoleType.SERVICE) {
                throw ErrorCodeException(AuthMessageCode.AUTH_CREATE_SERVICE_ROLE_WITH_PROJECT)
            }
        }
    }

    /**
     * 根据projectId检查权限
     * - projectId为null时，检查是否为系统管理员
     * - projectId不为null时，检查是否为项目管理员
     */
    private fun checkRolePermission(projectId: String?) {
        if (projectId == null) {
            preCheckUserAdmin()
        } else {
            preCheckProjectAdmin(projectId)
        }
    }
}
