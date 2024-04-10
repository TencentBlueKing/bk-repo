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

package com.tencent.bkrepo.auth.helper

import com.tencent.bkrepo.auth.constant.AUTH_ADMIN
import com.tencent.bkrepo.auth.constant.PROJECT_VIEWER_ID
import com.tencent.bkrepo.auth.dao.PermissionDao
import com.tencent.bkrepo.auth.dao.PersonalPathDao
import com.tencent.bkrepo.auth.dao.UserDao
import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.pojo.account.ScopeRule
import com.tencent.bkrepo.auth.pojo.enums.ResourceType.REPO
import com.tencent.bkrepo.auth.pojo.enums.ResourceType.NODE
import com.tencent.bkrepo.auth.pojo.enums.ResourceType.PROJECT
import com.tencent.bkrepo.auth.pojo.enums.ResourceType.ENDPOINT
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.pojo.permission.Permission
import com.tencent.bkrepo.auth.dao.repository.RoleRepository
import com.tencent.bkrepo.auth.model.TPermission
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.READ
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.MANAGE
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.auth.util.scope.RuleUtil
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.security.util.SecurityUtils
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class PermissionHelper constructor(
    private val userDao: UserDao,
    private val roleRepository: RoleRepository,
    private val permissionDao: PermissionDao,
    private val personalPathDao: PersonalPathDao
) {
    // check user is existed
    private fun checkUserExistBatch(idList: List<String>) {
        idList.forEach {
            userDao.findFirstByUserId(it) ?: run {
                logger.warn(" user not  exist.")
                throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
            }
        }
    }

    // check role is existed
    private fun checkRoleExist(roleId: String) {
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

    fun checkPermissionExist(permissionId: String) {
        permissionDao.findFirstById(permissionId) ?: run {
            logger.warn("update permission repos [$permissionId] not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_PERMISSION_NOT_EXIST)
        }
    }

    fun checkPlatformProject(projectId: String?, scopeDesc: List<ScopeRule>?): Boolean {
        if (scopeDesc == null || projectId == null) return false

        scopeDesc.forEach {
            when (it.field) {
                PROJECT.name -> {
                    if (RuleUtil.check(it, projectId, PROJECT)) return true
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
                ENDPOINT.name -> {
                    if (RuleUtil.check(it, endpoint, ENDPOINT)) return true
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
            resourceType = PROJECT.name,
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
        return resourceType == NODE.name || resourceType == REPO.name
    }

    fun checkProjectReadAction(request: CheckPermissionRequest, isProjectUser: Boolean): Boolean {
        return request.projectId != null && request.action == READ.name && isProjectUser
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

    fun checkRepoReadAction(request: CheckPermissionRequest, roles: List<String>): Boolean {
        with(request) {
            return resourceType == REPO.name && action == READ.name &&
                    permissionDao.listPermissionInRepo(projectId!!, repoName!!, uid, roles).isNotEmpty()
        }
    }

    fun getOnePermission(
        projectId: String,
        repoName: String,
        permName: String,
        actions: Set<PermissionAction>
    ): TPermission {
        permissionDao.findOneByPermName(projectId, repoName, permName, REPO.name) ?: run {
            val request = TPermission(
                projectId = projectId,
                repos = listOf(repoName),
                permName = permName,
                actions = actions.map { it.name },
                resourceType = REPO.name,
                createAt = LocalDateTime.now(),
                updateAt = LocalDateTime.now(),
                createBy = AUTH_ADMIN,
                updatedBy = AUTH_ADMIN
            )
            logger.info("permission not exist, create [$request]")
            permissionDao.insert(request)
        }
        return permissionDao.findOneByPermName(projectId, repoName, permName, REPO.name)!!
    }

    fun getNoAdminUserProject(userId: String): List<String> {
        val projectList = mutableListOf<String>()
        permissionDao.listByUserId(userId).forEach {
            if (it.actions.isNotEmpty() && it.projectId != null) {
                projectList.add(it.projectId!!)
            }
        }
        return projectList
    }

    fun getNoAdminRoleRepo(project: String, role: List<String>): List<String> {
        val repoList = mutableListOf<String>()
        permissionDao.listByProjectAndRoles(project, role).forEach {
            if (it.actions.isNotEmpty() && it.repos.isNotEmpty()) {
                repoList.addAll(it.repos)
            }
        }
        return repoList
    }

    fun getNoAdminUserRepo(projectId: String, userId: String): List<String> {
        val repoList = mutableListOf<String>()
        permissionDao.listByProjectIdAndUsers(projectId, userId).forEach {
            if (it.actions.isNotEmpty() && it.repos.isNotEmpty()) {
                repoList.addAll(it.repos)
            }
        }
        return repoList
    }

    fun getUserCommonRoleProject(roles: List<String>): List<String> {
        val projectList = mutableListOf<String>()
        roleRepository.findByIdIn(roles).forEach {
            if (it.projectId.isNotEmpty() && it.roleId == PROJECT_VIEWER_ID) {
                projectList.add(it.projectId)
            }
        }
        return projectList
    }

    fun getNoAdminRoleProject(roles: List<String>): List<String> {
        val projectList = mutableListOf<String>()
        if (roles.isNotEmpty()) {
            permissionDao.listByRole(roles).forEach {
                if (it.actions.isNotEmpty() && it.projectId != null) {
                    projectList.add(it.projectId!!)
                }
            }
        }
        return projectList
    }

    private fun checkIncludePatternAction(
        patternList: List<String>,
        path: String,
        actions: List<String>,
        checkAction: String
    ): Boolean {
        patternList.forEach {
            if (path.startsWith(it) && (actions.contains(MANAGE.name) || actions.contains(checkAction))) return true
        }
        return false
    }

    private fun checkExcludePatternAction(
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

    fun updatePermissionById(id: String, key: String, value: Any): Boolean {
        return permissionDao.updateById(id, key, value)
    }

    fun checkNodeAction(request: CheckPermissionRequest, userRoles: List<String>?, isProjectUser: Boolean): Boolean {
        with(request) {
            var roles = userRoles
            if (resourceType != NODE.name || path == null) return false
            if (roles == null) {
                val user = userDao.findFirstByUserId(uid) ?: run {
                    throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
                }
                roles = user.roles
            }
            val result = permissionDao.listInPermission(projectId!!, repoName!!, uid, resourceType, roles)
            result.forEach {
                if (checkIncludePatternAction(it.includePattern, path!!, it.actions, action)) return true

                if (checkExcludePatternAction(it.excludePattern, path!!, it.actions, action)) return false
            }

            val noPermissionResult = permissionDao.listNoPermission(projectId!!, repoName!!, uid, resourceType, roles)
            noPermissionResult.forEach {
                if (checkIncludePatternAction(it.includePattern, path!!, it.actions, action)) return false
            }
            val personalPathCheck = checkPersonalPath(uid, projectId!!, repoName!!, path!!)
            if (personalPathCheck != null) return personalPathCheck
        }
        return isProjectUser
    }

    private fun checkPersonalPath(userId: String, projectId: String, repoName: String, path: String): Boolean? {
        // check personal path
        val personalPath = personalPathDao.findOneByProjectAndRepo(userId, projectId, repoName)
        if (personalPath != null && path.startsWith(personalPath.fullPath)) return true

        // check personal exclude path
        val personalExcludePath = personalPathDao.listByProjectAndRepoAndExcludeUser(userId, projectId, repoName)
        personalExcludePath.forEach {
            if (path.startsWith(it.fullPath)) return false
        }
        return null
    }

    fun isUserLocalProjectAdmin(userId: String, projectId: String?): Boolean {
        if (projectId == null) return false
        val roleIdArray = mutableListOf<String>()
        roleRepository.findByTypeAndProjectIdAndAdmin(RoleType.PROJECT, projectId, true).forEach {
            roleIdArray.add(it.id!!)
        }
        userDao.findFirstByUserIdAndRolesIn(userId, roleIdArray) ?: return false
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

    companion object {
        private val logger = LoggerFactory.getLogger(PermissionHelper::class.java)
    }
}
