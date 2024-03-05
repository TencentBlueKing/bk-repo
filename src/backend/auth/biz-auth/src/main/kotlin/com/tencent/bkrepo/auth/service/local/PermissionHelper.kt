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

import com.tencent.bkrepo.auth.constant.PROJECT_VIEWER_ID
import com.tencent.bkrepo.auth.dao.UserDao
import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.model.TRole
import com.tencent.bkrepo.auth.model.TUser
import com.tencent.bkrepo.auth.pojo.account.ScopeRule
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.pojo.permission.Permission
import com.tencent.bkrepo.auth.pojo.role.CreateRoleRequest
import com.tencent.bkrepo.auth.dao.repository.RoleRepository
import com.tencent.bkrepo.auth.model.TPermission
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.auth.util.DataDigestUtils
import com.tencent.bkrepo.auth.util.IDUtil
import com.tencent.bkrepo.auth.util.RequestUtil
import com.tencent.bkrepo.auth.util.request.RoleRequestUtil
import com.tencent.bkrepo.auth.util.scope.RuleUtil
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.security.util.SecurityUtils
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class PermissionHelper constructor(
    val userDao: UserDao,
    val roleRepository: RoleRepository
) {

    fun checkUserExist(userId: String) {
        userDao.findFirstByUserId(userId) ?: run {
            logger.warn("user [$userId] not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }
    }

    fun checkUserRoleBind(userId: String, roleId: String): Boolean {
        userDao.findFirstByUserIdAndRoles(userId, roleId) ?: run {
            logger.warn("user [$userId,$roleId]  not exist.")
            return false
        }
        return true
    }

    // check user is existed
    private fun checkUserExistBatch(idList: List<String>) {
        idList.forEach {
            userDao.findFirstByUserId(it) ?: run {
                logger.warn(" user not  exist.")
                throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
            }
        }
    }

    private fun checkOrCreateUser(userIdList: List<String>) {
        userIdList.forEach {
            userDao.findFirstByUserId(it) ?: run {
                if (it != ANONYMOUS_USER) {
                    val user = TUser(
                        userId = it,
                        name = it,
                        pwd = randomPassWord()
                    )
                    userDao.insert(user)
                }
            }
        }
    }

    fun randomPassWord(): String {
        return DataDigestUtils.md5FromStr(IDUtil.genRandomId())
    }

    // check role is existed
    fun checkRoleExist(roleId: String) {
        val role = roleRepository.findFirstById(roleId)
        role ?: run {
            logger.warn("role not  exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_ROLE_NOT_EXIST)
        }
    }

    fun checkRepoAdmin(request: CheckPermissionRequest, roles: List<String>): Boolean {
        // check role repo admin
        var queryRoles = emptyList<String>()
        if (roles.isNotEmpty() && request.projectId != null && request.repoName != null) {
            queryRoles = roles.filter { !it.isNullOrEmpty() }.toList()
        }
        if (queryRoles.isEmpty()) return false

        val result = roleRepository.findByProjectIdAndTypeAndAdminAndRepoNameAndIdIn(
            projectId = request.projectId!!,
            type = RoleType.REPO,
            repoName = request.repoName!!,
            admin = true,
            ids = queryRoles
        )
        if (result.isNotEmpty()) return true
        return false
    }

    fun isUserLocalAdmin(userId: String): Boolean {
        val user = userDao.findFirstByUserId(userId) ?: run {
            return false
        }
        return user.admin
    }

    fun isUserLocalProjectAdmin(userId: String, projectId: String): Boolean {
        val roleIdArray = mutableListOf<String>()
        roleRepository.findByTypeAndProjectIdAndAdmin(RoleType.PROJECT, projectId, true).forEach {
            roleIdArray.add(it.id!!)
        }
        userDao.findFirstByUserIdAndRolesIn(userId, roleIdArray) ?: run {
            return false
        }
        return true
    }

    fun isUserLocalProjectUser(userId: String, projectId: String): Boolean {
        val user = userDao.findFirstByUserId(userId) ?: run {
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }
        val roles = user.roles
        return roleRepository.findAllById(roles)
            .any { role -> role.projectId == projectId && role.roleId == PROJECT_VIEWER_ID }
    }

    fun filterRepos(repos: List<String>, originRepoNames: List<String>): List<String> {
        (repos as MutableList).retainAll(originRepoNames)
        return repos
    }

    fun checkPlatformProject(projectId: String?, scopeDesc: List<ScopeRule>?): Boolean {
        if (scopeDesc == null || projectId == null) return false

        scopeDesc.forEach {
            when (it.field) {
                ResourceType.PROJECT.name -> {
                    if (RuleUtil.check(it, projectId, ResourceType.PROJECT)) return true
                }
                else -> return false
            }
        }
        return false
    }

    fun checkPlatformEndPoint(endpoint: String?, scopeDesc: List<ScopeRule>?): Boolean {
        if (scopeDesc == null || endpoint == null) return false

        scopeDesc.forEach {
            when (it.field) {
                ResourceType.ENDPOINT.name -> {
                    if (RuleUtil.check(it, endpoint, ResourceType.ENDPOINT)) return true
                }
                else -> return false
            }
        }
        return false
    }

    fun getProjectAdminUser(projectId: String): List<String> {
        val roleIdArray = mutableListOf<String>()
        roleRepository.findByTypeAndProjectIdAndAdmin(RoleType.PROJECT, projectId, true).forEach {
            roleIdArray.add(it.id!!)
        }
        return userDao.findAllByRolesIn(roleIdArray).map { it.userId }.distinct()
    }

    // 获取此项目一般用户
    fun getProjectCommonUser(projectId: String): List<String> {
        val roleIdArray = mutableListOf<String>()
        val role = roleRepository.findFirstByRoleIdAndProjectId(PROJECT_VIEWER_ID, projectId)
        if (role != null) role.id?.let { roleIdArray.add(it) }
        return userDao.findAllByRolesIn(roleIdArray).map { it.userId }.distinct()
    }

    fun createRoleCommon(request: CreateRoleRequest): String? {
        logger.info("create role request:[$request] ")
        val role: TRole? = if (request.type == RoleType.REPO) {
            roleRepository.findFirstByRoleIdAndProjectIdAndRepoName(
                request.roleId!!,
                request.projectId,
                request.repoName!!
            )
        } else {
            roleRepository.findFirstByProjectIdAndTypeAndName(
                projectId = request.projectId,
                type = RoleType.PROJECT,
                name = request.name
            )
        }

        role?.let {
            logger.warn("create role [${request.roleId} , ${request.projectId} ] is exist.")
            return role.id
        }

        val roleId = when (request.type) {
            RoleType.REPO -> request.roleId!!
            RoleType.PROJECT -> findUsableProjectTypeRoleId(request.roleId, request.projectId)
        }

        val result = roleRepository.insert(RoleRequestUtil.conv2TRole(roleId, request))
        return result.id
    }

    fun addProjectUserAdmin(projectId: String, idList: List<String>) {
        val createRoleRequest = RequestUtil.buildProjectAdminRequest(projectId)
        val roleId = createRoleCommon(createRoleRequest)
        addUserToRoleBatchCommon(idList, roleId!!)
    }

    fun addUserToRoleBatchCommon(userIdList: List<String>, roleId: String): Boolean {
        logger.info("add user to role batch userId : [$userIdList], roleId : [$roleId]")
        checkOrCreateUser(userIdList)
        checkRoleExist(roleId)
        userDao.addRoleToUsers(userIdList, roleId)
        return true
    }

    fun removeUserFromRoleBatchCommon(userIdList: List<String>, roleId: String): Boolean {
        logger.info("remove user from role batch userId : [$userIdList], roleId : [$roleId]")
        checkUserExistBatch(userIdList)
        checkRoleExist(roleId)
        userDao.removeRoleFromUsers(userIdList, roleId)
        return true
    }

    fun buildBuiltInPermission(
        permissionId: String,
        projectId: String,
        permissionName: String,
        userList: List<String>
    ): Permission {
        return Permission(
            id = permissionId,
            resourceType = ResourceType.PROJECT.name,
            projectId = projectId,
            permName = permissionName,
            users = userList,
            createBy = SecurityUtils.getUserId(),
            updatedBy = SecurityUtils.getUserId(),
            createAt = LocalDateTime.now(),
            updateAt = LocalDateTime.now()
        )
    }

    fun isRepoOrNodePermission(resourceType: String): Boolean {
        return resourceType == ResourceType.NODE.name || resourceType == ResourceType.REPO.name
    }

    fun checkProjectAdmin(request: CheckPermissionRequest): Boolean {
        if (request.projectId == null) return false
        return isUserLocalProjectAdmin(request.uid, request.projectId!!)
    }

    fun checkProjectReadAction(request: CheckPermissionRequest, isProjectUser: Boolean): Boolean {
        return request.projectId != null && request.action == PermissionAction.READ.name && isProjectUser
    }

    private fun findUsableProjectTypeRoleId(roleId: String?, projectId: String): String {
        var tempRoleId = roleId ?: "${projectId}_role_${IDUtil.shortUUID()}"
        while (true) {
            val role = roleRepository.findFirstByRoleIdAndProjectId(tempRoleId, projectId)
            if (role == null) return tempRoleId else tempRoleId = "${projectId}_role_${IDUtil.shortUUID()}"
        }
    }

    fun getNoPermissionPathFromConfig(
        userId: String,
        roles: List<String>,
        config: List<TPermission>
    ): List<String> {
        val excludePath = mutableListOf<String>()
        val includePath = mutableListOf<String>()
        config.forEach {
            if (it.users.contains(userId)) {
                if (it.excludePattern.isNotEmpty()) {
                    excludePath.addAll(it.excludePattern)
                }
                if (it.includePattern.isNotEmpty()) {
                    includePath.addAll(it.includePattern)
                }
            } else {
                if (it.includePattern.isNotEmpty()) {
                    excludePath.addAll(it.includePattern)
                }
            }

            val interRole = it.roles.intersect(roles.toSet())
            if (interRole.isNotEmpty()) {
                if (it.excludePattern.isNotEmpty()) {
                    excludePath.addAll(it.excludePattern)
                }
                if (it.includePattern.isNotEmpty()) {
                    includePath.addAll(it.includePattern)
                }
            } else {
                if (it.includePattern.isNotEmpty()) {
                    excludePath.addAll(it.includePattern)
                }
            }
        }
        val filterPath = includePath.distinct()
        return excludePath.distinct().filter { !filterPath.contains(it) }
    }


    fun checkIncludePatternAction(
        patternList: List<String>,
        path: String,
        actions: List<String>,
        checkAction: String
    ): Boolean {
        patternList.forEach {
            if (path.startsWith(it) && (actions.contains(PermissionAction.MANAGE.name) || actions.contains(checkAction))) return true
        }
        return false
    }

    fun checkExcludePatternAction(
        patternList: List<String>,
        path: String,
        actions: List<String>,
        checkAction: String
    ): Boolean {
        patternList.forEach {
            if (path.startsWith(it) && actions.contains(checkAction)) return true
        }
        return false
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PermissionHelper::class.java)
    }
}
