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

package com.tencent.bkrepo.auth.controller.service

import com.tencent.bkrepo.auth.api.ServiceRoleClient
import com.tencent.bkrepo.auth.constant.PROJECT_MANAGE_ID
import com.tencent.bkrepo.auth.constant.PROJECT_MANAGE_NAME
import com.tencent.bkrepo.auth.constant.REPO_MANAGE_ID
import com.tencent.bkrepo.auth.constant.REPO_MANAGE_NAME
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.pojo.role.CreateRoleRequest
import com.tencent.bkrepo.auth.pojo.role.RoleInfo
import com.tencent.bkrepo.auth.pojo.role.UpdateRoleRequest
import com.tencent.bkrepo.auth.service.RoleService
import com.tencent.bkrepo.auth.service.UserService
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ServiceRoleController @Autowired constructor(
    private val roleService: RoleService,
    private val userService: UserService,
) : ServiceRoleClient {

    override fun createProjectManage(projectId: String): Response<String?> {
        val request = CreateRoleRequest(
            roleId = PROJECT_MANAGE_ID,
            name = PROJECT_MANAGE_NAME,
            type = RoleType.PROJECT,
            projectId = projectId,
            admin = true
        )
        val id = roleService.createRole(request)
        return ResponseBuilder.success(id)
    }

    override fun createRepoManage(projectId: String, repoName: String): Response<String?> {
        val request = CreateRoleRequest(
            roleId = REPO_MANAGE_ID,
            name = REPO_MANAGE_NAME,
            type = RoleType.REPO,
            projectId = projectId,
            repoName = repoName,
            admin = true
        )
        val id = roleService.createRole(request)
        return ResponseBuilder.success(id)
    }

    override fun listRoleByProject(projectId: String): Response<List<RoleInfo>> {
        val roles = roleService.listRoleByProject(projectId)
        return ResponseBuilder.success(roles.map { it.toRoleInfo() })
    }

    override fun listRoleByProjectPage(projectId: String, pageNumber: Int, pageSize: Int): Response<List<RoleInfo>> {
        val roles = roleService.listRoleByProjectPage(projectId, pageNumber, pageSize)
        return ResponseBuilder.success(roles.map { it.toRoleInfo() })
    }

    private fun com.tencent.bkrepo.auth.pojo.role.Role.toRoleInfo() = RoleInfo(
        id = id,
        roleId = roleId,
        name = name,
        type = type.name,
        projectId = projectId,
        repoName = repoName,
        admin = admin,
        users = users,
        description = description
    )

    override fun createRoleForFederation(roleInfo: RoleInfo): Response<String?> {
        val type = runCatching { RoleType.valueOf(roleInfo.type) }.getOrDefault(RoleType.PROJECT)
        val request = CreateRoleRequest(
            roleId = roleInfo.roleId,
            name = roleInfo.name,
            type = type,
            projectId = roleInfo.projectId,
            repoName = roleInfo.repoName,
            admin = roleInfo.admin,
            description = roleInfo.description
        )
        val id = roleService.createRole(request)
        if (!roleInfo.users.isNullOrEmpty() && id != null) {
            runCatching { userService.addUserToRoleBatch(roleInfo.users, id) }
        }
        return ResponseBuilder.success(id)
    }

    override fun updateRoleForFederation(roleInfo: RoleInfo): Response<Boolean> {
        val projectId = roleInfo.projectId ?: return ResponseBuilder.success(false)
        val existingRole = roleService.detail(roleInfo.roleId, projectId)
            ?: return ResponseBuilder.success(false)
        val request = UpdateRoleRequest(
            name = roleInfo.name,
            description = roleInfo.description,
            userIds = roleInfo.users.toSet(),
            deptInfoList = null
        )
        val result = roleService.updateRoleInfo(existingRole.id!!, request)
        return ResponseBuilder.success(result)
    }

    override fun deleteRoleForFederation(id: String): Response<Boolean> {
        val result = roleService.deleteRoleById(id)
        return ResponseBuilder.success(result)
    }
}
