package com.tencent.bkrepo.auth.service

import com.tencent.bkrepo.auth.model.TPermission
import com.tencent.bkrepo.auth.model.TRole
import com.tencent.bkrepo.auth.model.TRolePermission
import com.tencent.bkrepo.auth.model.TUserRole
import com.tencent.bkrepo.auth.pojo.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.PermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.repository.PermissionRepository
import com.tencent.bkrepo.auth.repository.RolePermissionRepository
import com.tencent.bkrepo.auth.repository.RoleRepository
import com.tencent.bkrepo.auth.repository.UserRoleRepository
import com.tencent.bkrepo.auth.service.inner.AuthUtil
import com.tencent.bkrepo.auth.service.inner.Permission
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service

@Service
class PermissionServiceImpl @Autowired constructor(
    private val userService: UserService,
    private val permissionRepository: PermissionRepository,
    private val roleRepository: RoleRepository,
    private val userRoleRepository: UserRoleRepository,
    private val rolePermissionRepository: RolePermissionRepository,
    private val mongoTemplate: MongoTemplate
) : PermissionService {
    override fun createPermission(request: CreatePermissionRequest) {
        // todo check request
        permissionRepository.insert(
            TPermission(
                id = null,
                resourceType = request.resourceType,
                action = request.action,
                displayName = request.displayName
            )
        )
    }

    private fun getPermission(userId: String, projectId: String): List<Permission> {
        val allRoles = roleRepository.findAll()
//        val allRoles = listOf(
//            TRole("r1", RoleType.PROJECT, "projectAdmin", "项目管理员"),
//            TRole("r2", RoleType.PROJECT, "projectDeveloper", "项目开发"),
//            TRole("r3", RoleType.PROJECT, "projectGuest", "项目访客"),
//            TRole("r4", RoleType.REPO, "repoDeveloper", "仓库开发"),
//            TRole("r5", RoleType.REPO, "repoGuest", "仓库访客")
//        )

        val allPermissions = permissionRepository.findAll()
//        val allPermissions = listOf(
//            TPermission("p1", ResourceType.PROJECT, PermissionAction.MANAGE, "管理"),
//            TPermission("p2", ResourceType.PROJECT, PermissionAction.READ, "读"),
//            TPermission("p3", ResourceType.PROJECT, PermissionAction.WRITE, "写"),
//            TPermission("p4", ResourceType.REPO, PermissionAction.READ, "读"),
//            TPermission("p5", ResourceType.REPO, PermissionAction.WRITE, "写")
//        )

        val allRolePermissions = rolePermissionRepository.findAll()
//        val allRolePermissions = listOf(
//            TRolePermission("rp1", "r1", "p1"),
//            TRolePermission("rp2", "r2", "p2"),
//            TRolePermission("rp3", "r2", "p3"),
//            TRolePermission("rp4", "r3", "p2"),
//            TRolePermission("rp5", "r4", "p4"),
//            TRolePermission("rp6", "r4", "p5"),
//            TRolePermission("rp7", "r5", "p4")
//        )

        val userRoles = userRoleRepository.findByUserIdAndProjectId(userId, projectId)
//        val userRoles = listOf(
//            TUserRole("u1", "user1", "r1", "project1", "repo1"),
//            TUserRole("u2", "user1", "r3", "project1", null)
//        )

        val permissionMap = allPermissions.associateBy { it.id }
        val roleId2Permissions = mutableMapOf<String, MutableSet<TPermission>>()
        allRolePermissions.forEach { rolePermission ->
            val permission = permissionMap[rolePermission.permissionId] ?: error("permission not exists")
            if (roleId2Permissions.containsKey(rolePermission.roleId)) {
                roleId2Permissions[rolePermission.roleId]!!.add(permission)
            } else {
                roleId2Permissions[rolePermission.roleId] = mutableSetOf(permission)
            }
        }

        val result = mutableListOf<Permission>()
        userRoles.filter { it.projectId == projectId }.forEach { userRole ->
            roleId2Permissions[userRole.roleId]!!.forEach { tPermission ->
                result.add(Permission(tPermission.resourceType, tPermission.action, userRole.projectId, userRole.repoId, null))
            }
        }
        return result
    }

    override fun checkPermission(request: PermissionRequest): Boolean {
        val user = userService.getByName(request.userId) ?: return false
        if (user.admin) return true

        return if (request.resourceType == ResourceType.SYSTEM) {
            user.admin
        } else {
            val permissions = getPermission(request.userId, request.projectId!!)
            AuthUtil.checkAuth(request, permissions)
        }
    }

    override fun checkSystemPermission(userId: String, action: PermissionAction): Boolean {
        val user = userService.getByName(userId) ?: return false
        return user.admin
    }

    override fun checkProjectPermission(userId: String, projectId: String, action: PermissionAction): Boolean {
        val user = userService.getByName(userId) ?: return false
        if (user.admin) return true

        val permissions = getPermission(userId, projectId)
        return AuthUtil.checkAuth(
            PermissionRequest(
                userId = userId,
                resourceType = ResourceType.PROJECT,
                projectId = projectId,
                repoId = null,
                node = null,
                action = action
            ),
            permissions
        )
    }

    override fun checkRepoPermission(userId: String, projectId: String, repoId: String, action: PermissionAction): Boolean {
        val user = userService.getByName(userId) ?: return false
        if (user.admin) return true

        val permissions = getPermission(userId, projectId)
        return AuthUtil.checkAuth(
            PermissionRequest(
                userId = userId,
                resourceType = ResourceType.REPO,
                projectId = projectId,
                repoId = repoId,
                node = null,
                action = action
            ),
            permissions
        )
    }

    override fun checkNodePermission(userId: String, projectId: String, repoId: String, node: String, action: PermissionAction): Boolean {
        val user = userService.getByName(userId) ?: return false
        if (user.admin) return true

        val permissions = getPermission(userId, projectId)
        return AuthUtil.checkAuth(
            PermissionRequest(
                userId = userId,
                resourceType = ResourceType.NODE,
                projectId = projectId,
                repoId = repoId,
                node = node,
                action = action
            ),
            permissions
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PermissionServiceImpl::class.java)
    }
}
