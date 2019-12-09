package com.tencent.bkrepo.auth.resource

import com.tencent.bkrepo.auth.api.ServiceRoleResource
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

    override fun createRole(request: CreateRoleRequest): Response<Boolean> {
        // todo check request
        roleService.createRole(request)
        return Response(true)
    }

    override fun deleteRole(roleType: RoleType,projectId:String, rId: String ): Response<Boolean> {
        // todo check request
        roleService.deleteRoleByRid(roleType, projectId, rId)
        return Response(true)
    }

    override fun listAll(): Response<List<Role>> {
        return Response(roleService.listAllRole())
    }

    override fun listRoleByType(roleType: String): Response<List<Role>> {
        return Response(roleService.listRoleByType("PROJECT"))
    }

    override fun listRoleByTypeAndProjectId(type: RoleType, projectId: String): Response<List<Role>> {
        return Response(roleService.listRoleByProject(type,projectId))
    }

}
