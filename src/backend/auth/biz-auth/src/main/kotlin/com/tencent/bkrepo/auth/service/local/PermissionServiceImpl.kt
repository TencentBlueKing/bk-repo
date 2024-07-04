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
import com.tencent.bkrepo.auth.dao.PermissionDao
import com.tencent.bkrepo.auth.dao.PersonalPathDao
import com.tencent.bkrepo.auth.dao.AccountDao
import com.tencent.bkrepo.auth.dao.RepoAuthConfigDao
import com.tencent.bkrepo.auth.dao.UserDao
import com.tencent.bkrepo.auth.message.AuthMessageCode
import com.tencent.bkrepo.auth.model.TPermission
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.READ
import com.tencent.bkrepo.auth.pojo.enums.ResourceType.NODE
import com.tencent.bkrepo.auth.pojo.enums.ResourceType.PROJECT
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.dao.repository.RoleRepository
import com.tencent.bkrepo.auth.helper.PermissionHelper
import com.tencent.bkrepo.auth.helper.UserHelper
import com.tencent.bkrepo.auth.model.TPersonalPath
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.WRITE
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.DELETE
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.MANAGE
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction.UPDATE
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.permission.Permission
import com.tencent.bkrepo.auth.pojo.permission.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.permission.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.permission.UpdatePermissionRepoRequest
import com.tencent.bkrepo.auth.pojo.permission.UpdatePermissionDeployInRepoRequest
import com.tencent.bkrepo.auth.pojo.permission.UpdatePermissionUserRequest
import com.tencent.bkrepo.auth.pojo.role.ExternalRoleResult
import com.tencent.bkrepo.auth.pojo.role.RoleSource
import com.tencent.bkrepo.auth.service.PermissionService
import com.tencent.bkrepo.auth.util.RequestUtil
import com.tencent.bkrepo.auth.util.request.PermRequestUtil
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.LoggerFactory

open class PermissionServiceImpl constructor(
    private val roleRepository: RoleRepository,
    private val accountDao: AccountDao,
    private val permissionDao: PermissionDao,
    private val userDao: UserDao,
    private val personalPathDao: PersonalPathDao,
    private val repoAuthConfigDao: RepoAuthConfigDao,
    val repoClient: RepositoryClient,
    val projectClient: ProjectClient
) : PermissionService {

    private val permHelper by lazy { PermissionHelper(userDao, roleRepository, permissionDao, personalPathDao) }

    private val userHelper by lazy { UserHelper(userDao, roleRepository) }


    override fun deletePermission(id: String): Boolean {
        logger.info("delete  permission  repoName: [$id]")
        permissionDao.deleteById(id)
        return true
    }

    override fun listPermission(projectId: String, repoName: String?, resourceType: String): List<Permission> {
        logger.debug("list  permission  projectId: [$projectId , $repoName, $resourceType]")
        repoName?.let {
            return permissionDao.listByResourceAndRepo(resourceType, projectId, repoName).map {
                PermRequestUtil.convToPermission(it)
            }
        }
        return permissionDao.listByResourceAndProject(PROJECT.name, projectId).map {
            PermRequestUtil.convToPermission(it)
        }
    }

    override fun listBuiltinPermission(projectId: String, repoName: String): List<Permission> {
        logger.debug("list builtin permission : [$projectId ,$repoName]")
        val repoAdmin = permHelper.getOnePermission(projectId, repoName, AUTH_BUILTIN_ADMIN, setOf(MANAGE))
        val repoUser = permHelper.getOnePermission(
            projectId = projectId,
            repoName = repoName,
            permName = AUTH_BUILTIN_USER,
            actions = setOf(WRITE, READ, DELETE, UPDATE)
        )
        val repoViewer = permHelper.getOnePermission(projectId, repoName, AUTH_BUILTIN_VIEWER, setOf(READ))
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
        logger.info("update repo permission request : [$request]")
        with(request) {
            permHelper.checkPermissionExist(permissionId)
            return permHelper.updatePermissionById(permissionId, TPermission::repos.name, repos)
        }
    }

    override fun updatePermissionUser(request: UpdatePermissionUserRequest): Boolean {
        with(request) {
            logger.info("update permission user request: [$request]")
            when (permissionId) {
                PROJECT_MANAGE_ID -> {
                    val createAdminRequest = RequestUtil.buildProjectAdminRequest(projectId!!)
                    val createUserRequest = RequestUtil.buildProjectViewerRequest(projectId)
                    val adminRoleId = userHelper.createRoleCommon(createAdminRequest)
                    val commonRoleId = userHelper.createRoleCommon(createUserRequest)
                    val adminUsers = permHelper.getProjectAdminUser(projectId)
                    val addRoleUserList = userId.filter { !adminUsers.contains(it) }
                    val removeRoleUserList = adminUsers.filter { !userId.contains(it) }

                    userHelper.addUserToRoleBatchCommon(addRoleUserList, adminRoleId!!)
                    permHelper.removeUserFromRoleBatchCommon(removeRoleUserList, adminRoleId)
                    permHelper.removeUserFromRoleBatchCommon(addRoleUserList, commonRoleId!!)
                    return true
                }
                PROJECT_VIEWER_ID -> {
                    val createUserRequest = RequestUtil.buildProjectViewerRequest(projectId!!)
                    val createAdminRequest = RequestUtil.buildProjectAdminRequest(projectId)
                    val adminRoleId = userHelper.createRoleCommon(createAdminRequest)
                    val commonRoleId = userHelper.createRoleCommon(createUserRequest)
                    val commonUsers = permHelper.getProjectCommonUser(projectId)
                    val addRoleUserList = userId.filter { !commonUsers.contains(it) }
                    val removeRoleUserList = commonUsers.filter { !userId.contains(it) }

                    userHelper.addUserToRoleBatchCommon(addRoleUserList, commonRoleId!!)
                    permHelper.removeUserFromRoleBatchCommon(removeRoleUserList, commonRoleId)
                    permHelper.removeUserFromRoleBatchCommon(addRoleUserList, adminRoleId!!)
                    return true
                }
                else -> {
                    permHelper.checkPermissionExist(permissionId)
                    return permHelper.updatePermissionById(permissionId, TPermission::users.name, userId)
                }
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
            if (user.admin || isUserLocalProjectAdmin(uid, projectId)) return true
            val roles = user.roles
            if (permHelper.isRepoOrNodePermission(resourceType)) {
                // check role repo admin
                if (permHelper.checkRepoAdmin(request, roles)) return true
                // check repo read action
                if (permHelper.checkRepoReadAction(request, roles)) return true
                //  check project user
                val isProjectUser = isUserLocalProjectUser(uid, projectId!!)
                if (permHelper.checkProjectReadAction(request, isProjectUser)) return true
                // check node action
                if (needNodeCheck(projectId!!, repoName!!) && checkNodeAction(request, roles, isProjectUser)) {
                    return true
                }
            }
        }
        return false
    }

    override fun listPermissionProject(userId: String): List<String> {
        logger.debug("list permission project request : $userId ")
        val user = userDao.findFirstByUserId(userId) ?: run {
            throw ErrorCodeException(AuthMessageCode.AUTH_USER_NOT_EXIST)
        }
        // 用户为系统管理员
        if (user.admin) {
            return projectClient.listProject().data?.map { it.name } ?: emptyList()
        }

        val projectList = mutableListOf<String>()

        // 非管理员用户关联权限
        projectList.addAll(permHelper.getNoAdminUserProject(userId))

        // 取用户关联角色关联的项目
        if (user.roles.isNotEmpty()) projectList.addAll(permHelper.getUserCommonRoleProject(user.roles))

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
        projectList.addAll(permHelper.getNoAdminRoleProject(noAdminRole))

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
        if (user.admin || isUserLocalProjectAdmin(userId, projectId) || isUserLocalProjectUser(userId, projectId)) {
            return getAllRepoByProjectId(projectId)
        }

        val repoList = mutableListOf<String>()

        // 非管理员用户关联权限
        repoList.addAll(permHelper.getNoAdminUserRepo(projectId, userId))
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
        repoList.addAll(permHelper.getNoAdminRoleRepo(projectId, noAdminRole))

        return repoList.distinct()
    }

    override fun listNoPermissionPath(userId: String, projectId: String, repoName: String): List<String> {
        val user = userDao.findFirstByUserId(userId) ?: return emptyList()
        if (user.admin || isUserLocalProjectAdmin(userId, projectId)) {
            return emptyList()
        }
        val projectPermission = permissionDao.listByResourceAndRepo(NODE.name, projectId, repoName)
        val configPath = permHelper.getPermissionPathFromConfig(userId, user.roles, projectPermission, false)
        val personalPath = personalPathDao.listByProjectAndRepoAndExcludeUser(userId, projectId, repoName)
            .map { it.fullPath }
        return (configPath + personalPath).distinct()
    }

    override fun listPermissionPath(userId: String, projectId: String, repoName: String): List<String>? {
        val user = userDao.findFirstByUserId(userId) ?: return emptyList()
        if (user.admin || isUserLocalProjectAdmin(userId, projectId)) {
            return null
        }
        val permission = permissionDao.listByResourceAndRepo(NODE.name, projectId, repoName)
        val configPath = permHelper.getPermissionPathFromConfig(userId, user.roles, permission, true).toMutableList()
        val personalPath = personalPathDao.findOneByProjectAndRepo(userId, projectId, repoName)
        if (personalPath != null) {
            configPath.add(personalPath.fullPath)
        }
        return configPath.distinct()
    }

    fun getAllRepoByProjectId(projectId: String): List<String> {
        return repoClient.listRepo(projectId).data?.map { it.name } ?: emptyList()
    }

    override fun checkPlatformPermission(request: CheckPermissionRequest): Boolean {
        with(request) {
            val platform = accountDao.findOneByAppId(appId!!) ?: return false
            // 非平台账号
            if (!permHelper.isPlatformApp(platform)) return false
            // 不限制scope
            if (platform.scope == null) return true
            // 平台账号，限制scope
            if (!platform.scope!!.contains(ResourceType.lookup(resourceType))) return false
            // 校验平台账号权限范围
            when (resourceType) {
                PROJECT.name -> {
                    return permHelper.checkPlatformProject(projectId, platform.scopeDesc)
                }
                else -> return true
            }
        }
    }

    override fun listProjectBuiltinPermission(projectId: String): List<Permission> {
        val projectManager = permHelper.buildBuiltInPermission(
            permissionId = PROJECT_MANAGE_ID,
            projectId = projectId,
            permissionName = "project_manage_permission",
            userList = permHelper.getProjectAdminUser(projectId)
        )
        val projectViewer = permHelper.buildBuiltInPermission(
            permissionId = PROJECT_VIEWER_ID,
            projectId = projectId,
            permissionName = "project_view_permission",
            userList = permHelper.getProjectCommonUser(projectId)
        )
        return listOf(projectManager, projectViewer)
    }

    override fun updatePermissionDeployInRepo(request: UpdatePermissionDeployInRepoRequest): Boolean {
        logger.info("update permission deploy in repo, create [$request]")
        permHelper.checkPermissionExist(request.permissionId)
        return permHelper.updatePermissionById(request.permissionId, TPermission::includePattern.name, request.path)
                && permHelper.updatePermissionById(request.permissionId, TPermission::users.name, request.users)
                && permHelper.updatePermissionById(request.permissionId, TPermission::permName.name, request.name)
                && permHelper.updatePermissionById(request.permissionId, TPermission::roles.name, request.roles)
    }

    override fun getOrCreatePersonalPath(projectId: String, repoName: String, userId: String): String {
        val personalPath = "$defaultPersonalPrefix/$userId"
        personalPathDao.findOneByProjectAndRepo(userId, projectId, repoName) ?: run {
            logger.info("personal path [$projectId, $repoName, $personalPath ] not exist , create")
            val personalPathData =
                TPersonalPath(
                    projectId = projectId,
                    repoName = repoName,
                    userId = userId,
                    fullPath = personalPath
                )
            try {
                personalPathDao.insert(personalPathData)
            } catch (exception: RuntimeException) {
                logger.error("create personal path error [$projectId, $repoName, $personalPath ,$exception]")
            }

        }
        return personalPath
    }

    override fun getPathCheckConfig(): Boolean {
        return true
    }

    override fun listExternalRoleByProject(projectId: String, source: RoleSource): List<ExternalRoleResult> {
        return emptyList()
    }

    fun isUserLocalProjectAdmin(userId: String, projectId: String?): Boolean {
        return permHelper.isUserLocalProjectAdmin(userId, projectId)
    }

    fun isUserLocalProjectUser(userId: String, projectId: String): Boolean {
        return permHelper.isUserLocalProjectUser(userId, projectId)
    }

    fun isUserSystemAdmin(userId: String): Boolean {
        val user = userDao.findFirstByUserId(userId) ?: return false
        return user.admin
    }

    fun checkNodeAction(request: CheckPermissionRequest, userRoles: List<String>?, isProjectUser: Boolean): Boolean {
        with(request) {
            if (checkRepoAccessControl(projectId!!, repoName!!)) {
                return permHelper.checkNodeActionWithCtrl(request, userRoles)
            }
            return permHelper.checkNodeActionWithOutCtrl(request, userRoles, isProjectUser)
        }

    }

    fun needNodeCheck(projectId: String, repoName: String): Boolean {
        val projectPermission = permissionDao.listByResourceAndRepo(NODE.name, projectId, repoName)
        return projectPermission.isNotEmpty()
    }

    override fun checkRepoAccessControl(projectId: String, repoName: String): Boolean {
        val result = repoAuthConfigDao.findOneByProjectRepo(projectId, repoName) ?: return false
        return result.accessControl
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PermissionServiceImpl::class.java)
        private const val defaultPersonalPrefix = "/Personal"
    }
}