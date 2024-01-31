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

package com.tencent.bkrepo.auth.service.local

import com.tencent.bkrepo.auth.dao.UserDao
import com.tencent.bkrepo.auth.constant.PROJECT_MANAGE_ID
import com.tencent.bkrepo.auth.constant.PROJECT_VIEWER_ID
import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.model.TRole
import com.tencent.bkrepo.auth.pojo.role.CreateRoleRequest
import com.tencent.bkrepo.auth.pojo.role.Role
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.pojo.role.UpdateRoleRequest
import com.tencent.bkrepo.auth.pojo.user.UserResult
import com.tencent.bkrepo.auth.dao.repository.RoleRepository
import com.tencent.bkrepo.auth.service.RoleService
import com.tencent.bkrepo.auth.service.UserService
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import org.slf4j.LoggerFactory

class RoleServiceImpl constructor(
    private val roleRepository: RoleRepository,
    private val userService: UserService,
    override val userDao: UserDao
) : RoleService, AbstractServiceImpl(userDao, roleRepository) {

    override fun createRole(request: CreateRoleRequest): String? {
        return createRoleCommon(request)
    }


    override fun detail(id: String): Role? {
        logger.debug("get role detail : [$id] ")
        val result = roleRepository.findFirstById(id) ?: return null
        return transfer(result)
    }

    override fun detail(rid: String, projectId: String): Role? {
        logger.debug("get  role  detail rid : [$rid] , projectId : [$projectId] ")
        val result = roleRepository.findFirstByRoleIdAndProjectId(rid, projectId) ?: return null
        return transfer(result)
    }

    override fun detail(rid: String, projectId: String, repoName: String): Role? {
        logger.debug("get  role  detail rid : [$rid] , projectId : [$projectId], repoName: [$repoName]")
        val result = roleRepository.findFirstByRoleIdAndProjectIdAndRepoName(rid, projectId, repoName) ?: return null
        return transfer(result)
    }

    override fun updateRoleInfo(id: String, updateRoleRequest: UpdateRoleRequest): Boolean {
        with(updateRoleRequest) {
            if (name != null || description != null) {
                val role = roleRepository.findFirstById(id)
                if (role != null) {
                    name?.let { role.name = name }
                    description?.let { role.description = description }
                    roleRepository.save(role)
                }
            }

            updateRoleRequest.userIds?.map { it }?.let { idList ->
                val users = userDao.findAllByRolesIn(listOf(id))
                userService.removeUserFromRoleBatch(users.map { it.userId }, id)
                userService.addUserToRoleBatch(idList, id)
            }
            return true
        }
    }

    override fun listUserByRoleId(id: String): Set<UserResult> {
        val result = mutableSetOf<UserResult>()
        userDao.findAllByRolesIn(listOf(id)).let { users ->
            for (user in users) {
                result.add(UserResult(user.userId, user.name))
            }
        }
        return result
    }

    override fun listRoleByProject(projectId: String, repoName: String?): List<Role> {
        logger.info("list  role params , projectId : [$projectId], repoName: [$repoName]")
        repoName?.let {
            return roleRepository.findByProjectIdAndRepoNameAndType(projectId, repoName, RoleType.REPO)
                .map { transfer(it) }
        }
        return roleRepository.findByTypeAndProjectIdAndAdminAndRoleIdNotIn(
            RoleType.PROJECT,
            projectId,
            false,
            listOf(PROJECT_MANAGE_ID, PROJECT_VIEWER_ID)
        ).map { transfer(it) }
    }

    override fun deleteRoleById(id: String): Boolean {
        logger.info("delete  role  id : [$id]")
        val role = roleRepository.findFirstById(id)
        if (role == null) {
            logger.warn("delete role [$id ] not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_ROLE_NOT_EXIST)
        } else {
            val users = listUserByRoleId(role.id!!)
            if (users.isNotEmpty()) {
                userService.removeUserFromRoleBatch(users.map { it.userId }, id)
            }
            roleRepository.deleteTRolesById(role.id)
        }
        return true
    }

    private fun transfer(tRole: TRole): Role {
        val userList = userDao.findAllByRolesIn(listOf(tRole.id!!))
        val users = userList.map { it.userId }
        return Role(
            id = tRole.id,
            roleId = tRole.roleId,
            type = tRole.type,
            name = tRole.name,
            projectId = tRole.projectId,
            admin = tRole.admin,
            users = users,
            description = tRole.description
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RoleServiceImpl::class.java)
    }
}
