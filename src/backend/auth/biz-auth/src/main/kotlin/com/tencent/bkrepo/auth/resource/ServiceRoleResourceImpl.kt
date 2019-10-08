package com.tencent.bkrepo.auth.resource

import com.tencent.bkrepo.auth.api.ServiceRoleResource
import com.tencent.bkrepo.auth.model.TRole
import com.tencent.bkrepo.auth.model.TRolePermission
import com.tencent.bkrepo.auth.pojo.AddRolePermissionRequest
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
    private val roleService: RoleService,
    private val rolePermissionRepository: RolePermissionRepository
) : ServiceRoleResource {
    override fun createRole(createRoleRequest: CreateRoleRequest): Response<Boolean> {
        // todo check request
        roleService.addRole(createRoleRequest)
        return Response(true)
    }

    override fun deleteRole(name: String): Response<Boolean> {
        // todo check request
        roleService.deleteByName(name)
        return Response(true)
    }

    override fun listByType(roleType: RoleType): Response<List<Role>> {
        return Response(roleService.listByType(roleType).map { transfer(it) })
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

    private fun transfer(tRole: TRole): Role {
        return Role(
            id = tRole.id,
            roleType = tRole.roleType,
            name = tRole.name,
            displayName = tRole.displayName
        )
    }
}
