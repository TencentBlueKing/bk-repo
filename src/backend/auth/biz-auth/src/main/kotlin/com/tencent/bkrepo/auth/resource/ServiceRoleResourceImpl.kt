package com.tencent.bkrepo.auth.resource

import com.tencent.bkrepo.auth.api.ServicePermissionResource
import com.tencent.bkrepo.auth.pojo.PermissionData
import com.tencent.bkrepo.auth.service.RoleService
import com.tencent.bkrepo.common.api.pojo.Response
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ServiceRoleResourceImpl @Autowired constructor(
    private val roleService: RoleService
) : ServicePermissionResource {
    override fun check(userId: String, permissionType: String, checkPermissionData: PermissionData?): Response<Boolean> {
        roleService.addRole()


        return Response(true)
    }
}
