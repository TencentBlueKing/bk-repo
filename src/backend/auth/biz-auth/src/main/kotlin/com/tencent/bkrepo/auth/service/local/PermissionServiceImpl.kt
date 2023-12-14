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

import com.tencent.bkrepo.auth.constant.PROJECT_MANAGE_ID
import com.tencent.bkrepo.auth.constant.PROJECT_VIEWER_ID
import com.tencent.bkrepo.auth.dao.PermissionDao
import com.tencent.bkrepo.auth.dao.UserDao
import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.model.TPermission
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.READ
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.enums.ResourceType.REPO
import com.tencent.bkrepo.auth.pojo.enums.ResourceType.ENDPOINT
import com.tencent.bkrepo.auth.pojo.enums.ResourceType.PROJECT
import com.tencent.bkrepo.auth.pojo.enums.ResourceType.SYSTEM
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.permission.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.permission.Permission
import com.tencent.bkrepo.auth.pojo.permission.UpdatePermissionActionRequest
import com.tencent.bkrepo.auth.pojo.permission.UpdatePermissionPathRequest
import com.tencent.bkrepo.auth.pojo.permission.UpdatePermissionRepoRequest
import com.tencent.bkrepo.auth.pojo.permission.UpdatePermissionRoleRequest
import com.tencent.bkrepo.auth.pojo.permission.UpdatePermissionUserRequest
import com.tencent.bkrepo.auth.dao.repository.AccountRepository
import com.tencent.bkrepo.auth.dao.repository.RoleRepository
import com.tencent.bkrepo.auth.service.PermissionService
import com.tencent.bkrepo.auth.util.RequestUtil
import com.tencent.bkrepo.auth.util.request.PermRequestUtil
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.LoggerFactory
import javax.servlet.http.HttpServletRequest

open class PermissionServiceImpl constructor(
    private val roleRepository: RoleRepository,
    private val account: AccountRepository,
    private val permissionDao: PermissionDao,
    userDao: UserDao,
    val repoClient: RepositoryClient,
    val projectClient: ProjectClient
) : PermissionService, AbstractServiceImpl(userDao, roleRepository) {

    override fun deletePermission(id: String): Boolean {
        logger.info("delete  permission  repoName: [$id]")
        return permissionDao.deleteById(id)
    }

    override fun listPermission(projectId: String, repoName: String?): List<Permission> {
        logger.debug("list  permission  projectId: [$projectId], repoName: [$repoName]")
        repoName?.let {
            return permissionDao.findByResourceTypeAndProjectIdAndRepos(REPO, projectId, repoName)
                .map { PermRequestUtil.convToPermission(it) }
        }
        return permissionDao.findByResourceTypeAndProjectId(PROJECT, projectId)
            .map { PermRequestUtil.convToPermission(it) }
    }


    override fun createPermission(request: CreatePermissionRequest): Boolean {
        logger.info("create  permission request : [$request]")
        // todo check request
        val permission = permissionDao.findOneByPermNameAndProjectIdAndResourceType(
            request.permName, request.projectId, request.resourceType
        )
        permission?.let {
            logger.warn("create permission  [$request] is exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_DUP_PERMNAME)
        }
        val result = permissionDao.insert(PermRequestUtil.convToTPermission(request))
        if (result.id != null) return true
        return false
    }

    override fun updateIncludePath(request: UpdatePermissionPathRequest): Boolean {
        logger.info("update include path request :[$request]")
        with(request) {
            checkPermissionExist(permissionId)
            return permissionDao.updateById(permissionId, TPermission::includePattern.name, path)
        }
    }

    override fun updateExcludePath(request: UpdatePermissionPathRequest): Boolean {
        logger.info("update exclude path request :[$request]")
        with(request) {
            checkPermissionExist(permissionId)
            return permissionDao.updateById(permissionId, TPermission::excludePattern.name, path)
        }
    }

    override fun updateRepoPermission(request: UpdatePermissionRepoRequest): Boolean {
        logger.info("update repo permission request :  [$request]")
        with(request) {
            checkPermissionExist(permissionId)
            return permissionDao.updateById(permissionId, TPermission::repos.name, repos)
        }
    }

    override fun updatePermissionUser(request: UpdatePermissionUserRequest): Boolean {
        logger.info("update permission user request:[$request]")
        with(request) {
            val createAdminRequest = RequestUtil.buildProjectAdminRequest(projectId!!)
            val createUserRequest = RequestUtil.buildProjectViewerRequest(projectId)
            val adminRoleId = createRoleCommon(createAdminRequest)
            val commonRoleId = createRoleCommon(createUserRequest)
            when (permissionId) {
                PROJECT_MANAGE_ID -> {
                    val adminUsers = getProjectAdminUser(projectId)
                    val addRoleUserList = userId.filter { !adminUsers.contains(it) }
                    val removeRoleUserList = adminUsers.filter { !userId.contains(it) }

                    addUserToRoleBatchCommon(addRoleUserList, adminRoleId!!)
                    removeUserFromRoleBatchCommon(removeRoleUserList, adminRoleId)
                    removeUserFromRoleBatchCommon(addRoleUserList, commonRoleId!!)
                }
                PROJECT_VIEWER_ID -> {
                    val commonUsers = getProjectCommonUser(projectId)
                    val addRoleUserList = userId.filter { !commonUsers.contains(it) }
                    val removeRoleUserList = commonUsers.filter { !userId.contains(it) }

                    addUserToRoleBatchCommon(addRoleUserList, commonRoleId!!)
                    removeUserFromRoleBatchCommon(removeRoleUserList, commonRoleId)
                    removeUserFromRoleBatchCommon(addRoleUserList, adminRoleId!!)
                }
                else -> {
                    checkPermissionExist(permissionId)
                    return permissionDao.updateById(permissionId, TPermission::users.name, userId)
                }
            }
            return true
        }
    }

    override fun updatePermissionRole(request: UpdatePermissionRoleRequest): Boolean {
        logger.info("update permission role request:[$request]")
        with(request) {
            checkPermissionExist(permissionId)
            return permissionDao.updateById(permissionId, TPermission::roles.name, rId)
        }
    }

    override fun updatePermissionAction(request: UpdatePermissionActionRequest): Boolean {
        logger.info("update permission action request:[$request]")
        with(request) {
            checkPermissionExist(permissionId)
            return permissionDao.updateById(permissionId, TPermission::actions.name, actions)
        }
    }

    override fun checkPermission(request: CheckPermissionRequest): Boolean {
        logger.debug("check permission  request : [$request] ")

        if (request.uid == ANONYMOUS_USER) return false

        val user = userDao.findFirstByUserId(request.uid) ?: run {
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }

        // check user locked
        if (user.locked) return false
        // check user admin permission
        if (user.admin) return true
        // check role project admin
        if (checkProjectAdmin(request, user.roles)) return true
        // check role project user
        if (checkProjectUser(request, user.roles)) return true
        // check role repo admin
        if (checkRepoAdmin(request, user.roles)) return true
        // check repo action
        return checkAction(request, user.roles)
    }

    private fun checkProjectAdmin(request: CheckPermissionRequest, roles: List<String>): Boolean {
        var queryRoles = emptyList<String>()
        if (roles.isNotEmpty() && request.projectId != null) {
            queryRoles = roles.filter { !it.isNullOrEmpty() }.toList()
        }
        if (queryRoles.isEmpty()) return false
        val result = roleRepository.findByProjectIdAndTypeAndAdminAndIdIn(
            projectId = request.projectId!!, type = RoleType.PROJECT, admin = true, ids = queryRoles
        )
        if (result.isNotEmpty()) return true
        return false
    }

    private fun checkProjectUser(request: CheckPermissionRequest, roles: List<String>): Boolean {
        var queryRoles = emptyList<String>()
        if (roles.isNotEmpty() && request.projectId != null) {
            queryRoles = roles.filter { !it.isNullOrEmpty() }.toList()
        }
        if (queryRoles.isEmpty()) return false
        val isProjectUser = roleRepository.findByIdIn(queryRoles)
            .any { tRole -> tRole.projectId == request.projectId && tRole.roleId == PROJECT_VIEWER_ID }
        if (isProjectUser && request.action == READ.toString()) return true
        return false
    }

    private fun checkRepoAdmin(request: CheckPermissionRequest, roles: List<String>): Boolean {
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

    private fun checkAction(request: CheckPermissionRequest, roles: List<String>): Boolean {
        with(request) {
            val result = permissionDao.getPermissionByAction(projectId, repoName, uid, action, resourceType, roles)
            if (result.isEmpty()) return false
            // result is not empty and path is null
            if (path == null) return true
            result.forEach {
                if (checkIncludePattern(it.includePattern, path!!)) return true
                if (!checkExcludePattern(it.excludePattern, path!!)) return false
            }
        }
        return false
    }

    private fun checkIncludePattern(patternList: List<String>, path: String): Boolean {
        if (patternList.isEmpty()) return true
        return patternList.any { path.contains(it) }
    }

    private fun checkExcludePattern(patternList: List<String>, path: String): Boolean {
        if (patternList.isEmpty()) return true
        return patternList.all { !path.contains(it) }
    }

    override fun listPermissionProject(userId: String): List<String> {
        logger.debug("list permission project request : $userId ")
        if (userId.isEmpty()) return emptyList()
        val user = userDao.findFirstByUserId(userId) ?: run {
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }
        // 用户为系统管理员
        if (user.admin) return projectClient.listProject().data?.map { it.name } ?: emptyList()


        val projectList = mutableListOf<String>()

        // 非管理员用户关联权限
        projectList.addAll(getNoAdminUserProject(userId))

        // 取用户关联角色关联的项目
        if (user.roles.isNotEmpty()) projectList.addAll(getUserCommonRoleProject(user.roles))

        if (user.roles.isEmpty()) return projectList.distinct()

        val noAdminRole = mutableListOf<String>()

        // 管理员角色关联权限
        val roleList = roleRepository.findByIdIn(user.roles)
        roleList.forEach {
            if (it.admin) {
                projectList.add(it.projectId)
            } else {
                noAdminRole.add(it.id!!)
            }
        }

        // 非管理员角色关联权限
        projectList.addAll(getNoAdminRoleProject(noAdminRole))

        return projectList.distinct()
    }

    override fun listPermissionRepo(projectId: String, userId: String, appId: String?): List<String> {
        logger.debug("list repo permission request : [$projectId, $userId] ")
        val user = userDao.findFirstByUserId(userId) ?: run {
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }

        // 用户为系统管理员
        if (user.admin) return getAllRepoByProjectId(projectId)

        // 用户为项目管理员
        if (isUserLocalProjectAdmin(userId, projectId)) return getAllRepoByProjectId(projectId)

        if (isUserLocalProjectUser(user.roles, projectId)) return getAllRepoByProjectId(projectId)

        val repoList = mutableListOf<String>()

        // 非管理员用户关联权限
        repoList.addAll(getNoAdminUserRepo(projectId, userId))

        if (user.roles.isEmpty()) return repoList.distinct()

        val noAdminRole = mutableListOf<String>()

        // 仓库管理员角色关联权限
        val roleList = roleRepository.findByProjectIdAndTypeAndAdminAndIdIn(
            projectId = projectId, type = RoleType.REPO, admin = true, ids = user.roles
        )
        roleList.forEach {
            if (it.admin && it.repoName != null) {
                repoList.add(it.repoName)
            } else {
                noAdminRole.add(it.id!!)
            }
        }

        // 非仓库管理员角色关联权限
        repoList.addAll(getNoAdminRoleRepo(projectId, noAdminRole))

        return repoList.distinct()
    }

    private fun isUserLocalProjectUser(roleIds: List<String>, projectId: String): Boolean {
        return roleRepository.findAllById(roleIds)
            .any { role -> role.projectId == projectId && role.roleId == PROJECT_VIEWER_ID }
    }

    fun getAllRepoByProjectId(projectId: String): List<String> {
        return repoClient.listRepo(projectId).data?.map { it.name } ?: emptyList()
    }

    private fun getNoAdminUserProject(userId: String): List<String> {
        val projectList = mutableListOf<String>()
        permissionDao.findByUsers(userId).forEach {
            if (it.actions.isNotEmpty() && it.projectId != null) {
                projectList.add(it.projectId!!)
            }
        }
        return projectList
    }

    private fun getUserCommonRoleProject(roles: List<String>): List<String> {
        val projectList = mutableListOf<String>()
        roleRepository.findByIdIn(roles).forEach {
            if (it.projectId.isNotEmpty() && it.roleId == PROJECT_VIEWER_ID) {
                projectList.add(it.projectId)
            }
        }
        return projectList
    }

    private fun getNoAdminRoleProject(roles: List<String>): List<String> {
        val project = mutableListOf<String>()
        if (roles.isNotEmpty()) {
            permissionDao.findByRolesIn(roles).forEach {
                if (it.actions.isNotEmpty() && it.projectId != null) {
                    project.add(it.projectId!!)
                }
            }
        }
        return project
    }

    private fun getNoAdminUserRepo(projectId: String, userId: String): List<String> {
        val repoList = mutableListOf<String>()
        permissionDao.findByProjectIdAndUsers(projectId, userId).forEach {
            if (it.actions.isNotEmpty() && it.repos.isNotEmpty()) {
                repoList.addAll(it.repos)
            }
        }
        return repoList
    }

    private fun getNoAdminRoleRepo(project: String, role: List<String>): List<String> {
        val repoList = mutableListOf<String>()
        if (role.isNotEmpty()) {
            permissionDao.findByProjectIdAndRolesIn(project, role).forEach {
                if (it.actions.isNotEmpty() && it.repos.isNotEmpty()) {
                    repoList.addAll(it.repos)
                }
            }
        }
        return repoList
    }

    private fun checkPermissionExist(permissionId: String) {
        permissionDao.findFirstById(permissionId) ?: run {
            logger.warn("update permission repos [$permissionId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_PERMISSION_NOT_EXIST)
        }
    }

    override fun checkPlatformPermission(request: CheckPermissionRequest): Boolean {
        with(request) {
            if (appId == null) return true
            val platform = account.findOneByAppId(appId!!) ?: run {
                logger.info("can not find platform [$appId]")
                return false
            }
            val httpRequest: HttpServletRequest = HttpContextHolder.getRequest()
            if (platform.scope == null) return true
            var reqResourceType = ResourceType.lookup(resourceType)
            // if scope contains endpoint reqResourceType is endpoint
            if (platform.scope!!.contains(ENDPOINT)) reqResourceType = ENDPOINT
            if (!platform.scope!!.contains(reqResourceType)) return false
            return when (reqResourceType) {
                SYSTEM -> true
                PROJECT -> checkPlatformProject(projectId, platform.scopeDesc)
                ENDPOINT -> checkPlatformProject(httpRequest.requestURI, platform.scopeDesc)
                else -> false
            }
        }
    }

    override fun listProjectBuiltinPermission(projectId: String): List<Permission> {
        val projectManager = buildBuiltInPermission(
            permissionId = PROJECT_MANAGE_ID,
            projectId = projectId,
            permissionName = "project_manage_permission",
            userList = getProjectAdminUser(projectId)
        )
        val projectViewer = buildBuiltInPermission(
            permissionId = PROJECT_VIEWER_ID,
            projectId = projectId,
            permissionName = "project_view_permission",
            userList = getProjectCommonUser(projectId)
        )
        return listOf(projectManager, projectViewer)
    }


    companion object {
        private val logger = LoggerFactory.getLogger(PermissionServiceImpl::class.java)
    }
}
