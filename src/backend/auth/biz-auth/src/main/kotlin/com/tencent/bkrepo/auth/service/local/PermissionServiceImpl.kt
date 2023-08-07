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

import com.tencent.bkrepo.auth.constant.AUTH_ADMIN
import com.tencent.bkrepo.auth.constant.AUTH_BUILTIN_USER
import com.tencent.bkrepo.auth.constant.AUTH_BUILTIN_ADMIN
import com.tencent.bkrepo.auth.constant.AUTH_BUILTIN_VIEWER
import com.tencent.bkrepo.auth.constant.PROJECT_MANAGE_ID
import com.tencent.bkrepo.auth.constant.PROJECT_VIEWER_ID
import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.model.TPermission
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.UPDATE
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.READ
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.MANAGE
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.WRITE
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.DELETE
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.permission.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.permission.Permission
import com.tencent.bkrepo.auth.pojo.permission.UpdatePermissionActionRequest
import com.tencent.bkrepo.auth.pojo.permission.UpdatePermissionDepartmentRequest
import com.tencent.bkrepo.auth.pojo.permission.UpdatePermissionPathRequest
import com.tencent.bkrepo.auth.pojo.permission.UpdatePermissionRepoRequest
import com.tencent.bkrepo.auth.pojo.permission.UpdatePermissionRoleRequest
import com.tencent.bkrepo.auth.pojo.permission.UpdatePermissionUserRequest
import com.tencent.bkrepo.auth.repository.AccountRepository
import com.tencent.bkrepo.auth.repository.PermissionRepository
import com.tencent.bkrepo.auth.repository.RoleRepository
import com.tencent.bkrepo.auth.repository.UserRepository
import com.tencent.bkrepo.auth.service.PermissionService
import com.tencent.bkrepo.auth.util.RequestUtil
import com.tencent.bkrepo.auth.util.query.PermissionQueryHelper
import com.tencent.bkrepo.auth.util.request.PermRequestUtil
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.LocalDateTime

open class PermissionServiceImpl constructor(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val account: AccountRepository,
    private val permissionRepository: PermissionRepository,
    private val mongoTemplate: MongoTemplate,
    private val repoClient: RepositoryClient,
    private val projectClient: ProjectClient
) : PermissionService, AbstractServiceImpl(mongoTemplate, userRepository, roleRepository) {

    override fun deletePermission(id: String): Boolean {
        logger.info("delete  permission  repoName: [$id]")
        permissionRepository.deleteById(id)
        return true
    }

    override fun listPermission(projectId: String, repoName: String?): List<Permission> {
        logger.debug("list  permission  projectId: [$projectId], repoName: [$repoName]")
        repoName?.let {
            return permissionRepository.findByResourceTypeAndProjectIdAndRepos(ResourceType.REPO, projectId, repoName)
                .map { PermRequestUtil.convToPermission(it) }
        }
        return permissionRepository.findByResourceTypeAndProjectId(ResourceType.PROJECT, projectId)
            .map { PermRequestUtil.convToPermission(it) }
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
        val permission = permissionRepository.findOneByPermNameAndProjectIdAndResourceType(
            request.permName, request.projectId, request.resourceType
        )
        permission?.let {
            logger.warn("create permission  [$request] is exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_DUP_PERMNAME)
        }
        val result = permissionRepository.insert(PermRequestUtil.convToTPermission(request))
        result.id?.let {
            return true
        }
        return false
    }

    override fun updateIncludePath(request: UpdatePermissionPathRequest): Boolean {
        logger.info("update include path request :[$request]")
        with(request) {
            checkPermissionExist(permissionId)
            return updatePermissionById(permissionId, TPermission::includePattern.name, path)
        }
    }

    override fun updateExcludePath(request: UpdatePermissionPathRequest): Boolean {
        logger.info("update exclude path request :[$request]")
        with(request) {
            checkPermissionExist(permissionId)
            return updatePermissionById(permissionId, TPermission::excludePattern.name, path)
        }
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

    override fun updatePermissionRole(request: UpdatePermissionRoleRequest): Boolean {
        logger.info("update permission role request:[$request]")
        with(request) {
            checkPermissionExist(permissionId)
            return updatePermissionById(permissionId, TPermission::roles.name, rId)
        }
    }

    override fun updatePermissionDepartment(request: UpdatePermissionDepartmentRequest): Boolean {
        logger.info("update  permission department request:[$request]")
        with(request) {
            checkPermissionExist(permissionId)
            return updatePermissionById(permissionId, TPermission::departments.name, departmentId)
        }
    }

    override fun updatePermissionAction(request: UpdatePermissionActionRequest): Boolean {
        logger.info("update permission action request:[$request]")
        with(request) {
            checkPermissionExist(permissionId)
            return updatePermissionById(permissionId, TPermission::actions.name, actions)
        }
    }

    override fun checkPermission(request: CheckPermissionRequest): Boolean {
        logger.debug("check permission  request : [$request] ")

        if (request.uid == ANONYMOUS_USER) return false

        val user = userRepository.findFirstByUserId(request.uid) ?: run {
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }

        // check user locked
        if (user.locked) {
            return false
        }
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
        if (queryRoles.isEmpty()) {
            return false
        }
        val result = roleRepository.findByProjectIdAndTypeAndAdminAndIdIn(
            projectId = request.projectId!!, type = RoleType.PROJECT, admin = true, ids = queryRoles
        )
        if (result.isNotEmpty()) {
            return true
        }
        return false
    }

    private fun checkProjectUser(request: CheckPermissionRequest, roles: List<String>): Boolean {
        var queryRoles = emptyList<String>()
        if (roles.isNotEmpty() && request.projectId != null) {
            queryRoles = roles.filter { !it.isNullOrEmpty()  }.toList()
        }
        if (queryRoles.isEmpty()) return false

        if(roleRepository.findByIdIn(queryRoles).
            any { tRole -> tRole.projectId == request.projectId && tRole.roleId == PROJECT_VIEWER_ID }
            && request.action == READ.toString()
        ) {
            return true
        }
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
            val query = PermissionQueryHelper.buildPermissionCheck(
                projectId, repoName, uid, action, resourceType, roles
            )
            val result = mongoTemplate.find(query, TPermission::class.java)
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
        patternList.forEach {
            if (path.contains(it)) return true
        }
        return false
    }

    private fun checkExcludePattern(patternList: List<String>, path: String): Boolean {
        if (patternList.isEmpty()) return true
        patternList.forEach {
            if (path.contains(it)) return false
        }
        return true
    }

    override fun listPermissionProject(userId: String): List<String> {
        logger.debug("list permission project request : $userId ")
        if (userId.isEmpty()) return emptyList()
        val user = userRepository.findFirstByUserId(userId) ?: run {
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
        if(user.roles.isNotEmpty()) projectList.addAll(getUserCommonRoleProject(user.roles))

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

    override fun listPermissionRepo(projectId: String, userId: String, appId: String?): List<String> {
        logger.debug("list repo permission request : [$projectId, $userId] ")
        val user = userRepository.findFirstByUserId(userId) ?: run {
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }

        // 用户为系统管理员
        if (user.admin) {
            return getAllRepoByProjectId(projectId)
        }

        val roles = user.roles

        // 用户为项目管理员
        if (isUserLocalProjectAdmin(userId, projectId)) return getAllRepoByProjectId(projectId)

        if (isUserLocalProjectUser(roles,projectId)) return getAllRepoByProjectId(projectId)

        val repoList = mutableListOf<String>()

        // 非管理员用户关联权限
        repoList.addAll(getNoAdminUserRepo(projectId, userId))

        if (user.roles.isEmpty()) {
            return repoList.distinct()
        }

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

    private fun isUserLocalProjectUser(roleIds: List<String>, projectId: String): Boolean {
        return roleRepository.findAllById(roleIds)
            .any { role -> role.projectId == projectId && role.roleId == PROJECT_VIEWER_ID }
    }

    fun getAllRepoByProjectId(projectId: String): List<String> {
        return repoClient.listRepo(projectId).data?.map { it.name } ?: emptyList()
    }

    private fun getNoAdminUserProject(userId: String): List<String> {
        val projectList = mutableListOf<String>()
        permissionRepository.findByUsers(userId).forEach {
            if (it.actions.isNotEmpty() && it.projectId != null) {
                projectList.add(it.projectId!!)
            }
        }
        return projectList
    }

    private fun getUserCommonRoleProject(roles: List<String>): List<String> {
        val projectList = mutableListOf<String>()
        roleRepository.findByIdIn(roles).forEach{
            if (it.projectId.isNotEmpty() && it.roleId == PROJECT_VIEWER_ID) {
                projectList.add(it.projectId)
            }
        }
        return projectList
    }

    private fun getNoAdminRoleProject(roles: List<String>): List<String> {
        val project = mutableListOf<String>()
        if (roles.isNotEmpty()) {
            permissionRepository.findByRolesIn(roles).forEach {
                if (it.actions.isNotEmpty() && it.projectId != null) {
                    project.add(it.projectId!!)
                }
            }
        }
        return project
    }

    private fun getNoAdminUserRepo(projectId: String, userId: String): List<String> {
        val repoList = mutableListOf<String>()
        permissionRepository.findByProjectIdAndUsers(projectId, userId).forEach {
            if (it.actions.isNotEmpty() && it.repos.isNotEmpty()) {
                repoList.addAll(it.repos)
            }
        }
        return repoList
    }

    private fun getNoAdminRoleRepo(project: String, role: List<String>): List<String> {
        val repoList = mutableListOf<String>()
        if (role.isNotEmpty()) {
            permissionRepository.findByProjectIdAndRolesIn(project, role).forEach {
                if (it.actions.isNotEmpty() && it.repos.isNotEmpty()) {
                    repoList.addAll(it.repos)
                }
            }
        }
        return repoList
    }

    private fun checkPermissionExist(pId: String) {
        permissionRepository.findFirstById(pId) ?: run {
            logger.warn("update permission repos [$pId]  not exist.")
            throw ErrorCodeException(AuthMessageCode.AUTH_PERMISSION_NOT_EXIST)
        }
    }

    private fun getOnePermission(
        projectId: String,
        repoName: String,
        permName: String,
        actions: Set<PermissionAction>
    ): TPermission {
        permissionRepository.findOneByProjectIdAndReposAndPermNameAndResourceType(
            projectId, repoName, permName, ResourceType.REPO
        ) ?: run {
            val request = TPermission(
                projectId = projectId,
                repos = listOf(repoName),
                permName = permName,
                actions = actions.map { it.toString() },
                resourceType = ResourceType.REPO.toString(),
                createAt = LocalDateTime.now(),
                updateAt = LocalDateTime.now(),
                createBy = AUTH_ADMIN,
                updatedBy = AUTH_ADMIN
            )
            logger.info("permission not exist, create [$request]")
            permissionRepository.insert(request)
        }
        return permissionRepository.findOneByProjectIdAndReposAndPermNameAndResourceType(
            projectId = projectId, repoName = repoName, permName = permName, resourceType = ResourceType.REPO
        )!!
    }

    override fun checkPlatformPermission(request: CheckPermissionRequest): Boolean {
        with(request) {
            if (appId == null) return true
            val platform = account.findOneByAppId(appId!!) ?: run {
                logger.info("can not find platform [$appId]")
                return false
            }

            if (platform.scope == null) return true
            val reqResourceType = ResourceType.lookup(resourceType)
            if (!platform.scope!!.contains(reqResourceType)) return false
            when (reqResourceType) {
                ResourceType.SYSTEM -> return true
                ResourceType.PROJECT -> {
                    return checkPlatformProject(projectId, platform.scopeDesc)
                }
                else -> return false
            }
        }
    }

    override fun listProjectBuiltinPermission(projectId: String): List<Permission> {
        val projectManager = Permission(
            id = PROJECT_MANAGE_ID,
            resourceType = ResourceType.PROJECT.toString(),
            projectId = projectId,
            permName = "project_manage_permission",
            users = getProjectAdminUser(projectId),
            createBy = SecurityUtils.getUserId(),
            updatedBy = SecurityUtils.getUserId(),
            createAt = LocalDateTime.now(),
            updateAt = LocalDateTime.now()
        )
        val projectViewer = Permission (
            id = PROJECT_VIEWER_ID,
            resourceType = ResourceType.PROJECT.toString(),
            projectId = projectId,
            permName = "project_view_permission",
            users = getProjectCommonUser(projectId),
            createBy = SecurityUtils.getUserId(),
            updatedBy = SecurityUtils.getUserId(),
            createAt = LocalDateTime.now(),
            updateAt = LocalDateTime.now()
            )
        return listOf(projectManager, projectViewer)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PermissionServiceImpl::class.java)
    }
}
