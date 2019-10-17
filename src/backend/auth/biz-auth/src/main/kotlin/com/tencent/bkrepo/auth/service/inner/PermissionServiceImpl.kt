package com.tencent.bkrepo.auth.service.inner

import com.tencent.bkrepo.auth.model.TPermission
import com.tencent.bkrepo.auth.pojo.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.Permission
import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.repository.PermissionRepository
import com.tencent.bkrepo.auth.repository.RolePermissionRepository
import com.tencent.bkrepo.auth.repository.RoleRepository
import com.tencent.bkrepo.auth.repository.UserRoleRepository
import com.tencent.bkrepo.auth.service.PermissionService
import com.tencent.bkrepo.auth.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "auth", name = ["realm"], havingValue = "inner")
class PermissionServiceImpl @Autowired constructor(
    private val userService: UserService,
    private val permissionRepository: PermissionRepository,
    private val roleRepository: RoleRepository,
    private val userRoleRepository: UserRoleRepository,
    private val rolePermissionRepository: RolePermissionRepository,
    private val mongoTemplate: MongoTemplate
) : PermissionService {
    override fun deletePermission(id: String) {
        permissionRepository.deleteById(id)
    }

    override fun listPermission(resourceType: ResourceType?): List<Permission> {
        return if(resourceType == null){
            permissionRepository.findAll().map { transfer(it) }
        } else {
            permissionRepository.findByResourceType(resourceType).map { transfer(it) }
        }
    }

    private fun transfer(tPermission: TPermission): Permission {
        return Permission(
            id = tPermission.id!!,
            resourceType = tPermission.resourceType,
            action = tPermission.action,
            displayName = tPermission.displayName
        )
    }

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

    private fun getPermission(userId: String, projectId: String): List<PermissionInstance> {
        val allPermissions = permissionRepository.findAll()
        val allRolePermissions = rolePermissionRepository.findAll()

        val userRoles = userRoleRepository.findByUserNameAndProjectId(userId, projectId)

        val permissionMap = allPermissions.associateBy { it.id }
        val roleId2PermissionsMap = mutableMapOf<String, MutableSet<TPermission>>()
        allRolePermissions.forEach { rolePermission ->
            val permission = permissionMap[rolePermission.permissionId] ?: error("permission not exists")
            if (roleId2PermissionsMap.containsKey(rolePermission.roleId)) {
                roleId2PermissionsMap[rolePermission.roleId]!!.add(permission)
            } else {
                roleId2PermissionsMap[rolePermission.roleId] = mutableSetOf(permission)
            }
        }

        val result = mutableListOf<PermissionInstance>()
        userRoles.filter { it.projectId == projectId }.forEach { userRole ->
            roleId2PermissionsMap[userRole.roleId]!!.forEach { tPermission ->
                result.add(PermissionInstance(tPermission.resourceType, tPermission.action, userRole.projectId, userRole.repoId, null))
            }
        }
        return result
    }

    override fun checkPermission(request: CheckPermissionRequest): Boolean {
        val user = userService.getByName(request.userId) ?: return false
        if (user.admin) return true

        return if (request.resourceType == ResourceType.SYSTEM) {
            user.admin
        } else {
            val permissions = getPermission(request.userId, request.project!!)
            checkAuth(request, permissions)
        }
    }

    fun checkAuth(request: CheckPermissionRequest, permissions: List<PermissionInstance>): Boolean {
        permissions.forEach { permission ->
            if (check(request, permission)) {
                return true
            }
        }
        return false
    }

    private fun check(request: CheckPermissionRequest, permission: PermissionInstance): Boolean {
        when (request.resourceType) {
            ResourceType.PROJECT -> { // 项目管理权限，项目权限匹配 -> 通过
                if (permission.resourceType == ResourceType.PROJECT) {
                    return permission.action == PermissionAction.MANAGE || permission.action == request.action
                }
                return false
            }
            ResourceType.REPO, ResourceType.NODE  -> { // 项目管理权限，项目权限匹配，仓库权限匹配 -> 通过
                if (permission.resourceType == ResourceType.PROJECT) {
                    return permission.action == PermissionAction.MANAGE || permission.action == request.action
                }
                if (permission.resourceType == ResourceType.REPO) {
                    return permission.action == request.action
                        && (permission.repoId == "*" || permission.repoId == request.repo)
                }
                return false
            }
            else -> {
                throw RuntimeException("unsupported resource type")
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PermissionServiceImpl::class.java)
    }
}
