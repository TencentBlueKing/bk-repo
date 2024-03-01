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

import com.tencent.bkrepo.auth.constant.AUTH_BUILTIN_ADMIN
import com.tencent.bkrepo.auth.constant.AUTH_BUILTIN_USER
import com.tencent.bkrepo.auth.constant.AUTH_BUILTIN_VIEWER
import com.tencent.bkrepo.auth.constant.PROJECT_MANAGE_ID
import com.tencent.bkrepo.auth.constant.PROJECT_VIEWER_ID
import com.tencent.bkrepo.auth.constant.AUTH_ADMIN
import com.tencent.bkrepo.auth.dao.PermissionDao
import com.tencent.bkrepo.auth.dao.UserDao
import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.model.TPermission
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.READ
import com.tencent.bkrepo.auth.pojo.enums.ResourceType.REPO
import com.tencent.bkrepo.auth.pojo.enums.ResourceType.NODE
import com.tencent.bkrepo.auth.pojo.enums.ResourceType.PROJECT
import com.tencent.bkrepo.auth.pojo.enums.ResourceType.SYSTEM
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.dao.repository.AccountRepository
import com.tencent.bkrepo.auth.dao.repository.RoleRepository
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.WRITE
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.DELETE
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.MANAGE
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.UPDATE
import com.tencent.bkrepo.auth.pojo.permission.Permission
import com.tencent.bkrepo.auth.pojo.permission.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.permission.UpdatePermissionRepoRequest
import com.tencent.bkrepo.auth.pojo.permission.UpdatePermissionDeployInRepoRequest
import com.tencent.bkrepo.auth.pojo.permission.UpdatePermissionUserRequest
import com.tencent.bkrepo.auth.service.PermissionService
import com.tencent.bkrepo.auth.util.RequestUtil
import com.tencent.bkrepo.auth.util.request.PermRequestUtil
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

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
        permissionDao.deleteById(id)
        return true
    }

    override fun listPermission(projectId: String, repoName: String?, resourceType: String?): List<Permission> {
        logger.debug("list  permission  projectId: [$projectId], repoName: [$repoName]")
        resourceType?.let {
            repoName?.let {
                return permissionDao.listByResourceAndRepo(resourceType, projectId, repoName).map {
                    PermRequestUtil.convToPermission(it)
                }
            }
            return permissionDao.listByResourceAndProject(resourceType, projectId).map {
                PermRequestUtil.convToPermission(it)
            }
        }
        repoName?.let {
            return permissionDao.listByResourceAndRepo(REPO.name, projectId, repoName).map {
                PermRequestUtil.convToPermission(it)
            }
        }
        return permissionDao.listByResourceAndProject(PROJECT.name, projectId).map {
            PermRequestUtil.convToPermission(it)
        }
    }

    override fun listBuiltinPermission(projectId: String, repoName: String): List<Permission> {
        logger.debug("list  builtin permission  projectId: [$projectId], repoName: [$repoName]")
        val repoAdmin = getOnePermission(projectId, repoName, AUTH_BUILTIN_ADMIN, setOf(MANAGE))
        val repoUser = getOnePermission(
            projectId = projectId,
            repoName = repoName,
            permName = AUTH_BUILTIN_USER,
            actions = setOf(WRITE, READ, DELETE, UPDATE)
        )
        val repoViewer = getOnePermission(projectId, repoName, AUTH_BUILTIN_VIEWER, setOf(READ))
        return listOf(repoAdmin, repoUser, repoViewer).map { PermRequestUtil.convToPermission(it) }
    }

    override fun createPermission(request: CreatePermissionRequest): Boolean {
        logger.info("create  permission request : [$request]")
        // todo check request
        val permission = permissionDao.listPermissionByProject(
            request.permName, request.projectId, request.resourceType.toString()
        )
        permission?.let {
            logger.warn("create permission  [$request] is exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_DUP_PERMNAME)
        }
        val result = permissionDao.insert(PermRequestUtil.convToTPermission(request))
        result.id?.let {
            return true
        }
        return false
    }

    override fun updateRepoPermission(request: UpdatePermissionRepoRequest): Boolean {
        logger.info("update repo permission request :  [$request]")
        with(request) {
            checkPermissionExist(permissionId)
            return updatePermissionById(permissionId, TPermission::repos.name, repos)
        }
    }

    override fun updatePermissionUser(request: UpdatePermissionUserRequest): Boolean {
        logger.info("update permission user request:[$request]")
        with(request) {
            // update project admin
            if (permissionId == PROJECT_MANAGE_ID) {
                val createAdminRequest = RequestUtil.buildProjectAdminRequest(projectId!!)
                val createUserRequest = RequestUtil.buildProjectViewerRequest(projectId)
                val adminRoleId = createRoleCommon(createAdminRequest)
                val commonRoleId = createRoleCommon(createUserRequest)
                val adminUsers = getProjectAdminUser(projectId)
                val addRoleUserList = userId.filter { !adminUsers.contains(it) }
                val removeRoleUserList = adminUsers.filter { !userId.contains(it) }

                addUserToRoleBatchCommon(addRoleUserList, adminRoleId!!)
                removeUserFromRoleBatchCommon(removeRoleUserList, adminRoleId)
                removeUserFromRoleBatchCommon(addRoleUserList, commonRoleId!!)
                return true
                //  update project common user
            } else if (permissionId == PROJECT_VIEWER_ID) {
                val createUserRequest = RequestUtil.buildProjectViewerRequest(projectId!!)
                val createAdminRequest = RequestUtil.buildProjectAdminRequest(projectId)
                val adminRoleId = createRoleCommon(createAdminRequest)
                val commonRoleId = createRoleCommon(createUserRequest)
                val commonUsers = getProjectCommonUser(projectId)
                val addRoleUserList = userId.filter { !commonUsers.contains(it) }
                val removeRoleUserList = commonUsers.filter { !userId.contains(it) }

                addUserToRoleBatchCommon(addRoleUserList, commonRoleId!!)
                removeUserFromRoleBatchCommon(removeRoleUserList, commonRoleId)
                removeUserFromRoleBatchCommon(addRoleUserList, adminRoleId!!)
                return true
            } else {
                checkPermissionExist(permissionId)
                return updatePermissionById(permissionId, TPermission::users.name, userId)
            }

        }
    }

    override fun checkPermission(request: CheckPermissionRequest): Boolean {
        logger.debug("check permission request : [$request] ")
        with(request) {
            if (uid == ANONYMOUS_USER) return false
            val user = userDao.findFirstByUserId(uid) ?: run {
                throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
            }
            // check user locked
            if (user.locked) return false
            // check user admin permission
            if (user.admin || checkProjectAdmin(request)) return true
            val roles = user.roles
            if (isRepoOrNodePermission(resourceType)) {
                // check role repo admin
                if (checkRepoAdmin(request, roles)) return true
                // check repo read action
                if (checkRepoReadAction(request, roles)) return true
                //  check project user
                val isProjectUser = isUserLocalProjectUser(roles, request.projectId!!)
                if (checkProjecReadAction(request, isProjectUser)) return true
                // check node action
                if (isNodeNeedLocalCheck(projectId!!, repoName!!) && checkNodeAction(request, roles, isProjectUser)) {
                    return true
                }
            }
        }
        return false
    }

    private fun isRepoOrNodePermission(resourceType: String): Boolean {
        return resourceType == NODE.name || resourceType == REPO.name
    }

    private fun checkRepoReadAction(request: CheckPermissionRequest, roles: List<String>): Boolean {
        with(request) {
            return resourceType == REPO.name && action == READ.name &&
                    permissionDao.listPermissionInRepo(projectId!!, repoName!!, uid, roles).isNotEmpty()
        }
    }

    private fun checkProjectAdmin(request: CheckPermissionRequest): Boolean {
        if (request.projectId == null) return false
        return isUserLocalProjectAdmin(request.uid, request.projectId!!)
    }

    private fun checkProjecReadAction(request: CheckPermissionRequest, isProjectUser: Boolean): Boolean {
        return request.projectId != null && request.action == READ.name && isProjectUser
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

    fun checkNodeAction(request: CheckPermissionRequest, roles: List<String>, isProjectUser: Boolean): Boolean {
        with(request) {
            if (resourceType != NODE.name) return false
            val result = permissionDao.listInPermission(projectId!!, repoName!!, uid, resourceType, roles)
            result.forEach {
                if (checkIncludePatternAction(it.includePattern, path!!, it.actions, action)) return true

                if (checkExcludePatternAction(it.excludePattern, path!!, it.actions, action)) return false
            }

            val noPermissionResult = permissionDao.listNoPermission(projectId!!, repoName!!, uid, resourceType, roles)
            noPermissionResult.forEach {
                if (checkIncludePatternAction(it.includePattern, path!!, it.actions, action)) return false
            }
        }
        return isProjectUser
    }

    fun isNodeNeedLocalCheck(projectId: String, repoName: String): Boolean {
        val projectPermission = permissionDao.listByResourceAndRepo(NODE.name, projectId, repoName)
        return projectPermission.isNotEmpty()
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

    override fun listPermissionProject(userId: String): List<String> {
        logger.debug("list permission project request : $userId ")
        if (userId.isEmpty()) return emptyList()
        val user = userDao.findFirstByUserId(userId) ?: run {
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }
        // 用户为系统管理员
        if (user.admin) {
            return projectClient.listProject().data?.map { it.name } ?: emptyList()
        }

        val projectList = mutableListOf<String>()

        // 非管理员用户关联权限
        projectList.addAll(getNoAdminUserProject(userId))

        // 取用户关联角色关联的项目
        if (user.roles.isNotEmpty()) projectList.addAll(getUserCommonRoleProject(user.roles))

        if (user.roles.isEmpty()) {
            return projectList.distinct()
        }

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

    override fun getPermission(permissionId: String): Permission? {
        val result = permissionDao.findFirstById(permissionId) ?: run {
            return null
        }
        return PermRequestUtil.convToPermission(result)
    }

    override fun listPermissionRepo(projectId: String, userId: String, appId: String?): List<String> {
        logger.debug("list repo permission request : [$projectId, $userId] ")
        val user = userDao.findFirstByUserId(userId) ?: run {
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }
        val roles = user.roles

        // 用户为系统管理员、项目管理员、项目用户
        if (user.admin || isUserLocalProjectAdmin(userId, projectId) || isUserLocalProjectUser(roles, projectId)) {
            return getAllRepoByProjectId(projectId)
        }

        val repoList = mutableListOf<String>()

        // 非管理员用户关联权限
        repoList.addAll(getNoAdminUserRepo(projectId, userId))
        if (roles.isEmpty()) return repoList.distinct()

        val noAdminRole = mutableListOf<String>()

        // 仓库管理员角色关联权限
        val roleList = roleRepository.findByProjectIdAndTypeAndAdminAndIdIn(
            projectId = projectId, type = RoleType.REPO, admin = true, ids = roles
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

    override fun listNoPermissionPath(userId: String, projectId: String, repoName: String): List<String> {
        val user = userDao.findFirstByUserId(userId) ?: return emptyList()
        if (user.admin || isUserLocalAdmin(userId) || isUserLocalProjectAdmin(userId, projectId)) {
            return emptyList()
        }
        val projectPermission = permissionDao.listByResourceAndRepo(NODE.name, projectId, repoName)
        return getNoPermissionPathFromConfig(userId, user.roles, projectPermission)
    }

    private fun getNoPermissionPathFromConfig(
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

    fun isUserLocalProjectUser(roleIds: List<String>, projectId: String): Boolean {
        return roleRepository.findAllById(roleIds)
            .any { role -> role.projectId == projectId && role.roleId == PROJECT_VIEWER_ID }
    }

    fun getAllRepoByProjectId(projectId: String): List<String> {
        return repoClient.listRepo(projectId).data?.map { it.name } ?: emptyList()
    }

    private fun getNoAdminUserProject(userId: String): List<String> {
        val projectList = mutableListOf<String>()
        permissionDao.listByUserId(userId).forEach {
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

    private fun getNoAdminUserRepo(projectId: String, userId: String): List<String> {
        val repoList = mutableListOf<String>()
        permissionDao.listByProjectIdAndUsers(projectId, userId).forEach {
            if (it.actions.isNotEmpty() && it.repos.isNotEmpty()) {
                repoList.addAll(it.repos)
            }
        }
        return repoList
    }

    private fun getNoAdminRoleRepo(project: String, role: List<String>): List<String> {
        val repoList = mutableListOf<String>()
        if (role.isNotEmpty()) {
            permissionDao.listByProjectAndRoles(project, role).forEach {
                if (it.actions.isNotEmpty() && it.repos.isNotEmpty()) {
                    repoList.addAll(it.repos)
                }
            }
        }
        return repoList
    }

    private fun checkPermissionExist(permissionId: String) {
        permissionDao.findFirstById(permissionId) ?: run {
            logger.warn("update permission repos [$permissionId] not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_PERMISSION_NOT_EXIST)
        }
    }

    private fun getOnePermission(
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

    override fun checkPlatformPermission(request: CheckPermissionRequest): Boolean {
        with(request) {
            if (appId == null) return true
            val platform = account.findOneByAppId(appId!!) ?: run {
                logger.info("can not find platform [$appId]")
                return false
            }

            if (platform.scope == null) return true
            when (resourceType) {
                SYSTEM.name -> return true
                PROJECT.name -> {
                    return checkPlatformProject(projectId, platform.scopeDesc)
                }
                else -> return false
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

    override fun updatePermissionDeployInRepo(request: UpdatePermissionDeployInRepoRequest): Boolean {
        checkPermissionExist(request.permissionId)
        return updatePermissionById(request.permissionId, TPermission::includePattern.name, request.path)
                && updatePermissionById(request.permissionId, TPermission::users.name, request.users)
                && updatePermissionById(request.permissionId, TPermission::permName.name, request.name)
                && updatePermissionById(request.permissionId, TPermission::roles.name, request.roles)
    }

    private fun updatePermissionById(id: String, key: String, value: Any): Boolean {
        return permissionDao.updateById(id, key, value)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PermissionServiceImpl::class.java)
    }
}
