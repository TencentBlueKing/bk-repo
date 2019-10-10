package com.tencent.bkrepo.auth.resource

import com.tencent.bkrepo.auth.api.ServiceRoleResource
import com.tencent.bkrepo.auth.model.TRole
import com.tencent.bkrepo.auth.model.TRolePermission
import com.tencent.bkrepo.auth.pojo.AddRolePermissionRequest
import com.tencent.bkrepo.auth.pojo.AddUserRoleRequest
import com.tencent.bkrepo.auth.pojo.CreateRoleRequest
import com.tencent.bkrepo.auth.pojo.Role
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.auth.repository.RolePermissionRepository
import com.tencent.bkrepo.auth.service.RoleService
import com.tencent.bkrepo.common.api.pojo.Response
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ServiceRoleResourceImpl @Autowired constructor(
    private val rolePermissionRepository: RolePermissionRepository,
    private val roleService: RoleService
) : ServiceRoleResource {
    override fun listProjectRole(): Response<List<Role>> {
        return Response(roleService.listByType(RoleType.PROJECT))
    }

    override fun listRepoRole(): Response<List<Role>> {
        return Response(roleService.listByType(RoleType.REPO))
    }

    override fun addUserRole(request: AddUserRoleRequest): Response<Boolean> {
        roleService.addUserRole(request)
        return Response(true)
    }

    override fun createRole(request: CreateRoleRequest): Response<Boolean> {
        // todo check request
        roleService.addRole(request)
        return Response(true)
    }

    override fun deleteRole(name: String): Response<Boolean> {
        // todo check request
        roleService.deleteByName(name)
        return Response(true)
    }

    override fun addRolePermission(request: AddRolePermissionRequest): Response<Boolean> {
        rolePermissionRepository.insert(
            TRolePermission(
                id = null,
                roleId = request.roleId,
                permissionId = request.permissionId
            )
        )
        return Response(true)
    }
}
